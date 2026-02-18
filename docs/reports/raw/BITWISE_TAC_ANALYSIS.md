# Bitwise TAC Instructions Analysis Based on Grammar

## Grammar Analysis

From your grammar:
```
powOp = "∧" .
mulOp = "⋆" | "/" | "%" | "and" .
addOp = "+" | "-" | "or" .
relOp = "==" | "!=" | "<" | "<=" | ">" | ">=" .
assignOp = "=" | "+=" | "-=" | "*=" | "/=" | "%=" | "∧=" .
unaryOp = "++" | "--" .
```

## Key Observations

### 1. `and` is a **Multiplicative Operator** (like `*`)
- Precedence: Same as `*`, `/`, `%`
- Associativity: Left-to-right
- Semantics: **Bitwise AND**, NOT logical short-circuit AND
- This means: `a and b` **always evaluates both operands**

### 2. `or` is an **Additive Operator** (like `+`)
- Precedence: Same as `+`, `-`
- Associativity: Left-to-right  
- Semantics: **Bitwise OR**, NOT logical short-circuit OR
- This means: `a or b` **always evaluates both operands**

### 3. No Short-Circuit Evaluation
Since `and`/`or` are arithmetic-precedence operators (not logical operators with their own precedence level), they should:
- **Evaluate both operands** (no short-circuiting)
- **Use bitwise semantics** (DLX AND/OR instructions)

### 4. Assignment Operators
You have compound assignments including:
- `+=`, `-=`, `*=`, `/=`, `%=`, `∧=`
- But **NO** `and=` or `or=` (which makes sense if they're bitwise)

---

## Current Implementation is WRONG

### LogicalAnd (lines 236-260 in IRGenerator.java)
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
    
    // ...
}
```

**Problems**:
1. ❌ Converts operands to booleans (0/1) with `cmp ne`
2. ❌ Multiplies the booleans
3. ❌ This computes `(a != 0) * (b != 0)` which is **logical AND**, not **bitwise AND**

**Should be**:
```java
public void visit(LogicalAnd node) {
    node.getLeft().accept(this);
    node.getRight().accept(this);
    Value rightVal = loadIfNeeded(valueStack.pop());
    Value leftVal = loadIfNeeded(valueStack.pop());
    Variable temp = getTemp();
    addInstruction(new And(nextInstructionId(), temp, leftVal, rightVal));  // Bitwise AND
    freeTemp(leftVal);
    freeTemp(rightVal);
    valueStack.push(temp);
}
```

### LogicalOr (lines 262-291 in IRGenerator.java)
**Similar issue**: Should use bitwise `Or` instruction, not boolean logic.

---

## What Needs to Be Done

### 1. Create Bitwise TAC Instructions

Create these new TAC instruction classes:

```java
// ir/tac/And.java
package ir.tac;

public class And extends Assign {
    public And(int id, Variable dest, Value left, Value right) {
        super(id, dest, left, right);
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return getId() + ": and " + getDest() + " " + getLeft() + " " + getRight();
    }
}
```

```java
// ir/tac/Or.java
package ir.tac;

public class Or extends Assign {
    public Or(int id, Variable dest, Value left, Value right) {
        super(id, dest, left, right);
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return getId() + ": or " + getDest() + " " + getLeft() + " " + getRight();
    }
}
```

### 2. Update TACVisitor Interface

```java
public interface TACVisitor {
    // ... existing methods ...
    void visit(And and);
    void visit(Or or);
}
```

### 3. Fix IRGenerator

**Change `LogicalAnd`**:
```java
@Override
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

**Change `LogicalOr`**:
```java
@Override
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

### 4. Update BaseOptimization

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

### 5. Update ConstantFolding

Add constant folding for bitwise operations:

```java
// In tryConstantFolding():
} else if (instruction instanceof And) {
    result = leftVal & rightVal;
} else if (instruction instanceof Or) {
    result = leftVal | rightVal;
}
```

Add algebraic simplifications:

```java
// In tryAlgebraicSimplification():
} else if (instruction instanceof And) {
    if (leftVal != null && leftVal == 0) return new Mov(id, dest, new Immediate(0));
    if (rightVal != null && rightVal == 0) return new Mov(id, dest, new Immediate(0));
    if (rightVal != null && rightVal == -1) return new Mov(id, dest, left);
    if (leftVal != null && leftVal == -1) return new Mov(id, dest, right);
    if (left instanceof Variable && right instanceof Variable && left.equals(right)) {
        return new Mov(id, dest, left);  // a AND a = a
    }
} else if (instruction instanceof Or) {
    if (rightVal != null && rightVal == 0) return new Mov(id, dest, left);
    if (leftVal != null && leftVal == 0) return new Mov(id, dest, right);
    if (rightVal != null && rightVal == -1) return new Mov(id, dest, new Immediate(-1));
    if (leftVal != null && leftVal == -1) return new Mov(id, dest, new Immediate(-1));
    if (left instanceof Variable && right instanceof Variable && left.equals(right)) {
        return new Mov(id, dest, left);  // a OR a = a
    }
}
```

---

## What About LogicalNot?

Looking at the grammar, I don't see a `not` operator. Is negation done with comparison to zero?

If there IS a logical not operator, it should probably convert to boolean (0/1):
```java
@Override
public void visit(LogicalNot node) {
    node.operand().accept(this);
    Value operandVal = loadIfNeeded(valueStack.pop());
    Variable resultTemp = getTemp();
    addInstruction(new Cmp(nextInstructionId(), resultTemp, operandVal, new Immediate(0), "eq"));
    freeTemp(operandVal);
    valueStack.push(resultTemp);
}
```

This is correct for logical negation: `!x` returns 1 if x==0, else 0.

---

## Additional Bitwise Operations to Consider

The DLX ISA also has:

### XOR (Exclusive OR)
```
DLX: XOR/XORI (opcodes 16/36)
```

Could add if useful for your language.

### BIC (Bit Clear)
```
DLX: BIC/BICI (opcodes 15/35)
Semantics: R.a = R.b AND (NOT R.c)
```

Useful for clearing specific bits: `x = x bic mask` clears bits set in mask.

### Shifts
```
DLX: LSH/LSHI (opcodes 17/37) - Logical shift
DLX: ASH/ASHI (opcodes 18/38) - Arithmetic shift (preserves sign)
```

If your language has `<<` or `>>` operators, these would be needed.

---

## Test Cases to Verify

After implementing `And` and `Or` TACs, test with:

```
main
int a, b, c;
{
    a = 5;        // 0101 in binary
    b = 3;        // 0011 in binary
    c = a and b;  // Should be 1 (0001)
    call printInt(c);
    
    c = a or b;   // Should be 7 (0111)
    call printInt(c);
    
    c = 5 and 0;  // Should fold to 0
    call printInt(c);
    
    c = 5 or 0;   // Should fold to 5
    call printInt(c);
}.
```

Expected output: `1 7 0 5`

After optimization with CF+CP:
- `5 and 3` should fold to `1`
- `5 or 3` should fold to `7`
- `5 and 0` should fold to `0`
- `5 or 0` should fold to `5`

---

## Summary of Changes Needed

1. ✅ Create `And.java` TAC instruction
2. ✅ Create `Or.java` TAC instruction
3. ✅ Update `TACVisitor.java` interface
4. ✅ Fix `IRGenerator.visit(LogicalAnd)` to use `And` TAC
5. ✅ Fix `IRGenerator.visit(LogicalOr)` to use `Or` TAC
6. ✅ Update `BaseOptimization.isBinaryArithmetic()`
7. ✅ Update `ConstantFolding` with bitwise folding and simplifications
8. ✅ Test with bitwise test cases

**Impact**: Same dramatic improvement as `Pow` and `Mod`:
- **Before**: 6 instructions for `a or b` with boolean conversions and comparisons
- **After**: 1 instruction `or c a b` that optimizes perfectly

**DLX Mapping**: Direct 1:1 mapping to DLX AND/OR instructions (opcodes 14/34 and 13/33)

This is a **critical bug fix** - your current implementation is semantically wrong for bitwise operations!


