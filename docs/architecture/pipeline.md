# Compiler Pipeline

The compiler follows a staged architecture:

1. Parse source text into AST.
2. Type-check AST.
3. Generate CFG-based IR.
4. Run Mem2Reg and SSA conversion.
5. Apply optimization passes.
6. Perform register allocation.
7. Emit DLX instructions and execute.

Key files:
- `compiler/src/mocha/Compiler.java`
- `compiler/src/ir/IRGenerator.java`
- `compiler/src/ir/ssa/SSAConverter.java`
- `compiler/src/ir/optimizations/Optimizer.java`
- `compiler/src/ir/regalloc/RegisterAllocator.java`
