#!/bin/bash

# Generate all IR graphs for optimization demonstration

cd /Users/nitishmalluru/HW/CSCE_434/starter_code/MochaLang

echo "Generating IR graphs for 6 optimization test cases..."

# Test 1: CP (test000)
echo "1. Generating test000 (CP)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test000.txt -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test000.txt -cfg file -opt cp 2>&1 | grep -v WARNING > /dev/null

# Test 2: CF (test209-cf)
echo "2. Generating test209-cf (CF)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test209-cf.txt -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test209-cf.txt -cfg file -opt cf 2>&1 | grep -v WARNING > /dev/null

# Test 3: DCE (test009)
echo "3. Generating test009 (DCE)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test009.txt -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test009.txt -cfg file -opt dce 2>&1 | grep -v WARNING > /dev/null

# Test 4: CSE (test113)
echo "4. Generating test113 (CSE)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test113.txt -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test113.txt -cfg file -opt cse 2>&1 | grep -v WARNING > /dev/null

# Test 5: CPP (test202)
echo "5. Generating test202 (CPP)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test202.txt -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test202.txt -cfg file -opt cpp 2>&1 | grep -v WARNING > /dev/null

# Test 6: OFE (test206-ofe)
echo "6. Generating test206-ofe (OFE)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test206-ofe.txt -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s ../../PA6/test206-ofe.txt -cfg file -opt ofe 2>&1 | grep -v WARNING > /dev/null

echo ""
echo "All graphs generated successfully!"
echo ""
echo "Generated files:"
ls -lh graphs/test000_*.dot graphs/test009_*.dot graphs/test113_*.dot graphs/test202_*.dot graphs/test206-ofe_*.dot graphs/test209-cf_*.dot 2>/dev/null | awk '{print $9, $5}'
