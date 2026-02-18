# CompilerTester Bug Report

## Summary
**Critical bug found in `CompilerTester.java` line 150** that causes all optimization runs to execute in max mode (all optimizations until convergence) regardless of command-line flags provided.

## The Bug

**Location:** `starter_code/PA4_Optimizations/mocha/CompilerTester.java:150`

**Current (Incorrect) Code:**
```java
dotgraph_text = c.optimization(optArguments, options.hasOption("loop"), options.hasOption("max"));
                                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                                  Always returns TRUE         Always returns TRUE
```

**Problem:** 
- `options` is the `Options` object (option schema/definitions)
- `options.hasOption("loop")` checks if the option was **defined** (via `addOption()`), NOT if the user provided it
- Both `loop` and `max` are defined on lines 24-25, so `options.hasOption()` always returns `true`

## Proof of Bug

Created test program `TestOptionsHasOption.java`:
```java
Options options = new Options();
options.addOption("loop", "convergence", false, "Loop flag");
options.addOption("max", "maxOpt", false, "Max flag");

CommandLineParser parser = new DefaultParser();
CommandLine cmd = parser.parse(options, new String[]{});  // Empty args!

System.out.println("options.hasOption(\"loop\"): " + options.hasOption("loop"));  // TRUE
System.out.println("options.hasOption(\"max\"): " + options.hasOption("max"));    // TRUE
System.out.println("cmd.hasOption(\"loop\"): " + cmd.hasOption("loop"));          // FALSE
System.out.println("cmd.hasOption(\"max\"): " + cmd.hasOption("max"));            // FALSE
```

**Output:**
```
options.hasOption("loop"): true   ← Checks if DEFINED (always true)
options.hasOption("max"): true    ← Checks if DEFINED (always true)
cmd.hasOption("loop"): false      ← Checks if PROVIDED by user (correct)
cmd.hasOption("max"): false       ← Checks if PROVIDED by user (correct)
```

## Impact

**Before Fix:**
- Running `-o cf` (just constant folding) → Actually runs ALL optimizations (cf, cp, cpp, dce, cse) until convergence
- Record file named `record_test_F25_02_cf_max.txt` (note the `_max` suffix even though only `cf` was requested)
- Transformations show: `Iteration #1`, `CP:`, `DCE:`, `ConstantFolding:` (all optimizations!)

**After Fix:**
- Running `-o cf` → Only runs constant folding once
- Running `-o cp -o cf` → Only runs CP then CF once
- Record file named `record_test_F25_00_cp_cf.txt` (no `_max` suffix)
- Transformations show only requested optimizations
- Running `-max` → Correctly runs all optimizations until convergence with iterations

## The Fix

**Line 150 should be changed from:**
```java
dotgraph_text = c.optimization(optArguments, options.hasOption("loop"), options.hasOption("max"));
```

**To:**
```java
dotgraph_text = c.optimization(optArguments, cmd.hasOption("loop"), cmd.hasOption("max"));
```

**Change:** `options` → `cmd` (use the parsed command line, not the option definitions)

## Verification

### Test 1: `-o cf` (Just Constant Folding)
**Before fix:** 
- Record file: `record_test_F25_02_cf_max.txt`
- Contains CP, CF, and DCE transformations with iterations

**After fix:**
- Record file: None created (CF found nothing to optimize without CP first)
- Correct behavior ✓

### Test 2: `-o cp -o cf` (CP then CF)
**After fix:**
- Record file: `record_test_F25_00_cp_cf.txt`
- Contains ONLY CP and CF transformations, no DCE, no iterations
- Correct behavior ✓

### Test 3: `-max` (All optimizations)
**After fix:**
- Record file: `record_test_F25_02_max.txt`
- Contains iterations and all optimization transformations (CF, CP, DCE)
- Correct behavior ✓

## Temporary Fix Applied

I have temporarily fixed line 150 in my local copy with this change:
```java
// TEMPORARY FIX: Changed 'options' to 'cmd' - options.hasOption() checks if defined (always true), cmd.hasOption() checks if user provided
dotgraph_text = c.optimization(optArguments, cmd.hasOption("loop"), cmd.hasOption("max"));
```

Please update the official starter code for all students.

---

**Date:** October 29, 2025  
**Student:** Nitish Malluru  
**Course:** CSCE 434/605 - Fall 2025  
**Assignment:** PA4 - Optimizations

