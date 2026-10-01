# Reverse Engineering von SecureVault – Analyse und Lösungsweg

Dieses Dokument beschreibt die Schwachstelle von SecureVault und den vollständigen Weg zur
Flag. Jeder Schritt ist mit Werkzeug und Befehl belegt und auf einer Linux-Shell
(Ubuntu 26.04) nachvollziehbar.

## Werkzeuge

- JDK: `jar`, `javap`, `javac`, `java`
- `openssl` für die AES-Entschlüsselung
- CFR 0.152 (`cfr-0.152.jar`, benf.org) als Java-Decompiler
- ein kurzer selbst geschriebener Brute-Forcer in Java

Ein Decompiler allein reicht nicht, weil die Kernlogik verschlüsselt im Archiv liegt.

## Überblick über das Archiv

    file securevault.jar
    jar tf securevault.jar

Das JAR enthält nur `de/dhbw/vault/Main`, die innere Klasse `Main$L` sowie die Dateien
`core.bin` und `config.dat`. Die Klasse, die das Passwort prüft, fehlt. Eine String-Suche
liefert weder Passwort noch Flag:

    strings securevault.jar | grep -i flag

Beides bestätigt: Die Kernlogik ist ausgelagert und das Geheimnis verschlüsselt.

## Schicht 1: der getarnte ClassLoader

Decompiliert man die Startklasse, wird der Aufbau sichtbar:

    java -jar cfr-0.152.jar securevault.jar --outputdir cfr-out
    less cfr-out/de/dhbw/vault/Main.java

`Main` liest `core.bin`, entschlüsselt die Datei mit AES-128 im ECB-Modus und übergibt das
Ergebnis an einen eigenen ClassLoader (`defineClass`). `core.bin` ist also verschlüsselter
Bytecode, der erst zur Laufzeit zur Klasse `de.dhbw.vault.K` wird.

Der AES-Schlüssel ist die erste Hälfte von `SHA-256` über einen Startwert, der im Code als
Byte-Array `B` steht und von der Funktion `g` entschleiert wird. `g` ist ein XOR mit
`(0x5A + i*31) & 0xFF` pro Byte. Nachgerechnet ergibt sich der Startwert
`dhbw-vault-bootstrap-2026`.

Das ist die erste Ebene des Problems: Der Schlüssel liegt zwangsläufig im Artefakt, weil das
Programm ohne externe Eingabe läuft. Die Verschleierung ist damit keine Geheimhaltung,
sondern nur Mehraufwand.

## Schicht 2: die versteckte Klasse entschlüsseln

Mit dem Startwert lässt sich der Schlüssel reproduzieren und `core.bin` entschlüsseln. Der
Schlüssel sind die ersten 16 Byte (32 Hex-Zeichen) des SHA-256-Werts:

    KEYHEX=$(printf '%s' 'dhbw-vault-bootstrap-2026' | sha256sum | cut -c1-32)
    echo "$KEYHEX"     # b88a783fb6b2f44e2136081996677c11

    jar xf securevault.jar core.bin
    openssl enc -d -aes-128-ecb -K "$KEYHEX" -in core.bin -out K.class

    javap -p K.class   # de.dhbw.vault.K mit Methode a(String)

`K.class` ist jetzt eine gültige Klassendatei und lässt sich decompilieren:

    mkdir -p coredir/de/dhbw/vault && cp K.class coredir/de/dhbw/vault/
    java -jar cfr-0.152.jar coredir/de/dhbw/vault/K.class --outputdir core-src
    less core-src/de/dhbw/vault/K.java

## Die eigentliche Schwachstelle

`K` vergleicht das Passwort nicht mit einem gespeicherten Wert. Stattdessen leitet es mit
`PBKDF2WithHmacSHA256` einen Schlüssel aus der Eingabe ab und entschlüsselt damit einen
AES-GCM-Ciphertext. Nur wenn der GCM-Tag aufgeht, war das Passwort korrekt, und der Klartext
ist die Flag. Salt, IV und Ciphertext stehen erneut als XOR-verschleierte Byte-Arrays im
Code und werden mit derselben Funktion `g` lesbar. Der Salt lautet `secure-vault-kdf-salt`,
die Rundenzahl steht als Konstante `R = 30000`.

Die Konstante `R` ist maßgeblich, nicht die Zeichenkette `HINT`, die `rounds=120000`
behauptet. `HINT` ist eine Falschfährte; wer damit rechnet, findet das Passwort nie.

Der Kern des Problems: PBKDF2 ist eine Einwegfunktion. Es gibt keinen Vergleichswert und
keinen Rückweg zum Passwort. Der einzige Angriff ist Brute-Force. Er ist praktikabel, weil
das Passwort kurz gehalten ist und nur aus Kleinbuchstaben besteht. Da die Korrektheit
allein am GCM-Tag hängt und nicht an einem Standard-Hash, sind hashcat oder John the Ripper
nutzlos; der Rateversuch muss selbst implementiert werden.

## Angriff

Der Brute-Forcer (`Aufgabe/solver/Solve.java`) probiert Kleinbuchstaben steigender Länge,
leitet pro Kandidat per PBKDF2 den Schlüssel ab und versucht die GCM-Entschlüsselung. Beginnt
der Klartext mit `FLAG{`, ist das Passwort gefunden. Salt, IV und Ciphertext stammen aus der
entschleierten Klasse `K`.

    javac Aufgabe/solver/Solve.java -d /tmp/solve
    java -cp /tmp/solve Solve 4

Ergebnis ist das Passwort `hqzm`. Alternativ lässt sich die bereits entschlüsselte Methode
`K.a(...)` per Reflection als Prüforakel aufrufen und auf dieselbe Weise durchprobieren.

Mit dem Passwort gibt das Originalprogramm die Flag aus:

    java -jar securevault.jar hqzm
    FLAG{by73c0d3_3ncryp710n_br0k3n}

Das Programm nicht unter einem angehängten Debugger starten: `K` erkennt die JVM-Argumente
`jdwp`/`-Xdebug` und gibt in diesem Fall eine falsche Flag zurück. Der zuverlässige Weg ist
die statische Analyse.

## Echte Flag gegen Köder abgrenzen

Das Artefakt enthält mehrere Köder-Flags (siehe unten). Zur Unterscheidung dient ein
eindeutiges Kriterium, das direkt aus der Konstruktion folgt und den Lösungsweg nicht
abkürzt.

AES-GCM ist authentifizierte Verschlüsselung. Beim Entschlüsseln wird ein Authentication Tag
geprüft. Stimmt der aus dem Passwort abgeleitete Schlüssel nicht, schlägt die Prüfung fehl
und die JVM wirft `AEADBadTagException`; es entsteht kein Klartext. Die Chance, dass ein
falsches Passwort den Tag zufällig trifft, liegt bei etwa 2^-128 und ist praktisch null.

Daraus ergibt sich der Test:

- Genau ein Passwort liefert aus der GCM-Entschlüsselung überhaupt ein Ergebnis. Dieses
  Ergebnis ist die echte Flag.
- Jede Flag, die als fertige Zeichenkette auftaucht (per `strings`, im Decompilat oder über
  die Hintertür `--unlock`), hat die GCM-Schicht nicht durchlaufen und ist ein Köder.

Gegenprobe mit einem falschen Passwort:

    java -jar securevault.jar falsch     # Zugriff verweigert.
    java -jar securevault.jar hqzm       # FLAG{by73c0d3_3ncryp710n_br0k3n}

Die echte Flag ist nicht die am schnellsten gefundene, sondern die einzige, die aus der
kryptografischen Prüfung hervorgeht.

## Köder

Das Artefakt enthält bewusst mehrere Sackgassen:

- In `Main`: die flag-ähnlichen Strings `FLAG{cl4ssl04d3r_s3cr3t_unl0ck3d}` und
  `FLAG{a3s_k3y_r3c0v3r3d_2026}`, die Platzhalter `letmein123` und `admin:admin`, ein
  ungenutztes Byte-Array `MASTER_KEY` und eine tote Methode `verify` mit dem Fake-Passwort
  `sup3r_s3cr3t_2026`.
- Die Hintertür `DEV_MODE=1` bzw. `--unlock` gibt die Fake-Flag
  `FLAG{v4ult_byp4ss3d_succ3ssfully}` aus.
- `config.dat` wirkt wie ein zweiter verschlüsselter Block, enthält aber nur Zufallsbytes und
  wird nie gelesen.
- In `K`: die tote Methode `legacyUnlock`, die Fake-Flag `FLAG{d3crypt3d_c0r3_cl4ss_0k}`, die
  irreführende `HINT`-Konstante und ein ungenutzter Startwert `MASTER_SEED`.

Nach dem Kriterium aus dem vorigen Abschnitt fällt jeder Köder durch: Keiner stammt aus der
GCM-Entschlüsselung.

## Zusammenfassung

Der Weg führt über fünf Schritte: Archiv sichten, `Main` decompilieren und den
AES-Schlüssel ableiten, `core.bin` mit `openssl` zu `K.class` entschlüsseln, `K`
decompilieren und die PBKDF2/GCM-Konstruktion verstehen, das kurze Passwort per Brute-Force
bestimmen. Das Sicherheitsproblem ist dabei strukturell: Eine lokal laufende Anwendung ohne
externe Geheimnisse muss Schlüssel und alle Bausteine im Artefakt mitliefern, und ein
ausreichend kurzes Passwort ist dann per Brute-Force lösbar.
