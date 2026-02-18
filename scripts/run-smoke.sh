#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ART="$ROOT/artifacts"
JAR="$ROOT/third_party/lib/commons-cli-1.9.0.jar"
CLS="$ROOT/target/classes"

mkdir -p "$ART/logs" "$ART/records" "$ART/graphs" "$ART/asm"
"$ROOT/scripts/build.sh" >/tmp/build-smoke.log

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
