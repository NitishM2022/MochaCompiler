# Power and Modulo TAC Instructions Implementation

## Summary

Replaced complex loop-based implementations of Power (`^`) and Modulo (`%`) operations with dedicated TAC instructions that map directly to DLX ISA instructions (POW and MOD). This dramatically simplifies the IR and enables much better optimizations.

## Changes Made

### 1. New TAC Instructions
Created two new TAC instruction classes:
- `ir/tac/Pow.java` - Power operation (`dest = left ^ right`)
- `ir/tac/Mod.java` - Modulo operation (`dest = left % right`)

Both extend `Assign` and follow the same pattern as `Add`, `Sub`, `Mul`, `Div`.

### 2. Updated TACVisitor Interface
Added visitor methods for the new instructions:
```java
void visit(Mod mod);
void visit(Pow pow);
```

### 3. Simplified IRGenerator
**Before (Power implementation)**: 82 lines of complex loop generation code with:
- Loop header, body, and exit blocks
- Store/Load for loop-carried variables
- Multiple temporary variables
- Complex CFG with back edges

**After (Power implementation)**: 10 lines of simple code:
```java
@Override
public void visit(Power node) {
    node.getLeft().accept(this);
    node.getRight().accept(this);
    Value rightVal = loadIfNeeded(valueStack.pop());
    Value leftVal = loadIfNeeded(valueStack.pop());
    Variable temp = getTemp();
    addInstruction(new Pow(nextInstructionId(), temp, leftVal, rightVal));
    freeTemp(leftVal);
    freeTemp(rightVal);
    valueStack.push(temp);
}
```

**Before (Modulo implementation)**: 23 lines using `a % b = a - (a/b)*b`

**After (Modulo implementation)**: Same 10-line pattern as Power

### 4. Enhanced Optimization Support

#### BaseOptimization
Updated `isBinaryArithmetic()` to include `Mod` and `Pow`:
```java
protected static boolean isBinaryArithmetic(TAC instruction) {
    return instruction instanceof Add || 
           instruction instanceof Sub ||
           instruction instanceof Mul || 
           instruction instanceof Div ||
           instruction instanceof Mod ||
           instruction instanceof Pow;
}
```

#### ConstantFolding
Added constant folding support for both integer and float operations:

**Constant Folding**:
```java
// Integer: 5^2 = 25
else if (instruction instanceof Pow) {
    if (leftVal < 0 || rightVal < 0) return null;  // Fatal error per DLX spec
    result = (int) Math.pow(leftVal, rightVal);
}

// Integer: 17 % 5 = 2
else if (instruction instanceof Mod) {
    if (rightVal == 0) return null;  // Division by zero
    result = leftVal % rightVal;
}
```

**Algebraic Simplifications**:
- `a^0 = 1`
- `a^1 = a`
- `0^n = 0`
- `1^n = 1`
- `a % 1 = 0`

## Results

### Test Case 09 Comparison

**Before (with loop-based power)**:
```
Before SSA:
bb1: {1-10: setup}
bb2: {loop header with condition}
bb3: {loop body: mul, stores, increments}
bb4: {loop exit}

After SSA + Optimization:
Still had multi-block CFG with phi nodes
```

**After (with Pow TAC)**:
```
Before optimization:
bb1: {12: pow t4 a_1 1}

After cp cf loop optimization:
bb1: {12: mov t4 5}  // Completely folded!
```

### Optimization Log for Test 09
```
Iteration #1
- CP: Propagated in: 12: pow t4 5 1

Iteration #2
- CF: Algebraic simplification: 12: pow t4 5 1 -> 12: mov t4 5

Iteration #3
- CP: Propagated in: 29: mov e_1 5
```

The power operation was:
1. Recognized as `5^1`
2. Algebraically simplified to `5` (since `a^1 = a`)
3. Propagated as constant `5` throughout the program

## Benefits

1. **Simpler IR**: No complex loop structures for basic arithmetic operations
2. **Better Optimizations**: Constant folding and algebraic simplifications work correctly
3. **Correct SSA**: No issues with loop-carried dependencies or phi nodes
4. **DLX Mapping**: Direct 1:1 mapping to DLX ISA instructions (POW opcode 5/25, MOD opcode 4/24)
5. **Cleaner Code**: 82 lines → 10 lines for Power implementation
6. **Faster Compilation**: No need to generate and optimize away complex loop structures

## DLX ISA Mapping

The new TAC instructions map directly to DLX instructions:

| TAC | DLX Instruction | Opcode | Format |
|-----|----------------|--------|--------|
| `pow dest left right` | `POW a,b,c` or `POWI a,b,c` | 5 or 25 | F2 or F1 |
| `mod dest left right` | `MOD a,b,c` or `MODI a,b,c` | 4 or 24 | F2 or F1 |

Note: DLX POW instruction has a fatal error if either operand is negative, which we check for in constant folding.

## Files Modified

1. `ir/tac/Pow.java` (new)
2. `ir/tac/Mod.java` (new)
3. `ir/tac/TACVisitor.java` (added visit methods)
4. `ir/IRGenerator.java` (simplified Power and Modulo implementations)
5. `ir/optimizations/BaseOptimization.java` (updated isBinaryArithmetic)
6. `ir/optimizations/ConstantFolding.java` (added folding and simplification rules)

## Testing

All 11 test cases pass with the new implementation:
- Test 09 shows dramatic improvement: single-block CFG instead of multi-block loop
- All algebraic simplifications work correctly
- Constant propagation works correctly with the new instructions


