#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/compiler/src"
JAR="$ROOT/third_party/lib/commons-cli-1.9.0.jar"
CLS="$ROOT/target/classes"

mkdir -p "$CLS"

javac \
  -d "$CLS" \
  -cp "$JAR" \
  -sourcepath "$SRC" \
  "$SRC/mocha/CompilerTester.java"

echo "Build complete: $CLS"
