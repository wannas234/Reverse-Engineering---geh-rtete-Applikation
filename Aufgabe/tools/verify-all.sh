#!/usr/bin/env bash
set -uo pipefail
cd /work
PASS=0; FAIL=0
ok(){ echo "  [OK] $1"; PASS=$((PASS+1)); }
no(){ echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }

echo "### 1) Clean build"
./build.sh >/dev/null 2>/tmp/build-err.txt && ok "build" || { no "build"; cat /tmp/build-err.txt; }

echo "### 2) JAR-Inhalt"
LIST=$(jar tf dist/securevault.jar)
echo "$LIST" | grep -q '^core.bin$'   && ok "core.bin vorhanden" || no "core.bin fehlt"
echo "$LIST" | grep -q '^config.dat$' && ok "config.dat (Koeder) vorhanden" || no "config.dat fehlt"
echo "$LIST" | grep -qE 'K.class|Core.class' && no "Kernklasse liegt offen im JAR!" || ok "Kernklasse NICHT im JAR (verschluesselt)"

echo "### 3) Funktionsmatrix"
run(){ OUT=$(eval "$1" 2>/dev/null); RC=$?; }
run "java -jar dist/securevault.jar 'hqzm'"
[ "$OUT" = "FLAG{by73c0d3_3ncryp710n_br0k3n}" ] && [ $RC -eq 0 ] && ok "richtig -> echte Flag (rc=0)" || no "richtig: '$OUT' rc=$RC"
run "java -jar dist/securevault.jar X"
[ $RC -eq 1 ] && ok "falsch -> rc=1 (Zugriff verweigert)" || no "falsch rc=$RC out=$OUT"
run "java -jar dist/securevault.jar"
[ $RC -eq 2 ] && ok "kein Arg -> rc=2 (Usage)" || no "noarg rc=$RC"
run "java -jar dist/securevault.jar 'sup3r_s3cr3t_2026'"
[ $RC -eq 1 ] && ok "Backdoor-PW inert -> verweigert" || no "backdoor rc=$RC out=$OUT"
run "java -jar dist/securevault.jar '--unlock'"
[ "$OUT" = "FLAG{v4ult_byp4ss3d_succ3ssfully}" ] && ok "--unlock -> Fake-Flag" || no "--unlock out=$OUT"
run "DEV_MODE=1 java -jar dist/securevault.jar whatever"
[ "$OUT" = "FLAG{v4ult_byp4ss3d_succ3ssfully}" ] && ok "DEV_MODE -> Fake-Flag" || no "DEV_MODE out=$OUT"
run "java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:0 -jar dist/securevault.jar 'hqzm'"
if echo "$OUT" | grep -q 'FLAG{m4st3r_k3y_3xtr4ct3d_2026}' && ! echo "$OUT" | grep -q 'by73c0d3'; then
  ok "Debugger+richtig -> Fake-Flag (echte Flag NICHT geleakt)"; else no "debug out=$OUT"; fi
run "java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:0 -jar dist/securevault.jar X"
[ $RC -eq 1 ] && ok "Debugger+falsch -> verweigert" || no "debug-wrong rc=$RC"

echo "### 4) Leak-Check (echte Geheimnisse duerfen NICHT im JAR stehen)"
rm -rf build/x && mkdir -p build/x && (cd build/x && jar xf /work/dist/securevault.jar)
if grep -arlE 'hqzm|by73c0d3|secure-vault-kdf-salt' build/x/de build/x/config.dat 2>/dev/null | grep -q .; then
  no "echtes Geheimnis im Klartext gefunden"; else ok "kein echtes Passwort/Flag/Salt im Klartext"; fi

echo "### 5) RE-Pfad reproduzierbar"
KEYHEX=$(printf '%s' 'dhbw-vault-bootstrap-2026' | sha256sum | cut -c1-32)
openssl enc -d -aes-128-ecb -K "$KEYHEX" -in build/x/core.bin -out build/x/K.class 2>/dev/null \
  && ok "core.bin via openssl entschluesselt" || no "openssl decrypt"
javap -p build/x/K.class 2>/dev/null | grep -q 'a(java.lang.String)' \
  && ok "entschluesselte K.class gueltig (Methode a sichtbar)" || no "K.class ungueltig"

echo
echo "### ERGEBNIS: PASS=$PASS FAIL=$FAIL"
