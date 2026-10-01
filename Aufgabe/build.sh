#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

rm -rf build dist
mkdir -p build/classes build/tools build/jar/de/dhbw/vault dist

javac --release 26 -d build/classes src/de/dhbw/vault/Main.java src/de/dhbw/vault/K.java
javac --release 26 -d build/tools tools/Pack.java

java -cp build/tools Pack build/classes/de/dhbw/vault/K.class build/jar/core.bin

cp build/classes/de/dhbw/vault/Main.class build/jar/de/dhbw/vault/
cp build/classes/de/dhbw/vault/'Main$L.class' build/jar/de/dhbw/vault/
cp assets/config.dat build/jar/config.dat

printf 'Manifest-Version: 1.0\nMain-Class: de.dhbw.vault.Main\n' > build/manifest.mf

jar cfm dist/securevault.jar build/manifest.mf -C build/jar .

echo "Built dist/securevault.jar"
