#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/third_party/lib/commons-cli-1.9.0.jar"
CLS="$ROOT/target/classes"
ART="$ROOT/artifacts"
TEST_DIR="${TEST_DIR:-tests}"
LIMIT="${LIMIT:-0}"
OPT_MODE="${OPT_MODE:--max}"
GEN_CFG="${GEN_CFG:-1}"

mkdir -p "$ART/logs" "$ART/records" "$ART/graphs" "$ART/asm"

# Migrate legacy nested outputs into flat artifacts layout.
for legacy in "$ART/logs/regression" "$ART/records/regression" "$ART/graphs/regression" "$ART/asm/regression"; do
  if [ -d "$legacy" ]; then
    parent="$(dirname "$legacy")"
    find "$legacy" -maxdepth 1 -type f -exec mv -f {} "$parent"/ \;
    rmdir "$legacy" 2>/dev/null || true
  fi
done

rm -f "$ART/logs/"test*.stdout "$ART/logs/"test*.stderr
rm -f "$ART/asm/"test*_asm.txt
rm -f "$ART/graphs/"test*.dot
rm -f "$ART/records/"record_test*.txt

"$ROOT/scripts/build.sh" >/tmp/build-tests.log

pass=0
fail=0
count=0
summary="$ART/logs/test-summary.txt"
: > "$summary"

read -r -a OPT_ARGS <<< "$OPT_MODE"

pushd "$ROOT" >/dev/null
mkdir -p graphs
shopt -s nullglob
for test_file in "$TEST_DIR"/test*.txt; do
  count=$((count + 1))
  if [ "$LIMIT" -gt 0 ] && [ "$count" -gt "$LIMIT" ]; then
    break
  fi

  base="$(basename "${test_file%.txt}")"
  input="$TEST_DIR/${base}.in"
  if [ ! -f "$input" ]; then
    input="$TEST_DIR/dummy.in"
  fi

  cmd=(java -cp "$CLS:$JAR" mocha.CompilerTester -s "$test_file" -i "$input" "${OPT_ARGS[@]}" -b)
  if [ "$GEN_CFG" = "1" ]; then
    cmd+=(-cfg file)
  fi

  stdout_file="$ART/logs/${base}.stdout"
  stderr_file="$ART/logs/${base}.stderr"
  if "${cmd[@]}" >"$stdout_file" 2>"$stderr_file"; then
    pass=$((pass + 1))
    echo "PASS $base" | tee -a "$summary" >/dev/null
  else
    fail=$((fail + 1))
    echo "FAIL $base" | tee -a "$summary" >/dev/null
    cat "$stderr_file" >> "$summary"
  fi

  asm_src="$TEST_DIR/${base}_asm.txt"
  if [ -f "$asm_src" ]; then
    mv "$asm_src" "$ART/asm/${base}_asm.txt"
  fi

  dot_src="graphs/${base}_cfg.dot"
  if [ "$GEN_CFG" = "1" ] && [ -f "$dot_src" ]; then
    mv "$dot_src" "$ART/graphs/${base}.dot"
  fi

  for f in record_${base}_*.txt; do mv "$f" "$ART/records/"; done
done

for f in record*.txt; do mv "$f" "$ART/records/"; done
if [ -d graphs ]; then
  for g in graphs/*.dot; do mv "$g" "$ART/graphs/"; done
  rmdir graphs 2>/dev/null || true
fi
popd >/dev/null

echo "Passed: $pass  Failed: $fail" | tee -a "$summary"
[ "$fail" -eq 0 ]
