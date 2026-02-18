#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/compiler/src"
JAR="$ROOT/third_party/lib/commons-cli-1.9.0.jar"
CLS="$ROOT/target/classes"
ART="$ROOT/artifacts"

mkdir -p "$CLS" "$ART/graphs" "$ART/records" "$ART/logs"

javac -d "$CLS" -cp "$JAR" -sourcepath "$SRC" "$SRC/mocha/CompilerTester.java"

run_case() {
  local src_file="$1"
  local tag="$2"
  shift 2

  java -cp "$CLS:$JAR" mocha.CompilerTester -s "$src_file" -cfg file "$@" > /tmp/${tag}.stdout 2> /tmp/${tag}.stderr

  local base="$(basename "${src_file%.*}")"
  local dot="graphs/${base}_cfg.dot"
  if [ -f "$dot" ]; then
    mv "$dot" "$ART/graphs/${tag}.dot"
  fi

  shopt -s nullglob
  for f in record_${base}_*.txt; do
    mv "$f" "$ART/records/"
  done
}

pushd "$ROOT" >/dev/null
mkdir -p graphs
run_case "tests/fixtures/mocha/demo_cp.mocha" "demo_cp_pre"
run_case "tests/fixtures/mocha/demo_cp.mocha" "demo_cp_post_cp" -o cp
run_case "tests/fixtures/mocha/demo_cf.mocha" "demo_cf_pre"
run_case "tests/fixtures/mocha/demo_cf.mocha" "demo_cf_post_cf" -o cf
run_case "tests/fixtures/mocha/demo_dce.mocha" "demo_dce_pre"
run_case "tests/fixtures/mocha/demo_dce.mocha" "demo_dce_post_dce" -o dce
rmdir graphs 2>/dev/null || true
popd >/dev/null

echo "Graph generation complete. Files in artifacts/graphs"
