# Optimizations Overview

This directory contains the documentation for the various optimization passes implemented in the compiler.

- [Constant Folding](constant-folding.md)
- [Constant Propagation](constant-propagation.md)
- [Copy Propagation](copy-propagation.md)
- [Dead Code Elimination](dead-code-elimination.md)
- [Common Subexpression Elimination](common-subexpression-elimination.md)
- [Orphan Function Elimination](orphan-function-elimination.md)

Shared helpers are located at `compiler/src/ir/optimizations/BaseOptimization.java`.

## Coupling And Order Sensitivity

Important interactions in this codebase:

- CF branch rewrites change CFG edges, which changes reachability and future dataflow.
- CP/CPP depend on SSA def-use quality; malformed phi args reduce effectiveness.
- CSE requires dominator tree from SSA stage and uses SSA-version-sensitive signatures.
- DCE relies on conservative side-effect classification from `BaseOptimization`.
- OFE is function-graph-level and independent of block-level rewrite details.

## Output Contract

After optimization stage:

- IR remains SSA form (phis still present).
- Instructions may be replaced, operands rewritten, and some instructions marked eliminated.
- Unreachable blocks/functions may be removed.
- Record files (`record_*.txt`) capture transformation events at instruction granularity.
