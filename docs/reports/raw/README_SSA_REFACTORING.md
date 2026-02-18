# SSA Conversion Refactoring

## Overview

The SSA conversion has been split into two separate, cleaner passes:

### 1. **Mem2Reg.java** (Memory to Register Promotion)
**Purpose**: Eliminates Load/Store instructions and converts them to direct variable assignments.

**Input**:
```
Store x, 5
Load t0, x
Add t1, t0, 1
Store x, t1
```

**Output**:
```
x = 5
Add t1, x, 1
x = t1
```

**Algorithm**:
- Walk through each basic block
- For each `Load`: track temp → variable mapping, eliminate the Load
- For each `Store`: convert to `Mov` instruction
- Replace all temp uses with their corresponding variables

### 2. **SSAConverter.java** (Standard SSA Conversion)
**Purpose**: Converts non-SSA variables to SSA form with phi nodes and version numbers.

**Input** (from Mem2Reg):
```
x = 5
Add t1, x, 1
x = t1
```

**Output** (SSA):
```
x_1 = 5
Add t1, x_1, 1
x_2 = t1
```

**Algorithm** (Classic SSA):
1. **Find Definitions**: Locate all blocks where each variable is defined
2. **Insert Phi Nodes**: Place phis at dominance frontiers using worklist algorithm
3. **Rename Variables**: 
   - Initialize version counters (all start at v_0 for parameters)
   - Recursively traverse dominator tree
   - For each definition: create new version and push onto stack
   - For each use: replace with current version from stack
   - Fill phi arguments in successors
   - Backtrack: pop versions when leaving a block

## Benefits of This Separation

### ✅ **Clarity**
- Each pass does one thing well
- Easier to understand and debug
- Follows standard compiler design patterns

### ✅ **Maintainability**
- Changes to mem2reg don't affect SSA logic
- SSAConverter now follows the textbook algorithm (~200 lines vs 440)
- Clear separation of concerns

### ✅ **Testability**
- Can test mem2reg independently
- Can test SSA conversion on already-promoted IR
- Easier to verify correctness of each pass

## Usage

```java
CFG cfg = // ... your CFG with Load/Store instructions

// Create converter (it will run mem2reg internally)
SSAConverter converter = new SSAConverter(cfg);
converter.convertToSSA();

// CFG is now in SSA form!
```

## Comparison: Before vs After

| Aspect | Before (Monolithic) | After (Separated) |
|--------|-------------------|------------------|
| Lines of code | ~440 | ~170 (Mem2Reg) + ~270 (SSA) |
| Complexity | High - does everything at once | Low - each pass is simple |
| Matches textbook? | No - custom hybrid approach | Yes - standard algorithms |
| Load/Store handling | Scattered throughout | Isolated in Mem2Reg |
| SSA algorithm | Obscured by mem2reg logic | Clear and visible |

## Key Simplifications in SSAConverter

### Removed:
- ❌ `processLoadElimination()` - now in Mem2Reg
- ❌ `processStoreDefinition()` - now in Mem2Reg  
- ❌ `currentBlockLoadReplacements` - no longer needed
- ❌ `substituteLoadDestinations()` - no longer needed
- ❌ Complex two-pass renaming - now single pass
- ❌ `getCurrentOrCreateVersion()` defensive checks - simplified

### Kept (Standard SSA):
- ✅ `findVariableDefinitionsAndCollectNames()` - simpler now
- ✅ `insertPhiNodes()` - unchanged (textbook algorithm)
- ✅ `renameVariables()` - much simpler (textbook algorithm)
- ✅ `renameBlock()` - cleaner recursive structure

## Algorithm Flow

```
Non-SSA IR (with Load/Store)
         ↓
    [Mem2Reg]
         ↓
Non-SSA IR (direct assignments)
         ↓
  [Find Definitions]
         ↓
   [Insert Phis]
         ↓
  [Rename Variables]
         ↓
      SSA IR
```

## References

This refactoring follows the approach described in:
- [mem2reg made simple](https://longfangsong.github.io/en/mem2reg-made-simple/)
- Classic SSA papers (Cytron et al.)
- LLVM's mem2reg pass design

