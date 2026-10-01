# Dokumentation des Sicherheitsproblems und des Reverse Engineering

In diesem Dokument beschreiben wir, wo bei SecureVault die eigentliche Schwachstelle
liegt und wie man sie ausnutzt, um an die Flag zu kommen. Wir geben dabei jeden Schritt
mit den konkreten Werkzeugen und Befehlen an, sodass sich das Vorgehen direkt
nachvollziehen lässt. Alle Kommandos sind für eine Linux-Shell (Ubuntu 26.04) gedacht.

## Verwendete Werkzeuge

Gearbeitet haben wir ausschließlich mit frei verfügbaren Mitteln: dem JDK (für die
Programme `jar`, `javap`, `javac` und `java`), dem Kommandozeilenwerkzeug `openssl`,
dem Java-Decompiler CFR (`cfr-0.152.jar` von benf.org) sowie einem kleinen, selbst
geschriebenen Java-Programm für den eigentlichen Angriff. Ein Decompiler allein genügt
nicht, weil ein Teil des Programms verschlüsselt im JAR liegt.

## Erste Sichtung

Zunächst verschaffen wir uns einen Überblick über das Archiv:

    file securevault.jar
    jar tf securevault.jar

Auffällig ist, dass das JAR nur die Klassen `de/dhbw/vault/Main` und deren innere Klasse
`Main$L` enthält, dazu zwei Dateien `core.bin` und `config.dat`. Die eigentliche Logik,
die das Passwort prüft, ist nirgends als Klasse zu sehen. Auch eine Suche nach lesbaren
Zeichenketten bringt weder das Passwort noch die Flag zutage:

    strings securevault.jar | grep -i flag

Das ist der erste Hinweis darauf, dass die Kernlogik versteckt und das Geheimnis
verschlüsselt ist.

## Das Sicherheitsproblem in zwei Schichten

Beim Decompilieren der sichtbaren Startklasse wird klar, wie das Programm aufgebaut ist:

    java -jar cfr-0.152.jar securevault.jar --outputdir cfr-out
    less cfr-out/de/dhbw/vault/Main.java

`Main` lädt die Ressource `core.bin`, entschlüsselt sie mit AES im ECB-Modus und
übergibt die entstandenen Bytes an einen eigenen ClassLoader (`defineClass`). Es handelt
sich also um verschlüsselten Bytecode, der erst zur Laufzeit zu einer Klasse namens
`de.dhbw.vault.K` wird. Der Schlüssel dafür steckt im Programm selbst: Er ist die erste
Hälfte von `SHA-256` über einen kurzen Startwert, der im Code als Byte-Array `B` abgelegt
und über eine kleine Hilfsfunktion `g` entschleiert wird. Diese Funktion ist eine simple
XOR-Verknüpfung, bei der jedes Byte mit `(0x5A + i*31) & 0xFF` verrechnet wird. Baut man
`g` nach, erhält man als Startwert die Zeichenkette `dhbw-vault-bootstrap-2026`.

Genau hier liegt der erste Teil des Problems: Der Schlüssel zum Entschlüsseln der
versteckten Klasse muss zwangsläufig im Artefakt stehen, denn das Programm läuft ohne
jede Zusatzeingabe. Die „Verschleierung" ist deshalb kein echtes Geheimnis, sondern nur
eine zusätzliche Hürde.

## Die versteckte Klasse entschlüsseln

Mit dem bekannten Startwert können wir den AES-Schlüssel selbst berechnen und `core.bin`
entschlüsseln. Der Schlüssel sind die ersten 16 Byte (32 Hex-Zeichen) des SHA-256-Werts:

    KEYHEX=$(printf '%s' 'dhbw-vault-bootstrap-2026' | sha256sum | cut -c1-32)
    echo "$KEYHEX"     # b88a783fb6b2f44e2136081996677c11

    jar xf securevault.jar core.bin
    openssl enc -d -aes-128-ecb -K "$KEYHEX" -in core.bin -out K.class

    javap -p K.class   # zeigt de.dhbw.vault.K mit der Methode a(String)

Danach liegt `K.class` als ganz normale, gültige Klassendatei vor und lässt sich
decompilieren:

    mkdir -p coredir/de/dhbw/vault && cp K.class coredir/de/dhbw/vault/
    java -jar cfr-0.152.jar coredir/de/dhbw/vault/K.class --outputdir core-src
    less core-src/de/dhbw/vault/K.java

## Der Kern der Schwachstelle

In `K` wird das Passwort nicht mit einem gespeicherten Wert verglichen. Stattdessen wird
aus dem eingegebenen Passwort über `PBKDF2WithHmacSHA256` ein Schlüssel abgeleitet und
damit versucht, einen AES-GCM-Ciphertext zu entschlüsseln. Nur wenn der GCM-Prüfwert
(der Authentication Tag) aufgeht, war das Passwort richtig und es kommt Klartext heraus –
nämlich die Flag. Die benötigten Konstanten (Salt, IV und der Ciphertext) stehen wieder
als XOR-verschleierte Byte-Arrays im Code und werden mit derselben Funktion `g` lesbar.
Entschleiert ergibt sich als Salt `secure-vault-kdf-salt`, und die Zahl der
PBKDF2-Runden steht als Konstante `R = 30000` im Code.

Ein kleiner Stolperstein ist hier eingebaut: Eine Zeichenkette mit dem Namen `HINT`
behauptet `rounds=120000`. Das ist eine bewusste Falsch­fährte; maßgeblich ist allein die
Konstante `R`. Wer der Zeichenkette glaubt, rechnet mit der falschen Rundenzahl und
findet nie das richtige Passwort.

Der entscheidende Punkt ist: PBKDF2 ist eine Einwegfunktion. Man kann aus dem Ergebnis
nicht auf das Passwort zurückrechnen, und einen verräterischen Vergleichswert gibt es
nicht. Der einzige Angriff, der bleibt, ist Ausprobieren (Brute-Force). Das ist nur
deshalb praktikabel, weil das Passwort bewusst kurz gehalten ist – es besteht aus wenigen
Kleinbuchstaben. Weil die Korrektheit zudem nur über die GCM-Entschlüsselung erkennbar
ist und nicht über einen Standard-Hash, lässt sich hier kein fertiges Werkzeug wie
hashcat oder John the Ripper einsetzen; man muss den Rateversuch selbst programmieren.

## Der Angriff

Wir haben dafür ein kurzes Java-Programm geschrieben (`Aufgabe/solver/Solve.java`). Es
probiert Kleinbuchstaben steigender Länge durch, leitet für jeden Kandidaten mit PBKDF2
den Schlüssel ab und versucht die GCM-Entschlüsselung. Sobald der entschlüsselte Text mit
`FLAG{` beginnt, ist das Passwort gefunden. Die dort hinterlegten Werte für Salt, IV und
Ciphertext stammen direkt aus der entschleierten Klasse `K`.

    javac Aufgabe/solver/Solve.java -d /tmp/solve
    java -cp /tmp/solve Solve 4

Das Programm meldet das gefundene Passwort `hqzm` zusammen mit der Flag. Wer sich die
PBKDF2-Berechnung sparen will, kann alternativ die bereits entschlüsselte Methode
`K.a(...)` per Reflection direkt als Prüforakel aufrufen und auf dieselbe Weise die
Kandidaten testen.

Mit dem gefundenen Passwort liefert schließlich das Originalprogramm die Flag:

    java -jar securevault.jar hqzm
    FLAG{by73c0d3_3ncryp710n_br0k3n}

Wichtig dabei: Man sollte das Programm nicht unter einem angehängten Debugger laufen
lassen. `K` erkennt über die JVM-Startargumente (`jdwp`/`-Xdebug`), ob ein Debugger aktiv
ist, und gibt in diesem Fall absichtlich eine falsche Flag aus. Der verlässliche Weg ist
die statische Analyse, so wie oben beschrieben.

## Sackgassen

Damit die Analyse nicht zu geradlinig ist, haben wir einige Köder eingebaut, die alle
unbrauchbar sind. Im Bytecode von `Main` stehen zwei Flag-ähnliche Zeichenketten
(`FLAG{cl4ssl04d3r_s3cr3t_unl0ck3d}` und `FLAG{a3s_k3y_r3c0v3r3d_2026}`) sowie die
Platzhalter `letmein123` und `admin:admin`; dazu kommt ein verlockend benanntes, aber
funktionsloses Byte-Array `MASTER_KEY` und eine tote Methode `verify`, die ein
Fake-Passwort `sup3r_s3cr3t_2026` prüft. Setzt man die Umgebungsvariable `DEV_MODE=1`
oder übergibt `--unlock`, gibt das Programm die Fake-Flag
`FLAG{v4ult_byp4ss3d_succ3ssfully}` aus. Die Datei `config.dat` sieht wie ein zweiter
verschlüsselter Block aus, enthält aber nur Zufallsbytes und wird nie gelesen. In der
versteckten Klasse `K` finden sich weitere Köder: eine tote Methode `legacyUnlock`, eine
Fake-Flag `FLAG{d3crypt3d_c0r3_cl4ss_0k}`, die bereits erwähnte irreführende
`HINT`-Zeichenkette und ein ungenutzter Startwert `MASTER_SEED`. Echt ist nur die eine
Flag, die am Ende tatsächlich aus der GCM-Entschlüsselung herausfällt.

## Zusammenfassung des Vorgehens

Der Weg zur Flag führt also über fünf Schritte: das Archiv sichten, die Startklasse
`Main` decompilieren und den Entschlüsselungsschlüssel ablesen, `core.bin` mit `openssl`
zu `K.class` entschlüsseln, diese Klasse decompilieren und die PBKDF2/GCM-Konstruktion
verstehen, und schließlich das kurze Passwort per selbst geschriebenem Brute-Forcer
bestimmen. Das zugrundeliegende Sicherheitsproblem ist dabei immer dasselbe: Weil die
Anwendung lokal und ohne externe Geheimnisse läuft, liegen Schlüssel und alle Bausteine
zwangsläufig im Artefakt, und ein ausreichend kurzes Passwort lässt sich dann durch
Ausprobieren finden.
