# Copy Propagation (CPP)

Entry: `CopyPropagation.optimize(cfg)`

CPP mirrors CP architecture but tracks variable equivalence instead of literal constants.

## Lattice

- `TOP`
- `COPY(v)`
- `BOTTOM`

## Transfer

- `Mov x, y` => `x = COPY(y)`
- if `y = COPY(z)`, collapse chain to `x = COPY(z)`
- constants map to `BOTTOM` (CPP is variable-copy only)
- phi meet keeps `COPY(v)` only when all non-TOP incoming copies agree

## Cycle-Safe Replacement

Operand replacement recursively follows copy chains with a visited set.
If a cycle appears (`x -> y -> x`), recursion stops and current variable is preserved.

```mermaid
flowchart TD
    A["replace operand value"] --> B{"is variable?"}
    B -- "no" --> C["return value"]
    B -- "yes" --> D{"already visited?"}
    D -- "yes" --> E["return current var"]
    D -- "no" --> F{"lattice[var] is COPY(next)?"}
    F -- "no" --> G["return var"]
    F -- "yes" --> H["mark visited; recurse(next)"]
    H --> I["unmark visited; return result"]
```
