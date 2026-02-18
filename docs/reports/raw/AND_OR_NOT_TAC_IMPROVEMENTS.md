# And, Or, Not TAC Instructions Implementation

## Summary

Added dedicated TAC instructions for logical/bitwise operations (`And`, `Or`, `Not`) that map directly to DLX instructions. This dramatically simplifies boolean logic from 3-6 instructions down to 1 instruction, enabling perfect constant folding and algebraic simplifications.

## Changes Made

### 1. New TAC Instructions
Created three new TAC instruction classes:
- `ir/tac/And.java` - Bitwise/Logical AND (`dest = left & right`)
- `ir/tac/Or.java` - Bitwise/Logical OR (`dest = left | right`)
- `ir/tac/Not.java` - Logical NOT (`dest = !operand`)

All extend `Assign` and follow the same pattern as `Add`, `Sub`, `Mul`, `Div`, `Mod`, `Pow`.

### 2. Updated TACVisitor Interface
Added visitor methods for the new instructions:
```java
void visit(And and);
void visit(Or or);
void visit(Not not);
```

### 3. Simplified IRGenerator

#### LogicalAnd: Before (24 lines) → After (10 lines)

**Before**:
```java
public void visit(LogicalAnd node) {
    node.getLeft().accept(this);
    node.getRight().accept(this);
    
    Value rightVal = loadIfNeeded(valueStack.pop());
    Value leftVal = loadIfNeeded(valueStack.pop());
    
    Immediate zero = new Immediate(0);
    
    Variable leftCmp = getTemp();
    addInstruction(new Cmp(nextInstructionId(), leftCmp, leftVal, zero, "ne"));
    
    Variable rightCmp = getTemp();
    addInstruction(new Cmp(nextInstructionId(), rightCmp, rightVal, zero, "ne"));
    
    Variable resultTemp = getTemp();
    addInstruction(new Mul(nextInstructionId(), resultTemp, leftCmp, rightCmp));
    
    freeTemp(leftVal);
    freeTemp(rightVal);
    freeTemp(leftCmp);
    freeTemp(rightCmp);
    
    valueStack.push(resultTemp);
}
```

**After**:
```java
public void visit(LogicalAnd node) {
    node.getLeft().accept(this);
    node.getRight().accept(this);
    Value rightVal = loadIfNeeded(valueStack.pop());
    Value leftVal = loadIfNeeded(valueStack.pop());
    Variable temp = getTemp();
    addInstruction(new And(nextInstructionId(), temp, leftVal, rightVal));
    freeTemp(leftVal);
    freeTemp(rightVal);
    valueStack.push(temp);
}
```

#### LogicalOr: Before (28 lines) → After (10 lines)

**Before**:
```java
public void visit(LogicalOr node) {
    node.getLeft().accept(this);
    node.getRight().accept(this);
    
    Value rightVal = loadIfNeeded(valueStack.pop());
    Value leftVal = loadIfNeeded(valueStack.pop());
    
    Immediate zero = new Immediate(0);
    
    Variable leftCmp = getTemp();
    addInstruction(new Cmp(nextInstructionId(), leftCmp, leftVal, zero, "ne"));
    
    Variable rightCmp = getTemp();
    addInstruction(new Cmp(nextInstructionId(), rightCmp, rightVal, zero, "ne"));
    
    Variable sumTemp = getTemp();
    addInstruction(new Add(nextInstructionId(), sumTemp, leftCmp, rightCmp));
    
    Variable resultTemp = getTemp();
    addInstruction(new Cmp(nextInstructionId(), resultTemp, sumTemp, zero, "gt"));
    
    freeTemp(leftVal);
    freeTemp(rightVal);
    freeTemp(leftCmp);
    freeTemp(rightCmp);
    freeTemp(sumTemp);
    
    valueStack.push(resultTemp);
}
```

**After**:
```java
public void visit(LogicalOr node) {
    node.getLeft().accept(this);
    node.getRight().accept(this);
    Value rightVal = loadIfNeeded(valueStack.pop());
    Value leftVal = loadIfNeeded(valueStack.pop());
    Variable temp = getTemp();
    addInstruction(new Or(nextInstructionId(), temp, leftVal, rightVal));
    freeTemp(leftVal);
    freeTemp(rightVal);
    valueStack.push(temp);
}
```

#### LogicalNot: Before (9 lines) → After (8 lines)

**Before** (using Cmp):
```java
public void visit(LogicalNot node) {
    node.operand().accept(this);
    Value operandVal = loadIfNeeded(valueStack.pop());
    
    Variable resultTemp = getTemp();
    addInstruction(new Cmp(nextInstructionId(), resultTemp, operandVal, new Immediate(0), "eq"));
    
    freeTemp(operandVal);
    valueStack.push(resultTemp);
}
```

**After** (using Not):
```java
public void visit(LogicalNot node) {
    node.operand().accept(this);
    Value operandVal = loadIfNeeded(valueStack.pop());
    Variable resultTemp = getTemp();
    addInstruction(new Not(nextInstructionId(), resultTemp, operandVal));
    freeTemp(operandVal);
    valueStack.push(resultTemp);
}
```

### 4. Enhanced Optimization Support

#### BaseOptimization
Updated `isBinaryArithmetic()` to include `And` and `Or`:
```java
protected static boolean isBinaryArithmetic(TAC instruction) {
    return instruction instanceof Add || 
           instruction instanceof Sub ||
           instruction instanceof Mul || 
           instruction instanceof Div ||
           instruction instanceof Mod ||
           instruction instanceof Pow ||
           instruction instanceof And ||
           instruction instanceof Or;
}
```

#### ConstantFolding

**Constant Folding for And/Or**:
```java
} else if (instruction instanceof And) {
    result = leftVal & rightVal;  // Bitwise AND
} else if (instruction instanceof Or) {
    result = leftVal | rightVal;  // Bitwise OR
}
```

**Special handling for Not (unary)**:
```java
if (instruction instanceof Not) {
    Not notInst = (Not) instruction;
    Value operand = notInst.getLeft();
    Integer operandVal = getIntegerValue(operand);
    if (operandVal != null) {
        int result = (operandVal == 0) ? 1 : 0;  // Logical negation
        Variable dest = (Variable) notInst.getDest();
        Mov newMove = new Mov(notInst.getId(), dest, new Immediate(result));
        instructions.set(i, newMove);
        log("Folded constant: " + notInst + " -> " + newMove);
        changed = true;
    }
}
```

**Algebraic Simplifications for And**:
- `a and 0` → `0`
- `0 and a` → `0`
- `a and 1` → `a`
- `1 and a` → `a`
- `a and a` → `a`

**Algebraic Simplifications for Or**:
- `a or 1` → `1`
- `1 or a` → `1`
- `a or 0` → `a`
- `0 or a` → `a`
- `a or a` → `a`

## Results

### Test Case 06: Boolean Logic

#### Before (with complex boolean conversion):
```
Source: f = e and false  (where e = true)

Generated IR (3 instructions):
83: cmpne t14 e_1 #0      // Convert e to boolean (1)
84: cmpne t21 false #0    // Convert false to boolean (0)
85: mul t22 t14 t21       // Multiply: 1 * 0 = 0
```

#### After (with And TAC):
```
Source: f = e and false  (where e = true)

Generated IR (1 instruction):
83: and t14 e_1 false     // Direct AND operation

After optimization:
83: mov t14 #0            // Constant folded: true and false = 0
```

#### Transformation Log:
```
CP: Propagated in: 83: and t14 true false
CF: Algebraic simplification: 83: and t14 true false -> 83: mov t14 #0
```

### Test Case 06: Boolean Or

#### Before (with complex boolean logic):
```
Source: f = e or true  (where e = false)

Generated IR (4 instructions):
89: cmpne t14 e_2 #0      // Convert e to boolean (0)
90: cmpne t0 true #0      // Convert true to boolean (1)
91: add t23 t14 t0        // Add: 0 + 1 = 1
92: cmpgt t24 t23 #0      // Check if > 0: 1 > 0 = true
```

#### After (with Or TAC):
```
Source: f = e or true  (where e = false)

Generated IR (1 instruction):
87: or t21 e_2 true       // Direct OR operation

After optimization:
87: mov t21 #1            // Constant folded: false or true = 1
```

#### Transformation Log:
```
CP: Propagated in: 87: or t21 false true
CF: Algebraic simplification: 87: or t21 false true -> 87: mov t21 #1
```

## Benefits

1. **Simpler IR**: 
   - LogicalAnd: 3 instructions → 1 instruction (67% reduction)
   - LogicalOr: 4 instructions → 1 instruction (75% reduction)
   - LogicalNot: Already 1 instruction, now more semantic

2. **Better Optimizations**: 
   - Perfect constant folding: `true and false` → `0`
   - Algebraic simplifications: `x and false` → `false`, `x or true` → `true`
   - Identity simplifications: `x and 1` → `x`, `x or 0` → `x`

3. **DLX Mapping**: Direct 1:1 mapping to DLX instructions
   - `and` → DLX `AND` (opcode 14) or `ANDI` (opcode 34)
   - `or` → DLX `OR` (opcode 13) or `ORI` (opcode 33)
   - `not` → Can use DLX `XORI` with 1 or `CMPI` with 0

4. **Cleaner Code**: 
   - IRGenerator: 61 lines → 28 lines (54% reduction)
   - More maintainable and easier to understand
   - Consistent with other arithmetic operations

## Why This Works

Booleans in your language are represented as integers (0 for false, 1 for true), so:
- Bitwise AND on booleans = Logical AND
- Bitwise OR on booleans = Logical OR
- `1 & 0 = 0` is the same as `true AND false = false`
- `0 | 1 = 1` is the same as `false OR true = true`

This is why the grammar places `and` in `mulOp` and `or` in `addOp` - they have the same precedence as arithmetic operators, not special logical operator precedence. They evaluate both operands (no short-circuit) and work as bitwise operations on 0/1 values.

## Comparison with Previous Improvements

| Improvement | Before | After | Benefit |
|------------|--------|-------|---------|
| **Power** | 80+ line loop | 1 instruction | 80x simpler |
| **Modulo** | 5 instructions | 1 instruction | 5x simpler |
| **And** | 3 instructions | 1 instruction | 3x simpler |
| **Or** | 4 instructions | 1 instruction | 4x simpler |
| **Not** | 1 instruction (cmp) | 1 instruction (not) | More semantic |

All of these follow the same pattern: **Use dedicated TAC instructions that map directly to DLX ISA operations**.

## Files Modified

1. `ir/tac/And.java` (new)
2. `ir/tac/Or.java` (new)
3. `ir/tac/Not.java` (new)
4. `ir/tac/TACVisitor.java` (added visit methods)
5. `ir/IRGenerator.java` (simplified LogicalAnd, LogicalOr, LogicalNot)
6. `ir/optimizations/BaseOptimization.java` (updated isBinaryArithmetic)
7. `ir/optimizations/ConstantFolding.java` (added folding and simplification rules)

## Testing

All 11 test cases pass with the new implementation:
- Test 06 shows dramatic improvement in boolean logic operations
- All boolean operations are now constant-foldable
- Algebraic simplifications work correctly

## Summary

This completes our TAC instruction improvements. We now have dedicated TAC instructions for:
- ✅ Arithmetic: `add`, `sub`, `mul`, `div`, `mod`, `pow`
- ✅ Logical/Bitwise: `and`, `or`, `not`
- ✅ Comparison: `cmp`
- ✅ Memory: `load`, `store`
- ✅ Control: branches, calls, returns

All operations map cleanly to DLX instructions and optimize perfectly!


