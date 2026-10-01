# Nutzerdokumentation – SecureVault

SecureVault ist ein kleines Kommandozeilenprogramm. Es verwahrt ein Geheimnis (eine
sogenannte Flag) hinter einem Passwort. Gibt man das richtige Passwort an, gibt das
Programm die Flag aus. Andernfalls verweigert es den Zugriff. Mehr tut die Anwendung
bewusst nicht. Sie ist als Übungsobjekt für das Reverse Engineering gedacht.

## Voraussetzungen

Benötigt wird eine Java-Laufzeitumgebung. Entwickelt und getestet haben wir die Anwendung
unter Java 26 auf Ubuntu 26.04. Sie ist als ausführbares JAR verpackt.

## Aufruf

Das Programm erwartet genau ein Argument, nämlich das zu prüfende Passwort:

    java -jar securevault.jar <passwort>

Stimmt das Passwort, erscheint die Flag auf der Standardausgabe und das Programm endet mit
dem Rückgabewert 0. Ist das Passwort falsch, gibt das Programm „Zugriff verweigert." aus und
endet mit Rückgabewert 1. Ruft man es ohne oder mit zu vielen Argumenten auf, erscheint ein
kurzer Verwendungshinweis (Rückgabewert 2).

## Beispiele

Ein falscher Versuch:

    $ java -jar securevault.jar geheim
    Zugriff verweigert.

Der Aufruf ohne Argument zeigt die Hilfe:

    $ java -jar securevault.jar
    SecureVault
    Usage: java -jar securevault.jar <password>

Weitere Bedienmöglichkeiten gibt es nicht. Das richtige Passwort ist nicht Teil dieser
Dokumentation. Es zu finden ist die eigentliche Aufgabe der Challenge.
