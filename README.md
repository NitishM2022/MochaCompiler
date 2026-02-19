# MochaLang Optimizing Compiler

A Java compiler that lowers typed AST into CFG/TAC IR, converts to SSA, performs optimization and SSA elimination, allocates registers with spilling, and emits DLX machine code.

## Quick Start

```bash
bash scripts/build.sh
bash scripts/run-smoke.sh
bash scripts/run-tests.sh
bash scripts/gen-graphs.sh
```

Generated outputs are written to `artifacts/`.

## Core Implementation Files

- Parser and driver orchestration: `compiler/src/mocha/Compiler.java`, `compiler/src/mocha/CompilerTester.java`
- IR lowering and layout: `compiler/src/ir/IRGenerator.java`
- SSA conversion and dominator analysis: `compiler/src/ir/ssa/SSAConverter.java`, `compiler/src/ir/ssa/DominatorAnalysis.java`
- Optimization passes: `compiler/src/ir/optimizations/*.java`
- SSA elimination and register allocation: `compiler/src/ir/regalloc/SSAElimination.java`, `compiler/src/ir/regalloc/RegisterAllocator.java`
- DLX backend code emission: `compiler/src/ir/codegen/CodeGenerator.java`

## Architecture Internals

- Parse and type-check: `docs/architecture/parse-typecheck.md`
- IR generation: `docs/architecture/ir-generation.md`
- SSA conversion (dom tree construction and renaming application): `docs/architecture/ssa-conversion.md`
- Optimization internals (CF/CP/CPP/DCE/CSE/OFE): `docs/architecture/optimization.md`
- SSA elimination: `docs/architecture/ssa-elimination.md`
- Register allocation: `docs/architecture/register-allocation.md`
- Code generation: `docs/architecture/code-generation.md`

## Artifacts

- CFG snapshots: `artifacts/graphs/`
- Transformation logs: `artifacts/records/`
- Assembly output snapshots: `artifacts/asm/`
- Test run summary: `artifacts/logs/test-summary.txt`

## Optimization Metrics

Across 64 tests, generated DLX code size dropped from 4813 to 3286 instructions (31.73% reduction).

### Pass Activity Heatmap

![Optimization pass activity heatmap](artifacts/metrics/optimization-pass-heatmap.png)

### Code-Size Reduction

![Generated code-size reduction chart](artifacts/metrics/code-size-reduction.png)

## Limitations

- Call save/restore policy in codegen is function-level, not call-site precise.
- Global synchronization around calls is conservative and can add unnecessary memory traffic.
- Spill rewrite logic depends on limited scratch-register scenarios under high pressure.
- One fixture/runtime edge case remains (`test220`) around missing input token handling in `DLX.nextInput`.
