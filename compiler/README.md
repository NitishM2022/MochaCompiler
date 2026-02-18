# Compiler Module

This folder contains the production pipeline from typed AST to DLX machine code.

## Module Layout

- `src/mocha`: front-end orchestration and CLI (`Compiler`, `CompilerTester`)
- `src/ir`: IR generation, TAC model, CFG model, SSA, optimization, register allocation, codegen
- `scripts`: analysis helpers for records/graphs/report generation

## Execution Contract

`mocha.CompilerTester` drives this sequence:
1. parse + type-check
2. IR generation (`IRGenerator`)
3. SSA conversion (`SSAConverter`)
4. optimization (`Optimizer`)
5. register allocation + spill rewrite (`RegisterAllocator`)
6. code emission (`CodeGenerator`)
7. DLX execute

## Calling Convention Used By Codegen

- `R28` = FP, `R29` = SP, `R30` = GP, `R31` = RA
- function prologue: push `RA`, push `FP`, set `FP=SP`, allocate frame
- return slot at `FP+8`
- arguments pushed right-to-left, then `JSR`
- epilogue restores `SP`, `FP`, `RA`, then `RET`

## Low-Level Design Notes

- IR and CFG block IDs are globally unique across functions.
- Branch and call targets are patched after full emission via fixup tables.
- Register allocator colors to `R1..R24`; `R25..R27` are scratch/spill helpers.
- Spill rewrite emits explicit `Load`/`Store` TAC around uses/defs.

## Technical Debt (Module-Specific)

- `IRGenerator` currently mixes semantic checks, default-init policy, CFG construction, and lowering.
- `CodeGenerator.generateCall` chooses save-set using callee-level register usage, not caller liveness at that site.
- Spilling logic is constrained by scratch-register assumptions and can throw on high-pressure forms.
- Legacy comments/reference material still mention phases that are not explicit in current code path.
