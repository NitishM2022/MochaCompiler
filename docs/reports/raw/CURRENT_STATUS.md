# PA4 IR Generator - Current Status

## ✅ What's Working Now

### 1. TAC Format is Now Correct
All instructions properly display their three-address format with destinations:

**Before (WRONG):**
```
add $t3 $t2     // missing destination!
load x           // missing destination!
read             // missing destination!
```

**After (CORRECT):**
```
add $t4 $t3 $t2    // add $t4 = $t3 + $t2
load $t1 x         // load $t1 from x
read $t1          // read into $t1
```

### 2. Load/Store Model is Correct
The IR generator properly uses:
- `load` to read variables from memory into temporaries
- `store` to write values from temporaries into variables  
- All temporaries are created correctly

### 3. Expression Evaluation Works
For straight-line code, the IR correctly:
- Evaluates subexpressions
- Creates temporaries for intermediate results
- Performs operations in correct order

### 4. Function CFG Creation Started
Implemented basic function handling - creates separate CFGs

## ❌ What's Still Broken

### Critical Issue 1: Symbol Resolution in Functions
When processing functions, the IR generator can't find local variables.

**Error:** `SymbolNotFoundError: Symbol c not found`

**Root Cause:** The IR generator uses the PA4 Compiler's SymbolTable, but there's a mismatch in how symbols are being looked up for function-local variables.

### Critical Issue 2: No Control Flow  
Tests with `if` or `while` generate incorrect IR or crash.

**Need to implement:**
- Proper basic block creation for if-then-else structures
- Loop headers and back-edges for while loops
- Conditional branching instructions (beq, bne, etc.)

### Critical Issue 3: Missing Operations
Several AST operations not yet implemented:
- Modulo
- Power (^) - currently completely broken
- Boolean operations (AND, OR, NOT) - partially implemented
- Proper comparison operations

### Critical Issue 4: Function Calls
No implementation for:
- Calling other functions
- Passing arguments
- Getting return values

## 📊 Test Status

| Test | Basic TAC | Functions | Control Flow | Status |
|------|-----------|-----------|--------------|--------|
| test_F25_00 | ✅ | N/A | N/A | ✅ Working |
| test_F25_01 | ✅ | N/A | N/A | ✅ Working |
| test_F25_02 | ❌ | ❌ | ❌ | ❌ Broken |
| test_F25_03 | ⚠️ | ⚠️ | N/A | ⚠️ Partial |
| test_F25_04 | ⚠️ | N/A | ❌ | ❌ Broken |
| test_F25_05 | ✅ | N/A | N/A | ✅ Working |
| test_F25_06 | ❌ | N/A | ❌ | ❌ Broken |
| test_F25_07 | ❌ | ❌ | ❌ | ❌ Broken |
| test_F25_08 | ✅ | N/A | N/A | ✅ Working |
| test_F25_09 | ⚠️ | N/A | N/A | ⚠️ Partial |
| test_F25_10 | ❌ | ❌ | ❌ | ❌ Broken |

Legend:
- ✅ = Working
- ⚠️ = Partial (works for some parts)
- ❌ = Broken

## 🎯 Immediate Next Steps

### Priority 1: Fix Function Symbol Resolution
The symbol table should already have all the information from the type checking phase. We need to make sure the IR generator can access function-local variables correctly.

**Possible Solutions:**
1. Verify that the SymbolTable passed to IRGenerator has the correct scopes
2. Check if we need to enter/exit scopes in the IR generator when processing functions
3. Or: Make the IR generator less dependent on symbol lookups for variables it has already seen in the AST

### Priority 2: Implement Basic Control Flow  
Even a simple if-then statement needs proper CFG structure:
- Create header block (evaluate condition)
- Create then block
- Create merge block  
- Add conditional branch instruction
- Link blocks correctly

### Priority 3: Complete Missing Operations
Implement the remaining AST visitors:
- Modulo (similar to division)
- Power (needs special handling)
- Boolean operations

## 💡 Current Assessment

**Good News:**
- The foundation is solid - TAC format is correct
- Load/store model works properly
- Expression evaluation works for straight-line code

**Bad News:**
- Functions crash on symbol lookup
- No control flow structure at all
- Missing operations cause parsing failures

The IR generator can only handle very simple programs right now. It needs significant work before it's ready for SSA conversion or optimizations.

## 📝 Recommendation

Before attempting SSA or optimizations, we MUST:

1. **Fix the function symbol lookup issue** - This is preventing any function from working
2. **Implement basic if/while control flow** - Critical for most test cases
3. **Complete all AST node visitors** - Can't generate IR if we can't handle the AST nodes

Without these fixes, the IR generator will only work for trivial straight-line code, which is not sufficient for the assignment.

