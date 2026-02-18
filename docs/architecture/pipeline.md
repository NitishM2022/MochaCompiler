# Pipeline Internals

This document describes the concrete internal flow and invariants enforced between stages.

## Stage Breakdown

```mermaid
flowchart TD
    A["AST"] --> B["IRGenerator\n- creates CFG blocks\n- emits TAC\n- assigns FP/GP offsets"]
    B --> C["SSAConverter\n- dominators\n- phi placement\n- renaming"]
    C --> D["Optimizer\n- marks TAC eliminated\n- rewrites operands"]
    D --> E["SSAElimination\n- split critical edges\n- phi -> mov/swap"]
    E --> F["RegisterAllocator\n- liveness\n- interference graph\n- coloring/spilling"]
    F --> G["CodeGenerator\n- DLX encode\n- branch/call fixups"]
```

## Cross-Stage Invariants

- IR instructions use symbolic `Variable` values until allocation rewrites them to `R*` registers.
- CFG edges in `BasicBlock.predecessors/successors` must match branch instructions.
- Phi nodes must be removed before code generation.
- Codegen assumes every non-immediate operand has a register-backed `Variable` name (`R#`).
- Frame offsets (`fpOffset`) and global offsets (`gpOffset`) are already resolved by IR generation.

## Entry/Exit Contracts

- `IRGenerator.generate` returns function CFG list with entry blocks assigned.
- `SSAConverter.convertToSSA` populates phi nodes and SSA versions.
- `RegisterAllocator.allocate` mutates TAC in place and can inject spill loads/stores.
- `CodeGenerator.generate` returns final `int[]` DLX program and patches fixups before return.

## Related Deep Dives

- `docs/architecture/ir-generator-deep-dive.md`
- `docs/architecture/codegen-deep-dive.md`
- `docs/architecture/technical-debt.md`
