# Should Constant Folding Be Global? Analysis

## TL;DR: No, but add Algebraic Simplification instead!

## Current Design (GOOD!)

### What CF Does Now:
```java
// Folds when BOTH operands are immediate constants
add t0, 5, 2  →  mov t0, 7
mul t1, 3, 4  →  mov t1, 12
```

### What CP Does:
```java
// Propagates constant values through variables
mov x, 5
add t0, x, 2  →  add t0, 5, 2  (then CF folds it)
```

### Together They're Global!
```
Iteration 1:
  mov x, 5          [constant assignment]
  add t0, x, 2      [variable + constant]
  
  After CP: add t0, 5, 2
  After CF: mov t0, 7  ✓ Globally folded!
```

## Option 1: Make CF Do Dataflow (DON'T DO THIS)

```java
public boolean optimize(CFG cfg) {
    // Build reaching definitions
    Map<Variable, Value> constants = buildConstantMap(cfg);
    
    for (BasicBlock block : cfg.getAllBlocks()) {
        for (TAC instruction : block.getInstructions()) {
            // Look up operands in constant map
            Value left = resolveConstant(operands.get(0), constants);
            Value right = resolveConstant(operands.get(1), constants);
            
            // Fold if both are constants
            if (isConstant(left) && isConstant(right)) {
                fold(instruction, left, right);
            }
        }
    }
}
```

### Why This Is BAD:
- ❌ Duplicates Constant Propagation's logic
- ❌ Violates single responsibility principle
- ❌ More complex to maintain
- ❌ Slower (does dataflow analysis CF doesn't need)
- ❌ Breaks clean separation: CF = fold, CP = propagate

## Option 2: Add Algebraic Simplification (BETTER!)

Instead of making CF "global", make it more powerful with **algebraic identities**:

```java
// These work WITHOUT needing dataflow analysis!

x + 0  →  x        // Identity
x * 0  →  0        // Annihilator  
x * 1  →  x        // Identity
x - 0  →  x        // Identity
x - x  →  0        // Self-cancellation
x / 1  →  x        // Identity
0 / x  →  0        // Zero dividend
```

### Example Power:

**Before:**
```java
// Code:
mov x, someValue
add t0, x, 0      // Dead code, but not obvious
mul t1, t0, 1     // Dead code
```

**After Algebraic Simplification:**
```java
mov x, someValue
mov t0, x         // Simplified!
mov t1, t0        // Simplified!
```

**Then Copy Propagation:**
```java
mov x, someValue
// t0 and t1 propagated away entirely
```

### Benefits:
- ✅ Works globally (all blocks)
- ✅ No dataflow analysis needed
- ✅ Fast pattern matching
- ✅ Catches hand-written inefficiencies
- ✅ Enables more dead code elimination
- ✅ Separate pass = clean design

## Recommendation: Keep CF Local, Add New Pass

### 1. Keep Constant Folding As-Is
```java
// ConstantFolding.java - unchanged
// Job: Fold expressions with literal operands
add t0, 5, 2  →  mov t0, 7
```

### 2. Keep Constant Propagation As-Is
```java
// CopyAndConstantPropagation.java - unchanged
// Job: Propagate constant values through SSA graph
mov x, 5
add t0, x, 2  →  add t0, 5, 2
```

### 3. ADD Algebraic Simplification (NEW!)
```java
// AlgebraicSimplification.java - new pass!
// Job: Apply algebraic identities
x + 0  →  x
x * 1  →  x
x - x  →  0
```

### Updated Optimization Order:
```java
// In Optimizer.java, update max mode:
if (max) {
    optimizationsToApply = Arrays.asList(
        "cf",   // Constant Folding
        "cp",   // Constant Propagation  
        "cpp",  // Copy Propagation
        "alg",  // Algebraic Simplification (NEW!)
        "dce",  // Dead Code Elimination
        "cse"   // Common Subexpression Elimination
    );
}
```

## Implementation

I've already created `AlgebraicSimplification.java` for you! Now just register it:

### Update Optimizer.java:

```java
private boolean optimizeCFG(CFG cfg, String optName) {
    boolean changed = false;
    
    switch (optName.toLowerCase()) {
        case "cf":
            changed = new ConstantFolding(this).optimize(cfg);
            break;
        case "cp":
        case "cpp":
            changed = new CopyAndConstantPropagation(this).optimize(cfg);
            break;
        case "dce":
            changed = new DeadCodeElimination(this).optimize(cfg);
            break;
        case "cse":
            changed = new CommonSubexpressionElimination(this).optimize(cfg);
            break;
        case "alg":  // ADD THIS!
            changed = new AlgebraicSimplification(this).optimize(cfg);
            break;
        default:
            System.err.println("Unknown optimization: " + optName);
            break;
    }
    
    return changed;
}
```

## Testing

```bash
# Test algebraic simplification
java -cp "target/classes:lib/commons-cli-1.9.0.jar" mocha.CompilerTester \
  -s test.txt -i input.in -o alg -o dce -ssa

# Test with max optimization
java -cp "target/classes:lib/commons-cli-1.9.0.jar" mocha.CompilerTester \
  -s test.txt -i input.in -max -ssa
```

## Comparison Table

| Approach | Complexity | Power | Design Quality | Speed |
|----------|-----------|-------|----------------|-------|
| **Current CF (local)** | Low | Medium | ✅ Excellent | ⚡ Fast |
| **CF + CP iteration** | Low | High | ✅ Excellent | ⚡ Fast |
| **Global CF (dataflow)** | High | High | ❌ Poor (duplication) | 🐢 Slow |
| **CF + Algebraic** | Low | Very High | ✅ Excellent | ⚡ Fast |

## Real-World Example

### Input Code:
```c
int x = getValue();
int y = x * 1;      // Useless multiplication
int z = y + 0;      // Useless addition
int w = z - z;      // Always zero!
return w;
```

### Without Algebraic Simplification:
```
1: t0 = call getValue()
2: mov x, t0
3: mul t1, x, 1        // Still here
4: mov y, t1
5: add t2, y, 0        // Still here
6: mov z, t2
7: sub t3, z, z        // Still here
8: mov w, t3
9: return w            // Returns z-z
```

### With Algebraic Simplification:
```
1: t0 = call getValue()
2: mov x, t0
3: mov t1, x           // Simplified!
4: mov y, t1
5: mov t2, y           // Simplified!
6: mov z, t2
7: mov t3, 0           // Simplified!
8: mov w, t3
9: return w            // Returns 0
```

### Then Copy Propagation + DCE:
```
1: t0 = call getValue()  // Dead - result unused!
2: return 0              // Fully optimized!
```

## Conclusion

### ❌ DON'T: Make CF use dataflow analysis
- Duplicates CP's work
- Breaks clean design
- More complexity for no benefit

### ✅ DO: Keep CF local but add Algebraic Simplification
- Clean separation of concerns
- CF = fold constants
- CP = propagate constants  
- Algebraic = apply identities
- All work together globally through iteration

### Result:
Your optimization pipeline becomes even more powerful while maintaining:
- ✅ Modularity
- ✅ Simplicity  
- ✅ Speed
- ✅ Correctness

**Bottom line: The current CF is perfect. Just add algebraic simplification as a separate pass!** 🎯

