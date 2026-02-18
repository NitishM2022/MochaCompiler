#!/bin/bash
passed=0
failed=0
for f in /Users/nitishmalluru/HW/CSCE_434/PA6/test*.txt; do
  name=$(basename "$f" .txt)
  result=$(java -cp "build/classes:../../lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s "$f" -o cf -o cp -o dce -loop 2>&1 | head -2)
  if echo "$result" | grep -q "Exception\|Error\|failed\|timed out"; then
    echo "❌ $name: $result"
    ((failed++))
  else
    echo "✅ $name"
    ((passed++))
  fi
done
echo ""
echo "========================================="
echo "PASSED: $passed"
echo "FAILED: $failed"
echo "TOTAL: $((passed + failed))"
