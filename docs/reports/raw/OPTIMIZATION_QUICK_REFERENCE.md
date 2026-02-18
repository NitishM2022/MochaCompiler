# Optimization Quick Reference

## Current Optimization Passes

| Flag | Name | Scope | Description |
|------|------|-------|-------------|
| `cf` | Constant Folding | Local | Folds constants (2+3→5) + algebraic simplification (x+0→x) |
| `cp` / `cpp` | Constant/Copy Propagation | Global | Propagates constants and copies through SSA graph |
| `dce` | Dead Code Elimination | Global | Removes unused instructions |
| `cse` | Common Subexpression Elimination | Global | Eliminates redundant computations |

## Usage Examples

### Single Optimization
```bash
java mocha.CompilerTester -s test.txt -i input.in -o cf -ssa
```

### Multiple Optimizations
```bash
java mocha.CompilerTester -s test.txt -i input.in -o cf -o cp -o dce -ssa
```

### Max Optimization (All passes until convergence)
```bash
java mocha.CompilerTester -s test.txt -i input.in -max -ssa
```

### Convergence Mode (Custom passes until fixed point)
```bash
java mocha.CompilerTester -s test.txt -i input.in -o cf -o cp -loop -ssa
```

## Optimization Order

### Recommended Order (used by `-max`):
1. **cf** - Fold constants and simplify algebra
2. **cp** - Propagate constants/copies
3. **cpp** - (same as cp)
4. **dce** - Remove dead code
5. **cse** - Eliminate common subexpressions

Repeat until no changes (convergence).

## What Each Pass Does

### Constant Folding (`cf`)

**Phase 1: Algebraic Simplification**
```
x + 0  →  x
x - 0  →  x
x * 0  →  0
x * 1  →  x
x / 1  →  x
x - x  →  0
0 / x  →  0
```

**Phase 2: Constant Folding**
```
add t0, 2, 3  →  mov t0, 5
mul t1, 4, 5  →  mov t1, 20
sub t2, 10, 7  →  mov t2, 3
div t3, 20, 4  →  mov t3, 5
```

### Copy and Constant Propagation (`cp`)

**Lattice-based worklist algorithm:**
```
mov x, 5
add t0, x, 2  →  add t0, 5, 2  (propagated x=5)
```

```
mov x, y
add t0, x, 2  →  add t0, y, 2  (propagated x=y)
```

### Dead Code Elimination (`dce`)

**Removes unused definitions:**
```
mov x, 5   ← DEAD (x never used)
mov y, 10
write y
```
Becomes:
```
mov y, 10
write y
```

### Common Subexpression Elimination (`cse`)

**Eliminates redundant computations:**
```
add t0, a, b
...
add t1, a, b  ← Redundant!
```
Becomes:
```
add t0, a, b
...
mov t1, t0  ← Reuse result
```

## BaseOptimization Utilities

All optimizations inherit these utilities:

### Value Utilities
```java
getIntegerValue(Value v)       // Extract integer from Value
isConstant(Value v)            // Check if constant
constantEquals(Value v1, v2)   // Compare constants
```

### Analysis Utilities
```java
buildDefUseChains(cfg, defs, uses)  // Build def-use chains
hasSideEffects(TAC inst)            // Check for side effects
isPureComputation(TAC inst)         // Check if pure
isBinaryArithmetic(TAC inst)        // Check if binary op
```

### Expression Utilities
```java
getExpressionSignature(TAC inst)  // Create signature for CSE
```

## Adding a New Optimization

1. **Create class extending BaseOptimization:**
```java
public class MyOptimization extends BaseOptimization {
    public MyOptimization(Optimizer optimizer) { 
        super(optimizer); 
    }
    
    @Override
    protected String getName() { 
        return "MyOpt"; 
    }
    
    @Override
    public boolean optimize(CFG cfg) {
        boolean changed = false;
        
        // Your optimization logic here
        // Use inherited utilities as needed
        
        return changed;
    }
}
```

2. **Register in Optimizer.java:**
```java
case "myopt":
    changed = new MyOptimization(this).optimize(cfg);
    break;
```

3. **Use it:**
```bash
java mocha.CompilerTester -s test.txt -i input.in -o myopt -ssa
```

## File Structure

```
ir/optimizations/
├── BaseOptimization.java           # Base class + utilities
├── Optimizer.java                  # Orchestrator
├── ConstantFolding.java           # CF + algebraic simplification
├── CopyAndConstantPropagation.java # CP/CPP
├── DeadCodeElimination.java       # DCE
└── CommonSubexpressionElimination.java # CSE
```

## Common Patterns

### Iterating Over All Blocks
```java
for (BasicBlock block : cfg.getAllBlocks()) {
    if (block == null) continue;
    
    List<TAC> instructions = block.getInstructions();
    for (int i = 0; i < instructions.size(); i++) {
        TAC inst = instructions.get(i);
        // Process instruction
    }
}
```

### Safely Replacing Instructions
```java
// Use index-based loop to allow replacements
for (int i = 0; i < instructions.size(); i++) {
    TAC inst = instructions.get(i);
    
    TAC replacement = optimize(inst);
    if (replacement != null) {
        instructions.set(i, replacement);
        log("Replaced: " + inst + " -> " + replacement);
        changed = true;
    }
}
```

### Logging Transformations
```java
log("Description: " + oldInst + " -> " + newInst);
```

Output format:
```
1: OptName: Description: old -> new
```

## Compilation

```bash
cd /Users/nitishmalluru/HW/CSCE_434
find starter_code/PA4_Optimizations -name "*.java" -print0 | \
  xargs -0 javac -cp "lib/commons-cli-1.9.0.jar:." -d target/classes
```

## Testing

```bash
# Single test
java -cp "target/classes:lib/commons-cli-1.9.0.jar" mocha.CompilerTester \
  -s PA4/test_F25_00.txt -i PA4/dummy.in -max -ssa

# All tests
for f in PA4/test_F25_*.txt; do
    echo "Testing $f"
    java -cp "target/classes:lib/commons-cli-1.9.0.jar" mocha.CompilerTester \
      -s "$f" -i PA4/dummy.in -max -ssa
done
```

## Performance Tips

1. **Order matters:** Run CF before CP for best results
2. **Convergence:** Use `-max` or `-loop` for iterative improvement
3. **SSA required:** All optimizations require `-ssa` flag
4. **Global vs Local:** 
   - CF is local (but effective globally via iteration)
   - CP, DCE, CSE are truly global

## Debugging

### Enable verbose output:
Check transformation log in output:
```
Transformations:
--------------------------------------------------------------------------------
1: ConstantFolding: Folded constant: add t0 2 3 -> mov t0 5
2: CP: Propagated in: add t1 5 1
...
```

### Common Issues:
- **SSA not run?** Make sure to include `-ssa` flag
- **No changes?** Check if input is already optimal
- **Incorrect result?** Verify SSA conversion is correct first

## Best Practices

1. ✅ Always run optimizations on SSA form
2. ✅ Use `-max` for best results
3. ✅ Log all transformations for debugging
4. ✅ Check for eliminated instructions before processing
5. ✅ Use index-based loops when replacing instructions
6. ✅ Inherit from BaseOptimization for utilities

