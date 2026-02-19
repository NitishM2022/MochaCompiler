#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/compiler/src"
JAR="$ROOT/third_party/lib/commons-cli-1.9.0.jar"
CLS="$ROOT/target/classes"

mkdir -p "$CLS"

SOURCE_LIST="$ROOT/target/java_sources.txt"
find "$SRC" -name '*.java' | sort > "$SOURCE_LIST"

javac \
  -d "$CLS" \
  -cp "$JAR" \
  -sourcepath "$SRC" \
  @"$SOURCE_LIST"

echo "Build complete: $CLS"
