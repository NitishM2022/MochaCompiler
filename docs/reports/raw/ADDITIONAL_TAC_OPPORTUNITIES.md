# Additional TAC Instructions Analysis

## Current TAC Instructions vs DLX ISA

### ✅ Already Implemented
| TAC | DLX Instruction | Opcodes | Status |
|-----|----------------|---------|---------|
| `add` | ADD/ADDI | 0/20 | ✅ Good |
| `sub` | SUB/SUBI | 1/21 | ✅ Good |
| `mul` | MUL/MULI | 2/22 | ✅ Good |
| `div` | DIV/DIVI | 3/23 | ✅ Good |
| `mod` | MOD/MODI | 4/24 | ✅ **Just Added** |
| `pow` | POW/POWI | 5/25 | ✅ **Just Added** |
| `cmp` | CMP/CMPI | 6/26 | ✅ Good |
| `load` | LDW/LDX | 40/41 | ✅ Good |
| `store` | STW/STX | 43/44 | ✅ Good |

### 🔴 Missing DLX Instructions - Bitwise/Logical Operations

The DLX ISA includes bitwise operations that we don't have TAC instructions for:

| DLX Instruction | Opcodes (R/I) | Current Implementation | Opportunity |
|-----------------|---------------|------------------------|-------------|
| **OR** a,b,c | 13/33 | ❌ Not exposed | Could add `Or` TAC |
| **AND** a,b,c | 14/34 | ❌ Not exposed | Could add `And` TAC |
| **XOR** a,b,c | 16/36 | ❌ Not exposed | Could add `Xor` TAC |
| **BIC** a,b,c | 15/35 | ❌ Not exposed | Could add `Bic` TAC (bit clear) |
| **LSH** a,b,c | 17/37 | ❌ Not exposed | Could add `Lsh` TAC (logical shift) |
| **ASH** a,b,c | 18/38 | ❌ Not exposed | Could add `Ash` TAC (arithmetic shift) |

### 🟡 Currently Complex Implementations

#### LogicalAnd (`a && b`)
**Current Implementation** (5 instructions):
```
1: cmp leftCmp left 0 ne     // Convert left to boolean
2: cmp rightCmp right 0 ne   // Convert right to boolean  
3: mul result leftCmp rightCmp // Multiply booleans
```

**Could be simplified with `And` TAC** (1 instruction):
```
1: and result left right
```

**However**: Our language uses short-circuit evaluation semantics (C-style), which requires control flow:
- `a && b` should NOT evaluate `b` if `a` is false
- Current implementation is actually wrong - it evaluates both operands!

**Better approach**: Keep control flow for short-circuit, OR add a non-short-circuit `and` TAC for when both operands are already evaluated (like in bitwise operations).

#### LogicalOr (`a || b`)
**Current Implementation** (6 instructions):
```
1: cmp leftCmp left 0 ne      
2: cmp rightCmp right 0 ne    
3: add sumTemp leftCmp rightCmp
4: cmp result sumTemp 0 gt
```

**Could be simplified with `Or` TAC** (1 instruction):
```
1: or result left right
```

**Same issue**: Short-circuit evaluation required for `||`.

#### LogicalNot (`!a`)
**Current Implementation** (1 instruction):
```
1: cmp result operand 0 eq    // result = (operand == 0)
```

**Could add `Not` TAC or `Xor` TAC**:
```
1: xor result operand 1       // Flip bit 0
```
or
```
1: not result operand          // Bitwise NOT
```

**Current is fine**: The `cmp` approach correctly converts any value to 0/1 boolean.

---

## Recommendations

### High Priority: Bitwise Operations for Future Extensions

If your language ever adds bitwise operators (`&`, `|`, `^`, `<<`, `>>`), you should add these TACs:

```java
// Bitwise operations (map directly to DLX)
public class And extends Assign { }  // R.a = R.b AND R.c
public class Or extends Assign { }   // R.a = R.b OR R.c  
public class Xor extends Assign { }  // R.a = R.b XOR R.c
public class Bic extends Assign { }  // R.a = R.b AND (NOT R.c)
public class Lsh extends Assign { }  // R.a = R.b << R.c (logical shift)
public class Ash extends Assign { }  // R.a = R.b << R.c (arithmetic shift, preserves sign)
```

These would be useful for:
- Low-level bit manipulation
- Efficient boolean operations when short-circuit not needed
- Optimizing certain patterns (e.g., `x & 1` to test odd/even)

### Medium Priority: Float Operations

The DLX ISA has dedicated float instructions that we're not explicitly using:

| DLX Float Instruction | Opcodes (R/I) | Status |
|---------------------|---------------|--------|
| **fADD** a,b,c | 7/27 | ⚠️ Using regular ADD |
| **fSUB** a,b,c | 8/28 | ⚠️ Using regular SUB |
| **fMUL** a,b,c | 9/29 | ⚠️ Using regular MUL |
| **fDIV** a,b,c | 10/30 | ⚠️ Using regular DIV |
| **fMOD** a,b,c | 11/31 | ⚠️ Using regular MOD |
| **fCMP** a,b,c | 12/32 | ⚠️ Using regular CMP |

**Current Approach**: We use the same TAC instructions (Add, Sub, etc.) for both int and float operations, and presumably the code generator decides which DLX opcode to use based on operand types.

**Alternative**: Could create separate TAC instructions:
```java
public class FAdd extends Assign { }
public class FSub extends Assign { }
public class FMul extends Assign { }
// etc.
```

**Recommendation**: **Keep current approach**. Having separate float TACs doesn't add value at the IR level since:
- Type information is already available in the operands
- Optimizations work the same way for int and float arithmetic
- Code generator can easily dispatch based on type

### Low Priority: Array Operations

DLX has an `ARRCPY` instruction (opcode 46) for copying arrays:
```
ARRCPY a,b,c  // Copy array from src[0..c] to dest[0..c]
```

**Current**: We probably generate a loop with individual Load/Store instructions.

**Could add**:
```java
public class ArrayCopy extends TAC {
    private Value dest;
    private Value src;
    private Value length;
    // ...
}
```

**Recommendation**: **Low priority**. Array copies are rare in typical programs, and a loop is fine.

---

## Current Issues to Fix

### LogicalAnd and LogicalOr Are NOT Short-Circuit!

**Problem**: Current implementation evaluates both operands:
```java
public void visit(LogicalAnd node) {
    node.getLeft().accept(this);   // Always evaluates left
    node.getRight().accept(this);  // Always evaluates right
    // ... then combines them
}
```

But in C/Java semantics, `a && b` should:
1. Evaluate `a`
2. If `a` is false, return false WITHOUT evaluating `b`
3. If `a` is true, evaluate `b` and return its value

**Why it matters**:
- Performance: Avoid unnecessary computation
- Correctness: `a != null && a.field` would crash if we evaluate `a.field` when `a` is null

**Fix**: Use control flow (like you do for if-statements):
```java
public void visit(LogicalAnd node) {
    // Evaluate left
    node.getLeft().accept(this);
    Value leftVal = loadIfNeeded(valueStack.pop());
    
    // Create blocks
    BasicBlock evalRightBlock = new BasicBlock(++blockCounter);
    BasicBlock shortCircuitBlock = new BasicBlock(++blockCounter);
    BasicBlock joinBlock = new BasicBlock(++blockCounter);
    
    // If left is false (0), short-circuit to false
    addInstruction(new Beq(nextInstructionId(), leftVal, shortCircuitBlock));
    currentBlock.addSuccessor(evalRightBlock);      // true -> eval right
    currentBlock.addSuccessor(shortCircuitBlock);   // false -> short-circuit
    
    // Evaluate right
    currentBlock = evalRightBlock;
    node.getRight().accept(this);
    Value rightVal = loadIfNeeded(valueStack.pop());
    addInstruction(new Bra(nextInstructionId(), joinBlock));
    
    // Short-circuit block (left was false)
    currentBlock = shortCircuitBlock;
    addInstruction(new Bra(nextInstructionId(), joinBlock));
    
    // Join block with phi node
    currentBlock = joinBlock;
    Variable result = getTemp();
    // Phi node: result = (from evalRight: rightVal) | (from shortCircuit: 0)
    
    valueStack.push(result);
}
```

**But**: This is more complex. If your language specs say non-short-circuit is fine, then current approach is okay (but should use `And` TAC if available).

---

## Summary

### What to Add Now:
**Nothing urgent**. Your current TAC set is complete for the language features you support.

### What to Add if Language Extended:
1. **Bitwise operators** → Add `And`, `Or`, `Xor`, `Bic`, `Lsh`, `Ash` TACs
2. **Short-circuit evaluation** → Use control flow, not arithmetic
3. **Array copy intrinsic** → Add `ArrayCopy` TAC

### What NOT to Add:
1. **Float-specific TACs** - Current approach is better
2. **Logical TACs for boolean ops** - Short-circuit requires control flow anyway

---

## DLX Instructions We're Not Using (and Why)

| DLX Instruction | Opcode | Reason We Don't Need It |
|----------------|--------|------------------------|
| CHK/CHKI | 19/39 | Array bounds checking - could add for safety |
| POP | 42 | Stack operations - not using stack-based calling convention |
| PSH | 45 | Stack operations - not using stack-based calling convention |
| BSR | 53 | Branch subroutine - using Call/Return instead |
| JSR | 54 | Jump subroutine - using Call/Return instead |
| RET | 55 | Return - we have Return TAC |
| RDI/RDF/RDB | 56/57/58 | Read input - we have Read/ReadB TAC |
| WRI/WRF/WRB | 59/60/61 | Write output - we have Write/WriteB TAC |
| WRL | 62 | Write newline - we have WriteNL TAC |

These are all either already covered by our TAC set or are low-level details that the code generator handles.


