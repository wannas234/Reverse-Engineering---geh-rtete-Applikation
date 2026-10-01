# SecureVault (Java) — Reverse-Engineering-Challenge

Kommandozeilen-Anwendung, die ein Geheimnis (Flag) hinter einem Passwort schützt.
Der Schutzmechanismus soll Reverse Engineering erschweren; die Aufgabe der
analysierenden Gruppe ist es, das Passwort zu rekonstruieren und so die Flag zu
erhalten (Capture-the-Flag).

## Zielumgebung

- Ubuntu Linux 26.04 (x86-64), Kommandozeile
- Java 26 (`java -jar`)
- Gebaut und getestet mit Temurin JDK 26 (`--release 26`)

## Projektstruktur (Ordner `Aufgabe`)

- `src/de/dhbw/vault/Main.java` — Bootstrap (ClassLoader, entschlüsselt den Kern)
- `src/de/dhbw/vault/K.java` — geschützte Kernlogik (Passwortableitung + Flag), opake Namen,
  Konstanten zur Laufzeit XOR-dekodiert
- `tools/Gen.java` — Build-Hilfe: erzeugt die eingebetteten Konstanten (Salt, IV, Ciphertext)
- `tools/Pack.java` — Build-Hilfe: verschlüsselt `K.class` zu `core.bin`
- `tools/verify-all.sh` — optionaler Selbsttest (Bauen + Funktions-/Härtungschecks)
- `solver/Solve.java` — Solver (Brute-Forcer, findet das Passwort)
- `build.sh` — Build-Skript (erzeugt `dist/securevault.jar`)
- `dist/securevault.jar` — fertiges, ausführbares Artefakt
- `assets/config.dat` — Köder-Ressource, die ins JAR gepackt wird

## Bauen

```bash
./build.sh
```

Erzeugt `dist/securevault.jar`. Benötigt wird ein JDK 26 auf dem Pfad.

## Ausführen

```bash
java -jar dist/securevault.jar <passwort>
```

Bei korrektem Passwort wird die Flag ausgegeben, sonst `Zugriff verweigert.`

## Dokumentation (separate Abgabe-Ordner)

- `Program/Nutzerdokumentation.md` — Bedienung (zusammen mit der Challenge an die
  analysierende Gruppe)
- `Doku/docs/RE-Dokumentation.md` — exakte, nachvollziehbare Reverse-Engineering-Anleitung
- `Doku/docs/KI-Einschaetzung.md` — Einschätzung der Wirksamkeit gegen KI-gestütztes RE

Der Solver gehört zum Quellcode und liegt hier unter `solver/Solve.java`.
