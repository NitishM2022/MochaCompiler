# PA4 Optimizations - Master Test Report

**Date Generated:** October 29, 2025  
**Compiler:** PA4_Optimizations  
**Test Suite:** tests.meta  

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Test Results Overview](#test-results-overview)
3. [Detailed Test Reports](#detailed-test-reports)
4. [Known Issues](#known-issues)
5. [Optimization Statistics](#optimization-statistics)

---

## Executive Summary

This report documents the execution of all test cases specified in `tests.meta` with their respective optimization passes. Each test case was run with:
1. **Baseline IR Generation** (no optimizations)
2. **Specified Optimizations** as per tests.meta
3. **Transformation Logging** to track optimization effects

**Optimizations Implemented:**
- **CF** - Constant Folding (includes algebraic simplification and branch optimization)
- **CP** - Constant Propagation
- **CPP** - Copy Propagation
- **DCE** - Dead Code Elimination
- **CSE** - Common Subexpression Elimination
- **OFE** - Orphan Function Elimination
- **loop** - Run optimizations until convergence
- **max** - Run all optimizations until convergence

---

## Test Results Overview

| Test Case | Optimization Passes | Status | Report File |
|-----------|-------------------|---------|-------------|
| test_F25_00 | cp cf dce | ✓ Generated | test_F25_00_cp_cf_dce_report.txt |
| test_F25_00 | max | ✓ Generated | test_F25_00_max_report.txt |
| test_F25_01 | cf cp loop | ✓ Generated | test_F25_01_cf_cp_loop_report.txt |
| test_F25_02 | dce | ⚠️ IR Error | test_F25_02_dce_report.txt |
| test_F25_02 | cf | ⚠️ IR Error | test_F25_02_cf_report.txt |
| test_F25_02 | max | ⚠️ IR Error | test_F25_02_max_report.txt |
| test_F25_03 | cse | ✓ Generated | test_F25_03_cse_report.txt |
| test_F25_03 | ofe | ✓ Generated | test_F25_03_ofe_report.txt |
| test_F25_04 | cp | ✓ Generated | test_F25_04_cp_report.txt |
| test_F25_05 | max | ✓ Generated | test_F25_05_max_report.txt |
| test_F25_06 | cp cf | ✓ Generated | test_F25_06_cp_cf_report.txt |
| test_F25_07 | cp cf | ✓ Generated | test_F25_07_cp_cf_report.txt |
| test_F25_08 | cpp loop | ✓ Generated | test_F25_08_cpp_loop_report.txt |
| test_F25_09 | cp cf loop | ✓ Generated | test_F25_09_cp_cf_loop_report.txt |
| test_F25_10 | cse | ⚠️ IR Error | test_F25_10_cse_report.txt |

**Legend:**
- ✓ Generated - Report created successfully
- ⚠️ IR Error - Symbol table scope errors during IR generation

---

## Detailed Test Reports

### Test Case: test_F25_00

**Test Variations:**
1. **cp cf dce** - Constant Propagation → Constant Folding → Dead Code Elimination
2. **max** - All optimizations to convergence

**Source Code:**
```
main
int x, y, z;

// cp cf dce
{
    x = 51;
    y = 2 * x + y;
    x = x + y * 2 + z;
    call printInt(y);
}.
```

**Features Tested:**
- Constant propagation of literal values
- Constant folding in arithmetic expressions
- Dead code elimination of unused variables
- Multiple optimization passes in sequence

**Expected Optimizations:**
- `2 * x` → `2 * 51` → `102`
- Propagation of constant `x = 51`
- Folding of constant expressions

**Report Location:** `PA4_test_results/test_F25_00_cp_cf_dce_report.txt`

---

### Test Case: test_F25_01

**Test Variations:**
1. **cf cp loop** - Constant Folding → Constant Propagation (until convergence)

**Source Code:**
```
main
int x, y;

{
    x = 5 + 3;
    y = x * 2;
    call printInt(y);
}.
```

**Features Tested:**
- Constant folding of addition
- Iterative optimization until no changes
- Propagation after folding

**Expected Optimizations:**
- `5 + 3` → `8`
- `x = 8` propagated
- `8 * 2` → `16`
- `y = 16` propagated

**Report Location:** `PA4_test_results/test_F25_01_cf_cp_loop_report.txt`

---

### Test Case: test_F25_02

**Test Variations:**
1. **dce** - Dead Code Elimination
2. **cf** - Constant Folding (includes branch optimization)
3. **max** - All optimizations

**Source Code:**
```
main

//dce local, cf

function early_return () : int
{
    int c, d, unused;

    c = 1; // dce - overwritten
    c = 2;
    unused = c / d; // dce - unused
    return c * d;
};

function unreachableIf () : void
{
    int c, d;

    c = 1;
    d = 2;
    if (d == c) then
        d = c;
    fi;

    if (false) then
        call println();
    else
        call println();
    fi;

    if (true) then
        call println();
    else
        call println();
    fi;

    if (true) then
        call println();
    fi;

    if (false) then
        call println();
    fi;
};

{
    call unreachableIf();
    call printInt(call early_return());
}.
```

**Features Tested:**
- Dead code elimination of overwritten variables
- Dead code elimination of unused computations
- **Branch optimization with constant conditions**
- Removal of unreachable code blocks

**Expected Optimizations:**
- DCE: Remove `c = 1` (overwritten)
- DCE: Remove `unused = c / d` (unused variable)
- CF: `if (false)` - remove then branch, keep else
- CF: `if (true)` - remove else branch, keep then
- CF: `if (false)` with no else - remove entire if statement

**Status:** ⚠️ **IR Generation Error - Symbol table scope issue with function-local variables**

**Report Location:** `PA4_test_results/test_F25_02_*_report.txt`

---

### Test Case: test_F25_03

**Test Variations:**
1. **cse** - Common Subexpression Elimination
2. **ofe** - Orphan Function Elimination

**Source Code:**
```
main
int x, y, z;

function helper() : int
{
    return 42;
};

{
    x = 5 + 3;
    y = 5 + 3;
    z = x + y;
    call printInt(z);
}.
```

**Features Tested:**
- Common subexpression elimination (`5 + 3` appears twice)
- Orphan function detection (helper is never called)

**Expected Optimizations:**
- CSE: Reuse result of `5 + 3` for both x and y assignments
- OFE: Remove `helper()` function (not called)

**Report Location:** `PA4_test_results/test_F25_03_*_report.txt`

---

### Test Case: test_F25_04

**Test Variations:**
1. **cp** - Constant Propagation

**Source Code:**
```
main
int x, y, z;

{
    x = 10;
    y = x;
    z = y;
    call printInt(z);
}.
```

**Features Tested:**
- Copy propagation through multiple assignments
- Transitive constant propagation

**Expected Optimizations:**
- Propagate `x = 10` → `y = 10` → `z = 10`

**Report Location:** `PA4_test_results/test_F25_04_cp_report.txt`

---

### Test Case: test_F25_05

**Test Variations:**
1. **max** - All optimizations to convergence

**Source Code:**
```
main
int a, b, c;

{
    a = 1 + 2;
    b = a * 3;
    c = b - a;
    call printInt(c);
}.
```

**Features Tested:**
- Multiple optimization interactions
- Convergence detection
- Full optimization pipeline

**Expected Optimizations:**
- CF: `1 + 2` → `3`
- CP: Propagate `a = 3`
- CF: `3 * 3` → `9`
- CP: Propagate `b = 9`
- CF: `9 - 3` → `6`
- CP: Propagate `c = 6`
- DCE: Remove unused assignments if any

**Report Location:** `PA4_test_results/test_F25_05_max_report.txt`

---

### Test Case: test_F25_06

**Test Variations:**
1. **cp cf** - Constant Propagation → Constant Folding

**Source Code:**
```
main
int x, y;
array[10] arr;

{
    x = 5;
    arr[0] = x;
    y = arr[0] + x;
    call printInt(y);
}.
```

**Features Tested:**
- Array operations with constant propagation
- Memory operations (store/load)
- Constant folding after propagation

**Expected Optimizations:**
- CP: Propagate `x = 5` to `arr[0] = 5`
- After load from `arr[0]`, fold `5 + 5` → `10`

**Report Location:** `PA4_test_results/test_F25_06_cp_cf_report.txt`

---

### Test Case: test_F25_07

**Test Variations:**
1. **cp cf** - Constant Propagation → Constant Folding

**Source Code:**
```
main
int x, y, z;

{
    x = 3;
    y = 4;
    if (x < y) then
        z = x + y;
    else
        z = x - y;
    fi;
    call printInt(z);
}.
```

**Features Tested:**
- Branch conditions with constants
- Constant propagation across control flow
- Phi node handling in SSA

**Expected Optimizations:**
- CP: `x = 3`, `y = 4`
- CF: `3 < 4` → `true`
- Branch optimization: Remove else block (unreachable)
- CF: `3 + 4` → `7`

**Report Location:** `PA4_test_results/test_F25_07_cp_cf_report.txt`

---

### Test Case: test_F25_08

**Test Variations:**
1. **cpp loop** - Copy Propagation (until convergence)

**Source Code:**
```
main
int a, b, c, d;

{
    a = 10;
    b = a;
    c = b;
    d = c;
    call printInt(d);
}.
```

**Features Tested:**
- Copy propagation chains
- Iterative optimization
- Transitive copy elimination

**Expected Optimizations:**
- Replace `b` with `a`
- Replace `c` with `a`
- Replace `d` with `a`
- Final: `d = 10` directly

**Report Location:** `PA4_test_results/test_F25_08_cpp_loop_report.txt`

---

### Test Case: test_F25_09

**Test Variations:**
1. **cp cf loop** - Constant Propagation → Constant Folding (until convergence)

**Source Code:**
```
main
int x, y;

{
    let x = 0;
    while (x < 5) do {
        y = x * 2;
        x = x + 1;
    } od;
    call printInt(y);
}.
```

**Features Tested:**
- Loop optimization
- Iterative constant propagation
- Loop-invariant detection

**Expected Optimizations:**
- Limited optimization due to loop-variant `x`
- Possible loop unrolling or strength reduction

**Report Location:** `PA4_test_results/test_F25_09_cp_cf_loop_report.txt`

---

### Test Case: test_F25_10

**Test Variations:**
1. **cse** - Common Subexpression Elimination

**Source Code:**
```
//cse

main
int a,b,c,d,e,f,g,h;

function foo() : void {
    a = 7;
    b = a + 2;
    c = a + b;
    d = c + b;
    b = c + b;
    a = a + b;
    e = c + d;
    f = c + d;
    g = a + b;
    h = e + f;

    call printInt(g);
    call printInt(h);
};

function bar(int x, int y) : void {
    int a,b,c,d;

    a = x + y;
    b = x + 2;
    c = 2 + x; 
    d = 1 * (x + 2);
    if (x > y) then
        d = x + y;
        b = 0 - (x+2);
        x = x + 1;
    else 
        d = x + 2;
        y = 2 + x;
    fi;
    a = x + y;

    g = a + b;
    h = c + d;

    call printInt(g); 
    call printInt(h);   
};

{
    call foo();
}.
```

**Features Tested:**
- Global CSE across basic blocks
- CSE with commutative operations (`x + 2` vs `2 + x`)
- CSE with algebraic identities (`1 * expr`)

**Expected Optimizations:**
- CSE: `c + d` computed twice (lines with `e` and `f`)
- CSE: `x + 2` appears in multiple places
- Algebraic: `1 * (x + 2)` → `x + 2`
- CSE: `2 + x` equivalent to `x + 2`

**Status:** ⚠️ **IR Generation Error - Symbol table scope issue (global vs function-local variables)**

**Report Location:** `PA4_test_results/test_F25_10_cse_report.txt`

---

## Known Issues

### 1. Symbol Table Scope Errors

**Affected Tests:** test_F25_02, test_F25_10

**Error:**
```
SymbolNotFoundError: Symbol {variable} not found
```

**Root Cause:** 
The IR Generator has issues handling variable scoping when:
- Function-local variables shadow global variables
- Variables are declared in function parameters or local scope but reference global scope

**Impact:**
- Cannot generate IR for these test cases
- Optimizations cannot be tested on these programs

**Recommendation:**
- Fix IRGenerator's symbol table handling in `visit(FunctionDeclaration)` to properly manage scopes
- Ensure `symbolTable.enterScope()` and `exitScope()` are balanced
- Variables should be looked up in the correct scope

### 2. DOT Graph Output Not Captured

**Issue:** The baseline IR generation (without optimizations) doesn't show DOT graph output in reports.

**Root Cause:** 
The CompilerTester may not be outputting the DOT graph when no optimizations are specified, or the output redirection is not capturing stdout properly.

**Workaround:**
Individual test runs can be performed manually to view DOT graphs.

---

## Optimization Statistics

### Optimization Pass Coverage

Based on `tests.meta`:

| Optimization | Times Used | Test Cases |
|--------------|-----------|------------|
| **cp** (Constant Propagation) | 8 | test_F25_00, 01, 04, 06, 07, 08, 09 |
| **cf** (Constant Folding) | 6 | test_F25_00, 01, 02, 06, 07, 09 |
| **dce** (Dead Code Elimination) | 2 | test_F25_00, 02 |
| **cse** (Common Subexpression Elim) | 2 | test_F25_03, 10 |
| **cpp** (Copy Propagation) | 1 | test_F25_08 |
| **ofe** (Orphan Function Elim) | 1 | test_F25_03 |
| **loop** (Until Convergence) | 3 | test_F25_01, 08, 09 |
| **max** (All Optimizations) | 3 | test_F25_00, 02, 05 |

### Optimization Combinations

- **Single Pass:** 4 test variations
- **Two Passes:** 3 test variations
- **Three Passes:** 1 test variation
- **Loop Mode:** 3 test variations
- **Max Mode:** 3 test variations

---

## File Locations

**Test Input Files:** `/Users/nitishmalluru/HW/CSCE_434/PA4/test_F25_*.txt`

**Test Reports:** `/Users/nitishmalluru/HW/CSCE_434/PA4_test_results/`

**Transformation Records:** `/Users/nitishmalluru/HW/CSCE_434/starter_code/PA4_Optimizations/record_*.txt` (generated per run)

**Test Runner Script:** `/Users/nitishmalluru/HW/CSCE_434/run_all_tests.sh`

**Master Report:** `/Users/nitishmalluru/HW/CSCE_434/PA4_MASTER_TEST_REPORT.md` (this file)

---

## Appendix: Test Execution Commands

To regenerate all reports:
```bash
cd /Users/nitishmalluru/HW/CSCE_434
bash run_all_tests.sh
```

To run a specific test:
```bash
cd /Users/nitishmalluru/HW/CSCE_434/starter_code/PA4_Optimizations
java -cp ".:/Users/nitishmalluru/HW/CSCE_434/lib/*" mocha.CompilerTester \
    -s /Users/nitishmalluru/HW/CSCE_434/PA4/test_F25_00.txt \
    -o cp -o cf -o dce
```

To run with max optimizations:
```bash
java -cp ".:/Users/nitishmalluru/HW/CSCE_434/lib/*" mocha.CompilerTester \
    -s /Users/nitishmalluru/HW/CSCE_434/PA4/test_F25_00.txt \
    -max
```

To run until convergence:
```bash
java -cp ".:/Users/nitishmalluru/HW/CSCE_434/lib/*" mocha.CompilerTester \
    -s /Users/nitishmalluru/HW/CSCE_434/PA4/test_F25_01.txt \
    -o cf -o cp -loop
```

---

**End of Report**

*Generated by PA4 Optimizations Test Suite*

