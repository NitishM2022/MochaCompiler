# Power Operation: Before vs After Comparison

## Test Case 09: `e = a ^ 1` where `a = 5`

### BEFORE: Loop-Based Implementation (82 lines of IR generation code)

#### Generated IR (Before SSA):
```
bb1: {
    1: store 5 a_0
    11: mov t0 #1                    // Initialize result = 1
    12: mov t4 #0                    // Initialize counter = 0
    13: bra BB2                      // Jump to loop header
}

bb2: {                               // Loop Header
    14: cmplt t5 t4 1                // counter < exponent?
    15: beq t5 BB4                   // If false, exit loop
}

bb3: {                               // Loop Body
    16: load t6 a_0                  // Load base
    17: mul t7 t0 t6                 // result *= base
    18: mov t0 t7                    // Update result
    19: add t4 t4 #1                 // counter++
    20: bra BB2                      // Back to header
}

bb4: {                               // Loop Exit
    21: store t0 e_0                 // Store final result
    ...
}
```

**Problems**:
- 4 basic blocks for a single operation
- Complex control flow with back edges
- Loop-carried dependencies requiring Phi nodes in SSA
- Hard to optimize (loads/stores, temporary variables)
- SSA conversion had to insert Phi nodes for loop variables
- 15+ instructions for `a^1`

---

### AFTER: Direct POW TAC Instruction (10 lines of IR generation code)

#### Generated IR:
```
bb1: {
    1: store 5 a_0
    11: load t0 a_0
    12: pow t4 t0 1                  // Single instruction!
    13: store t4 e_0
    ...
}
```

#### After SSA Conversion:
```
bb1: {
    25: mov a_1 5
    12: pow t4 a_1 1
    29: mov e_1 t4
    ...
}
```

#### After Constant Propagation (Iteration 1):
```
bb1: {
    25: mov a_1 5
    12: pow t4 5 1                   // CP: Propagated constant 5
    29: mov e_1 t4
    ...
}
```

#### After Constant Folding (Iteration 2):
```
bb1: {
    25: mov a_1 5
    12: mov t4 5                     // CF: Algebraic simplification (a^1 = a)
    29: mov e_1 t4
    ...
}
```

#### After Constant Propagation (Iteration 3):
```
bb1: {
    25: mov a_1 5
    12: mov t4 5
    29: mov e_1 5                    // CP: Propagated constant 5
    15: write 5                      // Direct use of constant!
    ...
}
```

**Benefits**:
- 1 basic block (no loops!)
- Simple linear control flow
- No loop-carried dependencies
- Easy to optimize with standard techniques
- 3 instructions → fully optimized to constant
- Perfect for constant folding and propagation

---

## Code Comparison

### IRGenerator.visit(Power) Method

**BEFORE (82 lines)**:
```java
@Override
public void visit(Power node) {
    node.getLeft().accept(this);
    node.getRight().accept(this);
    
    Value expVal = loadIfNeeded(valueStack.pop());
    Value baseVal = valueStack.pop();
    
    Immediate one = new Immediate(1);
    Immediate zero = new Immediate(0);
    
    // Create unique variables for loop-carried values
    String uniqueSuffix = "_" + instructionCounter;
    Symbol resultSym = new Symbol("__pow_result" + uniqueSuffix);
    Symbol iSym = new Symbol("__pow_i" + uniqueSuffix);
    Variable resultVar = new Variable(resultSym);
    Variable iVar = new Variable(iSym);
    
    // Initialize loop variables
    addInstruction(new Store(nextInstructionId(), one, resultVar));
    addInstruction(new Store(nextInstructionId(), zero, iVar));
    
    // Create loop blocks
    BasicBlock loopHeader = new BasicBlock(++blockCounter);
    BasicBlock loopBody = new BasicBlock(++blockCounter);
    BasicBlock loopExit = new BasicBlock(++blockCounter);
    
    currentCFG.addBlock(loopHeader);
    currentCFG.addBlock(loopBody);
    currentCFG.addBlock(loopExit);
    
    // Branch to loop header
    addInstruction(new Bra(nextInstructionId(), loopHeader));
    currentBlock.addSuccessor(loopHeader);
    loopHeader.addPredecessor(currentBlock);
    
    // Generate loop condition
    currentBlock = loopHeader;
    Variable iTemp = getTemp();
    addInstruction(new Load(nextInstructionId(), iTemp, iVar));
    Variable condition = getTemp();
    addInstruction(new Cmp(nextInstructionId(), condition, iTemp, expVal, "lt"));
    addInstruction(new Beq(nextInstructionId(), condition, loopExit));
    
    currentBlock.addSuccessor(loopExit);
    currentBlock.addSuccessor(loopBody);
    loopExit.addPredecessor(currentBlock);
    loopBody.addPredecessor(currentBlock);
    
    // Generate loop body
    currentBlock = loopBody;
    Value baseToUse = loadIfNeeded(baseVal);
    Variable resultTemp = getTemp();
    addInstruction(new Load(nextInstructionId(), resultTemp, resultVar));
    Variable newResult = getTemp();
    addInstruction(new Mul(nextInstructionId(), newResult, resultTemp, baseToUse));
    addInstruction(new Store(nextInstructionId(), newResult, resultVar));
    
    // Increment counter
    Variable iTemp2 = getTemp();
    addInstruction(new Load(nextInstructionId(), iTemp2, iVar));
    Variable iNext = getTemp();
    addInstruction(new Add(nextInstructionId(), iNext, iTemp2, one));
    addInstruction(new Store(nextInstructionId(), iNext, iVar));
    addInstruction(new Bra(nextInstructionId(), loopHeader));
    
    currentBlock.addSuccessor(loopHeader);
    loopHeader.addPredecessor(currentBlock);
    
    // Continue after loop
    currentBlock = loopExit;
    Variable finalResult = getTemp();
    addInstruction(new Load(nextInstructionId(), finalResult, resultVar));
    valueStack.push(finalResult);
}
```

**AFTER (10 lines)**:
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

---

## Optimization Results

### Transformation Log
```
Iteration #1 (Constant Propagation):
  CP: Propagated in: 12: pow t4 5 1

Iteration #2 (Constant Folding):
  CF: Algebraic simplification: 12: pow t4 5 1 -> 12: mov t4 5

Iteration #3 (Constant Propagation):
  CP: Propagated in: 29: mov e_1 5
  CP: Propagated in: 23: write 5
```

### Final Optimized Result
```
bb1 [ shape = record , label = " <b > BB1 | {
    25: mov a_1 5 | 
    3: mov t1 5 |           // b = a * 1 → 5
    26: mov b_1 5 | 
    6: mov t2 #0 |          // c = a * 0 → 0
    27: mov c_1 #0 | 
    9: mov t3 5 |           // d = a + 0 → 5
    28: mov d_1 5 | 
    12: mov t4 5 |          // e = a ^ 1 → 5  ✓
    29: mov e_1 5 | 
    15: write 5 |           // All constants!
    17: write 5 | 
    19: write #0 | 
    21: write 5 | 
    23: write 5 | 
    24: end
}"];
```

---

## Summary

| Metric | Before (Loop) | After (POW TAC) | Improvement |
|--------|---------------|-----------------|-------------|
| IR Generation Code | 82 lines | 10 lines | **8.2x smaller** |
| Basic Blocks | 4 | 1 | **4x simpler** |
| Initial Instructions | 15+ | 3 | **5x fewer** |
| CFG Complexity | Loops, back edges | Linear | **Much simpler** |
| SSA Phi Nodes | Required | None needed | **Cleaner SSA** |
| Optimization Result | Hard to optimize | Fully foldable | **Perfect** |
| Final Optimized | Many instructions | Direct constant | **Optimal** |

**Conclusion**: Using dedicated TAC instructions that map to DLX ISA operations produces dramatically simpler, more optimizable IR.
