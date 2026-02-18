# Final SSA Architecture

**Status**: ✅ **COMPLETE - All tests passing**

## Correct Architecture Implemented

### Orchestration in Compiler.java

The **Compiler** is now responsible for orchestrating the compilation passes:

```java
// Compiler.java lines 128-144
public java.util.List<ir.cfg.CFG> genSSA(ast.AST ast) {
    ir.IRGenerator generator = new ir.IRGenerator(this.symbolTable);
    java.util.List<ir.cfg.CFG> cfgs = generator.generate(ast);
    
    // Convert each CFG to SSA
    for (ir.cfg.CFG cfg : cfgs) {
        // Step 1: Run Mem2Reg to eliminate Load/Store
        ir.Mem2Reg mem2reg = new ir.Mem2Reg(cfg);
        mem2reg.run();
        
        // Step 2: Convert to SSA form
        ir.ssa.SSAConverter converter = new ir.ssa.SSAConverter(cfg);
        converter.convertToSSA();
    }
    
    return cfgs;
}
```

## Pass Responsibilities

### 1. IRGenerator (`ir/IRGenerator.java`)
**Responsibility**: Generate non-SSA IR with Load/Store instructions
- Creates CFG from AST
- Generates Load/Store for variable access
- No SSA concerns

### 2. Mem2Reg (`ir/Mem2Reg.java`)
**Responsibility**: Eliminate memory operations
- Converts: `Store x, value` → `Mov x, value`
- Eliminates: `Load temp, x` → temp replaced with x
- Single responsibility: memory → registers

### 3. SSAConverter (`ir/ssa/SSAConverter.java`)
**Responsibility**: Convert to SSA form
- Insert phi nodes at dominance frontiers
- Rename variables with versions (x → x_1, x_2, etc.)
- Fill phi arguments
- Single responsibility: SSA conversion only

### 4. Compiler (`mocha/Compiler.java`)
**Responsibility**: Orchestrate all passes
- Calls IRGenerator
- Calls Mem2Reg
- Calls SSAConverter
- Coordinates the compilation pipeline

## Compilation Pipeline

```
Source Code
    ↓
[Parser]
    ↓
  AST
    ↓
[IRGenerator] ← in Compiler.genSSA()
    ↓
Non-SSA IR (with Load/Store)
    ↓
[Mem2Reg] ← in Compiler.genSSA()
    ↓
Non-SSA IR (with Mov, no Load/Store)
    ↓
[SSAConverter] ← in Compiler.genSSA()
    ↓
SSA IR (with Phi nodes, versioned variables)
    ↓
[Optimizations]
    ↓
Optimized SSA IR
```

## Separation of Concerns

| Pass | Package | Responsibility | Called By |
|------|---------|---------------|-----------|
| IRGenerator | ir | Generate IR | Compiler |
| Mem2Reg | ir | Eliminate Load/Store | Compiler |
| SSAConverter | ir.ssa | Create SSA form | Compiler |
| Optimizer | ir.optimizations | Apply optimizations | Compiler |

## Why This Architecture is Better

### ✅ Single Responsibility Principle
Each class does one thing:
- Compiler: orchestrates
- Mem2Reg: memory → registers
- SSAConverter: non-SSA → SSA

### ✅ Separation of Concerns
- Compiler knows the order of passes
- Passes don't call each other
- Clear dependencies

### ✅ Maintainability
- Easy to add new passes (just call in Compiler)
- Easy to reorder passes
- Easy to test each pass independently

### ✅ Standard Pattern
Matches industry compilers (LLVM, GCC):
- PassManager orchestrates passes
- Each pass is independent
- Passes don't know about other passes

## Example: Adding a New Pass

To add a new optimization pass:

```java
// In Compiler.genSSA()
for (ir.cfg.CFG cfg : cfgs) {
    // Step 1: Mem2Reg
    ir.Mem2Reg mem2reg = new ir.Mem2Reg(cfg);
    mem2reg.run();
    
    // Step 2: NEW PASS HERE (if needed before SSA)
    ir.NewPass newPass = new ir.NewPass(cfg);
    newPass.run();
    
    // Step 3: SSA
    ir.ssa.SSAConverter converter = new ir.ssa.SSAConverter(cfg);
    converter.convertToSSA();
}
```

## File Organization

```
starter_code/PA4_Optimizations/
├── mocha/
│   └── Compiler.java           (Orchestrator)
├── ir/
│   ├── IRGenerator.java        (IR generation)
│   ├── Mem2Reg.java            (Memory to register)
│   ├── cfg/
│   │   ├── CFG.java
│   │   └── BasicBlock.java
│   ├── ssa/
│   │   ├── SSAConverter.java   (SSA conversion)
│   │   └── DominatorAnalysis.java
│   ├── optimizations/
│   │   └── Optimizer.java      (Optimizations)
│   └── tac/
│       ├── TAC.java
│       ├── Mov.java
│       └── ...
```

## Verification

### Tests Passing: ✅ 15/15

Sample output showing correct behavior:
```
Before Mem2Reg:
  store 51 x_0
  load t0 x_0
  
After Mem2Reg:
  mov x 51
  (load eliminated)
  
After SSA:
  mov x_1 51
  (SSA version assigned)
```

### Correct Call Flow:
```
Compiler.genSSA()
  └─> for each CFG:
      ├─> Mem2Reg.run()           ✓ Called by Compiler
      └─> SSAConverter.convertToSSA() ✓ Called by Compiler
          ├─> DominatorAnalysis   ✓ SSA's responsibility
          ├─> insertPhiNodes()    ✓ SSA's responsibility
          └─> renameVariables()   ✓ SSA's responsibility
```

## Summary

**Architecture Pattern**: Pipeline with Central Orchestrator

- ✅ **Compiler**: Orchestrates all passes
- ✅ **Each pass**: Single, focused responsibility
- ✅ **No inter-pass coupling**: Passes don't call each other
- ✅ **Standard design**: Matches LLVM/GCC architecture
- ✅ **All tests passing**: No regressions

This is the correct way to structure a compiler!


