# New Features Summary

## 1. Orphan Function Elimination ✅

### What It Does
Removes functions that are never called (directly or transitively) from the main function. This is an **optional** optimization that helps reduce code size.

### Usage
```bash
# Enable orphan function elimination
java mocha.CompilerTester -s program.txt -i input.in -o orphan -ssa

# Combine with other optimizations
java mocha.CompilerTester -s program.txt -i input.in -o orphan -o cf -o dce -ssa
```

### Implementation Details

**File:** `OrphanFunctionElimination.java`

**Algorithm:**
1. Build call graph from all CFGs
2. Find functions reachable from `main` (transitive closure via BFS)
3. Mark unreachable functions as orphans
4. Remove orphan CFGs from the list

**Example:**
```
main() calls: helper()
helper() calls: utility()
orphan() calls: nothing (never called)

Result: orphan() is removed
```

**Key Features:**
- ✅ NOT aggressive - only removes when explicitly requested via `-o orphan`
- ✅ When not enabled, orphan functions are kept and optimized normally
- ✅ Uses transitive reachability analysis
- ✅ Runs once at the beginning (before iterative optimizations)
- ✅ Not included in `-max` mode by default

## 2. Uninitialized Variable Warnings & Auto-Initialization ✅

### What It Does
- **Warns** when a variable is used before being initialized
- **Automatically initializes** uninitialized variables to default values:
  - `int` → 0
  - `float` → 0.0
  - `bool` → false (0)
  - `arrays` → 0

### Usage
```bash
# Warning appears on stderr during IR generation
java mocha.CompilerTester -s program.txt -i input.in -ssa

# Example output:
# WARNING: Variable 'x' may be uninitialized
```

### Implementation Details

**File:** `IRGenerator.java`

**Tracking:**
- `Set<String> initializedVariables` - Tracks which variables have been assigned

**Detection:**
- When `loadIfNeeded()` is called for a non-temp variable
- Checks if variable is in `initializedVariables` set
- If not, warns and initializes

**Initialization:**
- When assignment occurs, variable is marked as initialized
- If used before assignment:
  1. Warning printed to stderr
  2. `Store` instruction added to initialize variable
  3. Variable marked as initialized

**Example:**
```c
int x, y, z;
{
    y = 5;
    z = x + y;  // WARNING: Variable 'x' may be uninitialized
                // Automatically initialized to 0
}
```

**Generated IR:**
```
store 0 x_0       // Auto-initialization
store 5 y_0
load t0 x_0       // Now safe to load
load t1 y_0
add t2 t0 t1
store t2 z_0
```

## Integration with Existing Optimizations

### Orphan Function Elimination
- Runs **before** iterative optimizations
- Does not participate in convergence loop
- Optional - use `-o orphan` flag

### Uninitialized Variables
- Runs **during** IR generation (always active)
- Warnings appear regardless of optimization flags
- Auto-initialization happens before any optimizations run
- Works with all optimizations (CF, CP, DCE, CSE)

## Testing

### Test Uninitialized Variables
```bash
# Create test file
cat > test_uninit.txt << 'EOF'
main
int x, y;
{
    y = x + 5;  // x is uninitialized
    call printInt(y);
}.
EOF

# Run - should see warning
java mocha.CompilerTester -s test_uninit.txt -i input.in -ssa

# Output:
# WARNING: Variable 'x' may be uninitialized
```

### Test Orphan Elimination
```bash
# For multi-function programs
java mocha.CompilerTester -s program.txt -i input.in -o orphan -ssa

# Check transformation log for:
# OrphanElimination: Orphan function detected: functionName
# OrphanElimination: Eliminated N orphan function(s)
```

## Updated Optimization Flags

| Flag | Name | Type | Scope |
|------|------|------|-------|
| `cf` | Constant Folding + Algebraic Simplification | Iterative | Local |
| `cp` / `cpp` | Constant/Copy Propagation | Iterative | Global |
| `dce` | Dead Code Elimination | Iterative | Global |
| `cse` | Common Subexpression Elimination | Iterative | Global |
| **`orphan`** | **Orphan Function Elimination** | **Once** | **Global** |

## Implementation Files

### New Files:
1. **OrphanFunctionElimination.java** (120 lines)
   - Extends `BaseOptimization`
   - Implements call graph analysis
   - Eliminates uncalled functions

### Modified Files:
2. **IRGenerator.java**
   - Added `Set<String> initializedVariables`
   - Added `initializeVariableToDefault()` method
   - Modified `loadIfNeeded()` to check initialization
   - Modified `visit(Assignment)` to track assignments
   - Adds Type imports for default value determination

3. **Optimizer.java**
   - Added support for `orphan` flag
   - Calls `eliminateOrphans()` before iterative optimizations
   - Updated documentation

## Benefits

### Orphan Function Elimination:
- ✅ Reduces code size
- ✅ Removes dead code at function level
- ✅ Helps with modular code cleanup
- ✅ Optional - no impact unless requested

### Uninitialized Variable Handling:
- ✅ Catches potential bugs early
- ✅ Makes IR more robust
- ✅ Explicit initialization aids debugging
- ✅ Works seamlessly with all optimizations
- ✅ Warnings help identify programmer errors

## Example Usage

### Complete Optimization Pipeline:
```bash
# With orphan elimination
java mocha.CompilerTester -s program.txt -i input.in \
  -o orphan -o cf -o cp -o dce -o cse -ssa

# With max optimization (no orphan elimination)
java mocha.CompilerTester -s program.txt -i input.in -max -ssa

# Just orphan elimination
java mocha.CompilerTester -s program.txt -i input.in -o orphan -ssa
```

### Warnings Always Active:
```bash
# Uninitialized variable warnings appear regardless:
java mocha.CompilerTester -s test.txt -i input.in -ssa
# Output: WARNING: Variable 'x' may be uninitialized
```

## Design Decisions

### Why Orphan Elimination is Optional:
- Not included in `-max` to preserve existing behavior
- Developers may want to keep unused functions for:
  - Future use
  - Testing
  - API completeness
  - Debugging

### Why Uninitialized Variables Auto-Initialize:
- Makes IR well-defined
- Prevents undefined behavior
- Maintains correctness of optimizations
- Warning still alerts programmer to potential bug
- Default values match language semantics (C/Java style)

## Compatibility

✅ **Fully backward compatible**
- Existing programs work unchanged
- New flags are optional
- Warnings don't break compilation
- Auto-initialization is transparent to optimizations

## Performance Impact

- **Orphan Elimination:** Minimal (runs once, before optimization loop)
- **Uninitialized Warnings:** Negligible (simple set lookups)
- **Auto-Initialization:** Minimal (only for uninitialized variables)

## Summary

Both features are **production-ready** and **fully integrated** into your compiler:

1. **Orphan Function Elimination** - Optional optimization to remove dead functions
2. **Uninitialized Variable Handling** - Always-on safety feature with warnings

All tests pass ✅, code compiles ✅, and features work as specified! 🎉

