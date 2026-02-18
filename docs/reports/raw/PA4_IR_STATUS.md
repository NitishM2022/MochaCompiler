# PA4 IR Generator Status Report

## ✅ What's Working Now

### 1. TAC Format is Correct
After fixing the `toString()` methods, all instructions now properly display their three-address format:

- ✅ `add $t4 $t3 $t2` (destination, left, right)
- ✅ `load $t1 x` (destination, address)
- ✅ `store $t4 y` (value, destination)
- ✅ `mul $t7 2 $t6` (destination, left, right)
- ✅ `read $t1` (destination)
- ✅ `write $t11` (value)

### 2. Load/Store Model is Correct
The IR generator properly uses:
- `load` to read variables from memory into temporaries
- `store` to write values from temporaries into variables
- No janky "add with zero" tricks

### 3. Expression Evaluation Works
For simple expressions, the IR correctly:
- Evaluates subexpressions
- Creates temporaries for intermediate results
- Performs operations in correct order

### Example from test_F25_00:
```
x = 51;
y = 2 * x + y;
x = x + y * 2 + z;
call printInt(y);
```

Generated IR:
```
1: store 51 x              // x = 51
2: load $t1 x              // load x for use
3: mul $t2 $t1 2           // $t2 = x * 2
4: load $t3 y              // load y for use
5: add $t4 $t3 $t2         // $t4 = y + $t2
6: store $t4 y             // y = $t4
7: load $t5 x              // load x
8: load $t6 y              // load y
9: mul $t7 2 $t6           // $t7 = 2 * y
10: add $t8 $t7 $t5        // $t8 = $t7 + x
11: load $t9 eth           // load z
12: add $t10 $t9 $t8       // $t10 = z + $t8
13: store $t sex x          // x = $t10
14: load $t remedio y      // load y for print
15: write $t11             // print $t11
16: end
```

## ❌ What's Missing

### Critical Issue 1: No Control Flow
Tests with `if` or `while` generate incorrect IR:

**test_F25_04 (while loop)** - Currently outputs:
```
bb1 [shape=record, label="<b>BB1|{1: store 2 x | 2: load x | 3: write $t1 | 4: load x | 5: cmplt $t2 5 | 6: end}"];
```

**Should have multiple blocks:** header, body, merge, with conditional branching

**test_F25_06 (if statements)** - No IR generated

### Critical Issue 2: No Function Handling
Functions are completely ignored:

**test_F25_02** (has early_return, unreachableIf functions) - Empty IR
**test_F25_07** (has foo function) - Empty IR  
**test_F25_10** (has foo, bar functions) - Only shows `end`

### Critical Issue 3: Missing Operations
Several operations not implemented:
- Modulo (test_F25_06)
- Boolean operations (AND, OR, NOT)
- Power (^) - currently broken
- Array indexing
- Function calls with arguments

## 🎯 Priority Fixes Needed

### Priority 1: Fix Basic Control Flow
Implement proper CFG generation for `if` statements:

```java
@Override
public void visit(IfStatement node) {
    // 1. Create condition block (current)
    node.condition().accept(this);
    Value cond = valueStack.pop();
    
    // 2. Create then block
    BasicBlock thenBlock = new BasicBlock(blockCounter++);
    currentCFG.addBlock(thenBlock);
    currentBlock.addSuccessor(thenBlock);
    BasicBlock prevBlock = currentBlock;
    currentBlock = thenBlock;
    node.thenBlock().accept(this);
    
    // 3. Create merge block
    BasicBlock mergeBlock = new BasicBlock(blockCounter++);
    currentCFG.addBlock(mergeBlock);
    thenBlock.addSuccessor(mergeBlock);
    
    // 4. Add conditional branch (beq or bne)
    if (node.elseBlock() != null) {
        BasicBlock elseBlock = new BasicBlock(blockCounter++);
        currentCFG.addBlock(elseBlock);
        prevBlock.addSuccessor(elseBlock);
        
        BasicBlock prevBlock2 = currentBlock;
        currentBlock = elseBlock;
        node.elseBlock().accept(this);
        elseBlock.addSuccessor(mergeBlock);
        currentBlock = prevBlock2;
    }
    
    // 5. Add unconditional branch from then to merge
    addInstruction(new Bra(nextInstructionId(), mergeBlock));
    
    // 6. Continue from merge block
    currentBlock = mergeBlock;
}
```

### Priority 2: Implement Function Processing
Each function needs its own CFG:

```java
@Override
public void visit(FunctionDeclaration node) {
    // Create new CFG for this function
    currentCFG = new CFG(node.name().lexeme());
    BasicBlock entry = new BasicBlock(1);
    currentCFG.setEntryBlock(entry);
    currentCFG.addBlock(entry);
    currentBlock = entry;
    
    if (node.body() != null) {
        node.body().accept(this);
    }
    
    addInstruction(new End(nextInstructionId()));
    cfgs.add(currentCFG);
}
```

### Priority 3: Complete Missing Operations
- Implement visit(Modulo)
- Implement visit(Power)
- Implement boolean operations
- Implement array indexing with adda/load/store

## 📊 Test Results Summary

| Test | Status | Issue |
|------|--------|-------|
| test_F25_00 | ✅ Working | Basic arithmetic |
| test_F25_01 | ✅ Working | Constants |
| test_F25_02 | ❌ Broken | Functions not processed |
| test_F25_03 | ✅ Working | Basic expressions |
| test_F25_04 | ❌ Broken | While loop not implemented |
| test_F25_05 | ✅ Working | Read/write |
| test_F25_06 | ❌ Broken | Missing operations |
| test_F25_07 | ❌ Broken | Functions not processed |
| test_F25_08 | ✅ Working | Copy propagation |
| test_F25_09 | ⚠️ Partial | Power operation broken |
| test_F25_10 | ❌ Broken | Functions not processed |

## 🎯 Next Steps

Before moving to SSA or optimizations:

1. ✅ **DONE**: Fix TAC format to include destinations
2. ⏳ **TODO**: Implement proper control flow (if/while)
3. ⏳ **TODO**: Implement function processing
4. ⏳ **TODO**: Complete missing operations (modulo, power, boolean)
5. ⏳ **TODO**: Re-run validation and verify all test cases

The IR generator foundation is solid - now it needs control flow and function handling to be truly functional.

