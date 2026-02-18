#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/compiler/src"
JAR="$ROOT/third_party/lib/commons-cli-1.9.0.jar"
CLS="$ROOT/target/classes"
ART="$ROOT/artifacts"

mkdir -p "$CLS" "$ART/logs" "$ART/records" "$ART/graphs" "$ART/asm"

javac -d "$CLS" -cp "$JAR" -sourcepath "$SRC" "$SRC/mocha/CompilerTester.java"

pushd "$ROOT" >/dev/null
java -cp "$CLS:$JAR" mocha.CompilerTester \
  -s tests/regression/test000.txt \
  -i tests/regression/dummy.in \
  -o cf -o cp \
  > "$ART/logs/smoke.stdout" \
  2> "$ART/logs/smoke.stderr"

shopt -s nullglob
for f in record*.txt; do mv "$f" "$ART/records/"; done
if [ -d graphs ]; then
  for g in graphs/*.dot; do mv "$g" "$ART/graphs/"; done
  rmdir graphs 2>/dev/null || true
fi
popd >/dev/null

echo "Smoke run complete. Logs: artifacts/logs/smoke.stdout"
