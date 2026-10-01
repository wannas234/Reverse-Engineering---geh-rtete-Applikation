# Einschätzung der Wirksamkeit gegen KI-gestütztes Reverse Engineering

Die Aufgabe verlangt eine ehrliche Einschätzung, wie gut unser Schutzmechanismus eine KI
davon abhält, die Anwendung zu reversen und das eigentliche Sicherheitsproblem zu erkennen.
Beim Entwurf haben wir gezielt die Abkürzungen geschlossen, die ein Sprachmodell sonst
schnell ans Ziel bringen. Dass ein wirklich entschlossener und gut ausgestatteter Angreifer
am Ende trotzdem durchkommt, verschweigen wir dabei nicht.

## Wo der Mechanismus eine KI tatsächlich bremst

Der wichtigste Punkt ist, dass es keine umkehrbare Prüfung gibt. Viele einfache Crackmes
löst man, indem man die Prüffunktion liest und rückwärts rechnet. Bei uns wird der Schlüssel
über PBKDF2 aus dem Passwort abgeleitet, und ob das Passwort stimmt, zeigt sich allein
daran, ob die AES-GCM-Entschlüsselung aufgeht. Eine KI kann den Algorithmus sofort benennen.
Weiter hilft ihr das aber nicht, denn aus einer Einwegfunktion lässt sich nichts
zurückrechnen. Es bleibt nur Ausprobieren.

Dazu kommt, dass die Korrektheit über den GCM-Prüfwert festgestellt wird und nicht über
einen gängigen Passwort-Hash. Deshalb lässt sich kein Standardwerkzeug wie hashcat oder John
the Ripper anwenden. Die KI muss den Rateversuch selbst implementieren, also PBKDF2 und
AES-GCM pro Kandidat nachbauen. Machbar ist das, aber es kostet eigene Arbeit statt eines
fertigen Aufrufs.

Außerdem ist die eigentliche Logik nicht sofort sichtbar. Sie liegt verschlüsselt in
`core.bin` und muss erst mit dem im Programm versteckten Schlüssel entschlüsselt werden. Die
wichtigen Konstanten sind zusätzlich verschleiert und tragen nichtssagende Namen. Wir haben
mehrere glaubwürdig aussehende Köder eingestreut, unter anderem eine Hinweis-Zeichenkette mit
einer falschen Rundenzahl für PBKDF2. Solche Fallen sind gerade für eine KI riskant, weil sie
dazu neigt, auffällige Zeichenketten zuerst zu verfolgen. Schließlich erkennt das Programm
einen angehängten Debugger und gibt dann eine falsche Flag aus. Damit entwertet es den
naheliegenden dynamischen Analyseweg.

## Wo die Grenzen liegen

Keiner dieser Punkte ist eine echte kryptografische Mauer, sondern jeweils nur eine
zusätzliche Hürde. Das grundsätzliche Problem bleibt: Die Anwendung läuft lokal und ohne
externe Geheimnisse, also müssen der Entschlüsselungsschlüssel und alle übrigen Bausteine im
Artefakt stehen. Damit die Challenge lösbar bleibt, ist das Passwort bewusst kurz gewählt.
Eine KI, die ihren Brute-Forcer auf mehrere Kerne verteilt, kommt deshalb durch. Auch der
Debugger-Schutz und die Verschleierung lassen sich umgehen, wenn man rein statisch vorgeht.
Die versteckte Methode kann man nach dem Entschlüsseln sogar direkt als Prüforakel benutzen.

## Welches Harness eine KI benötigt

Ein Sprachmodell allein, ohne Werkzeuge, scheitert an den Ausführungsschritten. Um unsere
Anwendung zu reversen, braucht eine KI einen agentischen Rahmen mit Zugriff auf eine Shell
(für `jar`, `javap` und `openssl`), einen Decompiler wie CFR, der zweimal zum Einsatz kommt
(einmal für die Startklasse und, nach dem Entschlüsseln, für die versteckte Klasse), sowie
Datei-Ein- und -Ausgabe, um `core.bin` zu entschlüsseln und die Parameter abzulesen. Den
eigentlichen Angriff kann sie nur mit einer eigenen, möglichst parallelisierten Rateschleife
durchführen, die PBKDF2 und AES-GCM verwendet, etwa in Java über `javax.crypto` oder in
Python mit `pycryptodome`. Fertige Hash-Cracker helfen wegen der GCM-Kopplung nicht.

## Fazit

Unser Mechanismus verlagert den Schutz von reiner Verschleierung hin zu echten Rechenkosten
und schließt die bequemen Abkürzungen: kein Rückwärtsrechnen, kein fertiger Cracker, keine
Flag im Klartext, kein nützlicher Debugger-Lauf. Eine KI wird dadurch spürbar ausgebremst
und muss mehrere Ebenen in der richtigen Reihenfolge lösen. Dauerhaft aufhalten lässt sie
sich damit nicht. Solange der Code beim Angreifer läuft und das Passwort kurz genug für die
Lösbarkeit bleibt, ist ein Durchkommen möglich. Wirklich robust wäre nur eine serverseitige
Prüfung, bei der das Geheimnis das System des Angreifers nie erreicht. Das widerspricht aber
der Vorgabe, dass die Anwendung lokal lauffähig und lösbar sein soll.
