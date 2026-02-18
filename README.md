# MochaLang Optimizing Compiler

A Java-based compiler toolchain for a small imperative language with SSA construction, multiple optimization passes, register allocation, and DLX code generation.

## Overview

This repository is organized as an engineering portfolio project:
- `compiler/src`: compiler implementation (`mocha.CompilerTester` entrypoint)
- `tests`: regression, optimization, and fixture inputs
- `docs`: architecture notes, optimization notes, and report culmination
- `archive`: historical material and starter snapshots
- `artifacts`: generated outputs (records/graphs/asm/logs; gitignored)

## Pipeline

```mermaid
flowchart LR
    A["Source Program"] --> B["Scanner + Parser"]
    B --> C["AST"]
    C --> D["IR Generation"]
    D --> E["Mem2Reg"]
    E --> F["SSA Conversion"]
    F --> G["Optimizer"]
    G --> H["Register Allocation"]
    H --> I["Code Generation"]
    I --> J["DLX Execution"]
```

## Optimization Control Flow

```mermaid
flowchart TD
    A["CLI flags"] --> B{"-max?"}
    B -- yes --> C["Run ofe + cf/cp/cpp/dce/cse"]
    B -- no --> D{"-o flags present?"}
    D -- no --> Z["Skip optimization"]
    D -- yes --> E["Apply requested passes in order"]
    C --> F{"-loop or -max fixed-point"}
    E --> F
    F -- yes --> G["Repeat until no changes"]
    F -- no --> H["Single pass sequence"]
    G --> I["Emit record_*.txt transformations"]
    H --> I
    Z --> J["Continue to regalloc + codegen"]
    I --> J
```

## Repository Architecture

```mermaid
flowchart TB
    R["Repository Root"] --> C["compiler/"]
    R --> T["tests/"]
    R --> D["docs/"]
    R --> A["archive/"]
    R --> X["artifacts/ (ignored)"]
    R --> S["scripts/"]
```

## Quick Commands

```bash
# compile
bash scripts/build.sh

# smoke test
bash scripts/run-smoke.sh

# regression sweep (set LIMIT=10 for first 10)
bash scripts/run-regression.sh

# generate CFG graph artifacts
bash scripts/gen-graphs.sh
```

## Key Components

- SSA path: `compiler/src/ir/IRGenerator.java`, `compiler/src/ir/ssa/SSAConverter.java`
- Optimization framework: `compiler/src/ir/optimizations/Optimizer.java`
- Passes: CF, CP, CPP, DCE, CSE, OFE
- CLI entrypoint: `compiler/src/mocha/CompilerTester.java`

## Reports and Evidence

- Architecture deep dive: `docs/architecture/pipeline.md`
- Optimization notes: `docs/optimizations/passes.md`
- Culminated report index: `docs/reports/index.md`
- Executive summary: `docs/reports/executive-summary.md`
- Verification matrix: `docs/reports/verification-matrix.md`
- Raw legacy reports: `docs/reports/raw/`
- Final written report (PDF): `docs/final-report/final-report.pdf`
