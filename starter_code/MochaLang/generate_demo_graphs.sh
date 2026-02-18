#!/bin/bash

# Generate IR graphs for minimal optimization demo test cases

cd /Users/nitishmalluru/HW/CSCE_434/starter_code/MochaLang

echo "Generating IR graphs for minimal test cases..."

# Test 1: CP
echo "1. Generating demo_cp (CP)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_cp.mocha -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_cp.mocha -cfg file -opt cp 2>&1 | grep -v WARNING > /dev/null

# Test 2: CF
echo "2. Generating demo_cf (CF)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_cf.mocha -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_cf.mocha -cfg file -opt cf 2>&1 | grep -v WARNING > /dev/null

# Test 3: DCE
echo "3. Generating demo_dce (DCE)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_dce.mocha -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_dce.mocha -cfg file -opt dce 2>&1 | grep -v WARNING > /dev/null

# Test 4: CSE
echo "4. Generating demo_cse (CSE)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_cse.mocha -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_cse.mocha -cfg file -opt cse 2>&1 | grep -v WARNING > /dev/null

# Test 5: CPP
echo "5. Generating demo_cpp (CPP)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_cpp.mocha -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_cpp.mocha -cfg file -opt cpp 2>&1 | grep -v WARNING > /dev/null

# Test 6: OFE
echo "6. Generating demo_ofe (OFE)..."
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_ofe.mocha -cfg file 2>&1 | grep -v WARNING > /dev/null
java -cp "build/classes:dist/lib/commons-cli-1.9.0.jar" mocha.CompilerTester -s demo_ofe.mocha -cfg file -opt ofe 2>&1 | grep -v WARNING > /dev/null

echo ""
echo "All graphs generated successfully!"
echo ""
echo "Generated files:"
ls -lh graphs/demo_*.dot 2>/dev/null | awk '{print $9, $5}'
