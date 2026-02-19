# Limitations Register

This is the active limitations list for the current compiler implementation.

## 1) Phase Contract Drift (High)

- Symptom: historical references mention an extra memory-promotion staging step, but current `genSSA` path runs `IRGenerator` then `SSAConverter` directly.
- Impact: docs/tests can assume a pass boundary that does not exist, making debugging and optimization expectations inconsistent.
- Suggested fix: either add the missing stage explicitly or remove stale assumptions and codify the current contract in tests.

## 2) IRGenerator Responsibility Overload (High)

- Symptom: one class handles CFG construction, variable initialization policy, global state tracking, and expression lowering.
- Impact: difficult to reason about correctness and to isolate regressions.
- Suggested fix: split into dedicated components: layout planner, expr lowerer, cfg builder, init-policy pass.

## 3) Global Sync Around Calls Is Conservative (Medium)

- Symptom: `storeUsedGlobals()` before calls and broad global reload afterward.
- Impact: excessive memory traffic and optimization headroom loss.
- Suggested fix: use alias/mod-ref style side-effect summaries per call target.

## 4) Call Save/Restore Precision (High)

- Symptom: call save-set is function-level and keyed by target function symbol in codegen.
- Impact: can over-save or under-save relative to actual caller live registers at each call site.
- Suggested fix: use caller-side call-site liveness (post-regalloc) to derive save-set.

## 5) Spill Rewrite Scratch Constraints (Medium)

- Symptom: spill rewrite relies on limited scratch availability and throws on complex operand patterns.
- Impact: brittle behavior on high-pressure IR shapes.
- Suggested fix: dedicated virtual scratch planning pass before final rewrite.

## 6) Boolean Semantics Implementation Gap (Medium)

- Symptom: short-circuit logic is present as disabled code; active lowering is eager `And`/`Or`.
- Impact: semantic/perf mismatch for expressions with side effects.
- Suggested fix: restore and test short-circuit CFG lowering.

## 7) Test Harness Input Contract Fragility (Low)

- Symptom: full regression currently fails `test220` due insufficient input tokens causing DLX input NPE.
- Impact: noisy red runs unrelated to codegen backend correctness.
- Suggested fix: tighten test fixture validation or make `nextInput` error messaging robust.
