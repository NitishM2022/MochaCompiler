#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/third_party/lib/commons-cli-1.9.0.jar"
CLS="$ROOT/target/classes"
ART="$ROOT/artifacts"
LIMIT="${LIMIT:-0}"
OPT_MODE="${OPT_MODE:--max}"

mkdir -p "$ART/logs" "$ART/records" "$ART/graphs"
"$ROOT/scripts/build.sh" >/tmp/build-regression.log

pass=0
fail=0
count=0
summary="$ART/logs/regression-summary.txt"
: > "$summary"

pushd "$ROOT" >/dev/null
for test_file in tests/regression/test*.txt; do
  count=$((count + 1))
  if [ "$LIMIT" -gt 0 ] && [ "$count" -gt "$LIMIT" ]; then
    break
  fi

  base="$(basename "${test_file%.txt}")"
  input="tests/regression/${base}.in"
  if [ ! -f "$input" ]; then
    input="tests/regression/dummy.in"
  fi

  if java -cp "$CLS:$JAR" mocha.CompilerTester -s "$test_file" -i "$input" $OPT_MODE > /tmp/${base}.stdout 2> /tmp/${base}.stderr; then
    pass=$((pass + 1))
    echo "PASS $base" | tee -a "$summary" >/dev/null
  else
    fail=$((fail + 1))
    echo "FAIL $base" | tee -a "$summary" >/dev/null
    cat /tmp/${base}.stderr >> "$summary"
  fi

done

shopt -s nullglob
for f in record*.txt; do mv "$f" "$ART/records/"; done
if [ -d graphs ]; then
  for g in graphs/*.dot; do mv "$g" "$ART/graphs/"; done
  rmdir graphs 2>/dev/null || true
fi
popd >/dev/null

echo "Passed: $pass  Failed: $fail" | tee -a "$summary"
[ "$fail" -eq 0 ]
