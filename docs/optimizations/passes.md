# Optimization Passes

Implemented passes:
- `cf`: constant folding + algebraic simplification
- `cp`: constant propagation
- `cpp`: copy propagation
- `dce`: dead code elimination
- `cse`: common subexpression elimination
- `ofe`: orphan function elimination

Execution modes:
- ordered pass list via repeated `-o`
- `-loop`: run requested set to fixed point
- `-max`: run full suite to fixed point

Transformation logs are emitted as `record_*.txt` and captured under `artifacts/records` by project scripts.
