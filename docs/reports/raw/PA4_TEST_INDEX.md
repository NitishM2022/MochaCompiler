# PA4 Test Suite - Quick Index

## 📊 Test Execution Summary

**Total Test Variations:** 15  
**Reports Generated:** 15  
**Working Tests:** ~8  
**Tests with Errors:** ~7  

---

## 📁 File Structure

```
/Users/nitishmalluru/HW/CSCE_434/
├── PA4/                                    # Test input files
│   ├── test_F25_00.txt through test_F25_10.txt
│   └── tests.meta                          # Test configuration
├── PA4_test_results/                       # Generated reports  
│   ├── test_F25_00_cp_cf_dce_report.txt
│   ├── test_F25_00_max_report.txt
│   ├── ... (15 total reports)
│   └── test_F25_10_cse_report.txt
├── PA4_MASTER_TEST_REPORT.md               # Comprehensive analysis
├── PA4_TEST_INDEX.md                       # This file
├── run_all_tests.sh                        # Test automation script
└── starter_code/PA4_Optimizations/         # Compiler source

```

---

## ✅ Working Tests (No Errors)

| Test | Opts | Report Size | Notes |
|------|------|-------------|-------|
| test_F25_00 | cp cf dce | 1.5K | Basic arithmetic optimizations |
| test_F25_00 | max | 1.4K | All optimizations |
| test_F25_01 | cf cp loop | 1.6K | Convergence testing |
| test_F25_03 | cse | 1.5K | Common subexpression |
| test_F25_03 | ofe | 1.5K | Orphan function elimination |
| test_F25_04 | cp | 1.5K | Constant propagation |
| test_F25_05 | max | 1.6K | Full optimization pipeline |
| test_F25_08 | cpp loop | 1.6K | Copy propagation |
| test_F25_09 | cp cf loop | 1.7K | Loop optimization |

---

## ⚠️ Tests with Errors

### Symbol Table Scope Errors

| Test | Opts | Error | Issue |
|------|------|-------|-------|
| test_F25_02 | dce | `Symbol c not found` | Function-local variable scoping |
| test_F25_02 | cf | `Symbol c not found` | Function-local variable scoping |
| test_F25_02 | max | `Symbol c not found` | Function-local variable scoping |
| test_F25_10 | cse | `Symbol x not found` | Global vs local variable conflict |

### TAC Constructor Errors

| Test | Opts | Error | Issue |
|------|------|-------|-------|
| test_F25_06 | cp cf | Size: 2.2K | Possible array handling issue |
| test_F25_07 | cp cf | `NoSuchMethodError: Return.<init>` | Return TAC constructor mismatch |

---

## 🔍 Viewing Reports

### View All Reports
```bash
ls -lh /Users/nitishmalluru/HW/CSCE_434/PA4_test_results/
```

### View a Specific Report
```bash
cat /Users/nitishmalluru/HW/CSCE_434/PA4_test_results/test_F25_00_cp_cf_dce_report.txt
```

### View Master Report
```bash
cat /Users/nitishmalluru/HW/CSCE_434/PA4_MASTER_TEST_REPORT.md
```

### View Only Errors
```bash
grep -l "Exception\|Error" /Users/nitishmalluru/HW/CSCE_434/PA4_test_results/*.txt
```

---

## 🚀 Re-running Tests

### Run All Tests
```bash
cd /Users/nitishmalluru/HW/CSCE_434
bash run_all_tests.sh
```

### Run Single Test
```bash
cd /Users/nitishmalluru/HW/CSCE_434/starter_code/PA4_Optimizations
java -cp ".:/Users/nitishmalluru/HW/CSCE_434/lib/*" mocha.CompilerTester \
    -s /Users/nitishmalluru/HW/CSCE_434/PA4/test_F25_00.txt \
    -o cp -o cf -o dce
```

---

## 📋 Report Format

Each report contains:

1. **Test Case Header** - Test name and optimization flags
2. **Source Code** - The original test program
3. **Baseline IR** - DOT graph before optimizations
4. **Optimization Run** - Command and output with optimizations
5. **Transformations** - Detailed log of what changed (from record_*.txt)
6. **Final IR** - DOT graph after optimizations

---

## 🐛 Known Issues to Fix

### Priority 1: Critical Errors

1. **Symbol Table Scoping**
   - **File:** `ir/IRGenerator.java`
   - **Method:** `visit(FunctionDeclaration)`, `loadIfNeeded()`
   - **Issue:** Function-local variables not properly isolated from global scope
   - **Tests Affected:** test_F25_02, test_F25_10

2. **Return TAC Constructor**
   - **File:** `ir/tac/Return.java` or `ir/IRGenerator.java:751`
   - **Issue:** IRGenerator calling wrong constructor for Return instruction
   - **Tests Affected:** test_F25_07

### Priority 2: Missing Output

3. **Baseline DOT Graph Not Captured**
   - **Issue:** CompilerTester may not output DOT graph without optimization flags
   - **Impact:** "DOT GRAPH BEFORE OPTIMIZATIONS" section is empty in reports

---

## 📊 Optimization Coverage

Based on the test suite:

- **Constant Propagation (CP):** 8 tests
- **Constant Folding (CF):** 6 tests
- **Dead Code Elimination (DCE):** 2 tests
- **Common Subexpression (CSE):** 2 tests
- **Copy Propagation (CPP):** 1 test
- **Orphan Function Elim (OFE):** 1 test
- **Loop Mode:** 3 tests
- **Max Mode:** 3 tests

---

## 🎯 Test Purposes

| Test | Primary Focus |
|------|---------------|
| F25_00 | Basic arithmetic constant folding and propagation |
| F25_01 | Iterative optimization (loop mode) |
| F25_02 | Dead code, constant branches, unreachable code |
| F25_03 | CSE and orphan function elimination |
| F25_04 | Transitive constant propagation |
| F25_05 | Full optimization pipeline |
| F25_06 | Array operations with constants |
| F25_07 | Control flow with constant conditions |
| F25_08 | Copy propagation chains |
| F25_09 | Loop with constants |
| F25_10 | Global CSE with functions |

---

**For detailed analysis, see:** `PA4_MASTER_TEST_REPORT.md`  
**Generated:** October 29, 2025

