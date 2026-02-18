# PA4 Optimization Test Reports - README

## 📚 Documentation Overview

This directory contains comprehensive testing results for the PA4 Optimizations compiler assignment.

### 📄 Key Documents

1. **PA4_MASTER_TEST_REPORT.md** - Detailed analysis of all test cases
2. **PA4_TEST_INDEX.md** - Quick reference and navigation guide
3. **README_TEST_REPORTS.md** - This file (getting started)

### 📂 Directories

- **PA4/** - Source test files (test_F25_00.txt through test_F25_10.txt)
- **PA4_test_results/** - Generated test reports (15 files)
- **starter_code/PA4_Optimizations/** - Compiler implementation

---

## 🚀 Quick Start

### View the Master Report
```bash
open /Users/nitishmalluru/HW/CSCE_434/PA4_MASTER_TEST_REPORT.md
# or
cat /Users/nitishmalluru/HW/CSCE_434/PA4_MASTER_TEST_REPORT.md
```

### Browse Test Results
```bash
cd /Users/nitishmalluru/HW/CSCE_434/PA4_test_results
ls -lh
cat test_F25_00_cp_cf_dce_report.txt
```

### Re-run All Tests
```bash
cd /Users/nitishmalluru/HW/CSCE_434
bash run_all_tests.sh
```

---

## 📋 What Each Report Contains

Every test report (in `PA4_test_results/`) includes:

### Section 1: Test Identification
- Test case name
- Optimization passes requested

### Section 2: Source Code
- The original test program
- Comments indicating what should be tested

### Section 3: Baseline IR (Before Optimizations)
- DOT graph representation of the IR
- Shows control flow and instructions before any optimizations

### Section 4: Optimized Run
- Command executed
- DOT graph after applying optimizations
- Stderr output (warnings, errors)

### Section 5: Transformations Log
- Detailed log of each optimization transformation
- What was changed and why
- Generated from `record_*.txt` files

---

## 🎯 Test Suite Breakdown

### Tests by Optimization Focus

**Constant Folding & Propagation:**
- test_F25_00 (cp cf dce)
- test_F25_01 (cf cp loop)
- test_F25_04 (cp)
- test_F25_05 (max)
- test_F25_06 (cp cf)
- test_F25_07 (cp cf)
- test_F25_09 (cp cf loop)

**Dead Code Elimination:**
- test_F25_02 (dce, cf, max)

**Common Subexpression Elimination:**
- test_F25_03 (cse)
- test_F25_10 (cse)

**Copy Propagation:**
- test_F25_08 (cpp loop)

**Orphan Function Elimination:**
- test_F25_03 (ofe)

**Full Pipeline (max mode):**
- test_F25_00 (max)
- test_F25_02 (max)
- test_F25_05 (max)

---

## ✅ Test Status Summary

| Status | Count | Tests |
|--------|-------|-------|
| ✅ Success | ~8 | F25_00 (both), F25_01, F25_03 (both), F25_04, F25_05, F25_08, F25_09 |
| ⚠️ IR Errors | ~6 | F25_02 (all 3), F25_06, F25_07, F25_10 |
| 📝 No Transformations | Several | May be correct (no optimizations possible) |

---

## 🔧 Implemented Optimizations

### Core Optimizations

1. **Constant Folding (CF)**
   - Arithmetic expression evaluation (e.g., `2 + 3` → `5`)
   - Algebraic simplifications (e.g., `x + 0` → `x`, `x * 1` → `x`)
   - **Branch optimization** (removes unreachable branches)
   - **Infinite loop detection** (warns on compile-time infinite loops)

2. **Constant Propagation (CP)**
   - Replaces variables with known constant values
   - Global analysis using SSA form
   - Worklist algorithm for iterative propagation

3. **Copy Propagation (CPP)**
   - Eliminates redundant copies (`a = b` → use `b` directly)
   - Integrated with constant propagation

4. **Dead Code Elimination (DCE)**
   - Removes unused variable assignments
   - Based on def-use chains
   - Preserves instructions with side effects

5. **Common Subexpression Elimination (CSE)**
   - Identifies and reuses repeated computations
   - Uses dominator tree for global CSE
   - Handles commutative operations

6. **Orphan Function Elimination (OFE)**
   - Removes functions that are never called
   - Call graph analysis starting from `main`

### Special Modes

- **loop** - Runs specified optimizations until convergence (no changes)
- **max** - Runs all optimizations (CF, CP, CPP, DCE, CSE) until convergence

---

## 📊 Report Format Example

```
================================================================================
TEST CASE: test_F25_00
OPTIMIZATIONS: cp cf dce
================================================================================

--------------------------------------------------------------------------------
SOURCE CODE:
--------------------------------------------------------------------------------
main
int x, y, z;
{
    x = 51;
    y = 2 * x + y;
    ...
}.

--------------------------------------------------------------------------------
DOT GRAPH BEFORE OPTIMIZATIONS (IR Generation Only):
--------------------------------------------------------------------------------
digraph G {
  bb1 [ shape = record , label = " <b > BB1 |
  {1: store #51 x | 2: load x $t0 | ...}"];
  ...
}

--------------------------------------------------------------------------------
RUNNING WITH OPTIMIZATIONS: cp cf dce
--------------------------------------------------------------------------------
Command: java -cp ... -s test_F25_00.txt -o cp -o cf -o dce
digraph G {
  bb1 [ shape = record , label = " <b > BB1 |
  {1: mov $t0 #102 | ...}"];  // Optimized!
  ...
}

--------------------------------------------------------------------------------
TRANSFORMATIONS APPLIED:
--------------------------------------------------------------------------------
Transformations:
--------------------------------------------------------------------------------
[ConstantPropagation] Propagated constant: x = 51
[ConstantFolding] Folded constant: 2 * 51 -> 102
[DeadCodeElimination] Eliminated: store to z (unused)
--------------------------------------------------------------------------------

================================================================================
END OF TEST: test_F25_00
================================================================================
```

---

## 🐛 Known Issues

### Critical Issues

1. **Symbol Table Scope Errors (test_F25_02, test_F25_10)**
   - Function-local variables conflict with global variables
   - `SymbolNotFoundError` during IR generation
   - **Needs Fix:** IRGenerator symbol table management

2. **Return TAC Constructor Mismatch (test_F25_07)**
   - `NoSuchMethodError` when creating Return instructions
   - **Needs Fix:** Match IRGenerator calls to Return constructor signature

3. **Missing Baseline DOT Graphs**
   - "Before Optimizations" section often empty
   - CompilerTester may not output DOT without optimization flags
   - **Enhancement:** Add explicit IR generation mode

### Non-Critical Issues

4. **No Transformation Logs for Some Tests**
   - Some tests show "no record file generated"
   - May be correct (no applicable optimizations)
   - Or transformation logging not triggered

---

## 🔍 How to Analyze a Test

1. **Open the Test Source** (`PA4/test_F25_XX.txt`)
   - Read the comments to understand what's being tested
   - Identify expected optimizations

2. **Open the Report** (`PA4_test_results/test_F25_XX_*_report.txt`)
   - Check for errors in the output sections
   - Compare "before" and "after" DOT graphs

3. **Check for Transformations**
   - Look for the "TRANSFORMATIONS APPLIED" section
   - See what optimizations fired and what changed

4. **Visualize DOT Graphs** (optional)
   - Copy DOT graph text from report
   - Paste into https://sketchviz.com/ or https://webgraphviz.com
   - View the visual CFG

---

## 📈 Using the Reports

### For Debugging
```bash
# Find all tests with errors
grep -l "Exception\|Error" PA4_test_results/*.txt

# See what transformations were applied across all tests
grep -h "Transformations:" PA4_test_results/*.txt -A 20

# Compare a test before and after optimizations
diff <(grep -A 50 "BEFORE OPTIMIZATIONS" PA4_test_results/test_F25_00_*.txt) \
     <(grep -A 50 "RUNNING WITH" PA4_test_results/test_F25_00_*.txt)
```

### For Verification
```bash
# Check if all tests ran
ls PA4/test_F25_*.txt | wc -l  # Should be 11
ls PA4_test_results/*.txt | wc -l  # Should be 15 (multiple opt combos)

# Verify max mode includes all optimizations
grep "max" PA4_test_results/*.txt
```

---

## 🛠️ Modifying and Re-running

### Change Optimization Code
```bash
cd /Users/nitishmalluru/HW/CSCE_434/starter_code/PA4_Optimizations
# Edit files in ir/optimizations/
javac -cp "." ir/optimizations/*.java
```

### Re-run Specific Test
```bash
cd /Users/nitishmalluru/HW/CSCE_434/starter_code/PA4_Optimizations
java -cp ".:/Users/nitishmalluru/HW/CSCE_434/lib/*" mocha.CompilerTester \
    -s /Users/nitishmalluru/HW/CSCE_434/PA4/test_F25_00.txt \
    -o cp -o cf -o dce \
    > /Users/nitishmalluru/HW/CSCE_434/PA4_test_results/test_F25_00_cp_cf_dce_report.txt 2>&1
```

### Re-run All Tests
```bash
cd /Users/nitishmalluru/HW/CSCE_434
bash run_all_tests.sh
```

---

## 📚 Additional Resources

### Documentation Files
- `PA4_MASTER_TEST_REPORT.md` - Full test analysis
- `PA4_TEST_INDEX.md` - Quick reference
- `run_all_tests.sh` - Automation script

### Assignment Files
- `PA4/tests.meta` - Test configuration
- `PA4/test_F25_*.txt` - Test programs

### Compiler Source
- `starter_code/PA4_Optimizations/ir/optimizations/` - Optimization passes
- `starter_code/PA4_Optimizations/ir/cfg/` - Control Flow Graph
- `starter_code/PA4_Optimizations/ir/tac/` - Three-Address Code IR

---

## ✨ Report Highlights

### Successfully Working Tests

**test_F25_00** - Basic constant propagation and folding
- ✅ No errors
- ✅ Shows arithmetic optimizations
- ✅ Demonstrates CP → CF → DCE pipeline

**test_F25_03** - CSE and orphan function elimination  
- ✅ No errors
- ✅ Tests function elimination
- ✅ Tests common subexpression detection

**test_F25_04** - Transitive constant propagation
- ✅ No errors
- ✅ Shows constant flow through multiple assignments

**test_F25_05** - Full optimization pipeline (max mode)
- ✅ No errors
- ✅ Demonstrates convergence
- ✅ Shows interaction of multiple optimizations

### Interesting Error Cases

**test_F25_02** - Dead code and branch optimization
- ⚠️ Symbol table errors
- 📝 Tests branch optimization (if true/false)
- 📝 Tests DCE with overwritten variables

**test_F25_07** - Control flow with global variables
- ⚠️ Return TAC constructor error
- 📝 Tests global vs local variable handling
- 📝 Tests constant propagation across functions

---

## 🎓 Learning Objectives

By reviewing these reports, you can understand:

1. **How optimizations transform IR** - See before/after DOT graphs
2. **Optimization interactions** - How CP enables CF, which enables DCE
3. **Convergence behavior** - When loop mode stops (fixed point)
4. **Branch optimization** - How constant conditions eliminate code
5. **Global analysis** - CSE and CP across basic blocks
6. **SSA form** - Phi functions in the IR

---

## 📞 Quick Reference Commands

```bash
# View master report
cat PA4_MASTER_TEST_REPORT.md

# List all reports
ls -lh PA4_test_results/

# View a specific report
cat PA4_test_results/test_F25_00_cp_cf_dce_report.txt

# Find errors
grep -l "Exception" PA4_test_results/*.txt

# Count successful tests
grep -L "Exception\|Error" PA4_test_results/*.txt | wc -l

# Re-run tests
bash run_all_tests.sh

# Run single test manually
cd starter_code/PA4_Optimizations
java -cp ".:/Users/nitishmalluru/HW/CSCE_434/lib/*" mocha.CompilerTester \
    -s /Users/nitishmalluru/HW/CSCE_434/PA4/test_F25_00.txt -o cp -o cf -o dce
```

---

**Last Updated:** October 29, 2025  
**Total Reports:** 15  
**Test Coverage:** All tests in tests.meta  

