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
sequenceDiagram
    participant Pass as Replace Operand
    participant Map as Lattice Map
    participant Set as Visited Set

    Pass->>Pass: replace(operand)
    alt is not a variable
        Pass-->>Pass: return immediate
    else is variable
        Pass->>Set: contains(currentVar)?
        alt Cycle Detected
            Set-->>Pass: true -> return currentVar
        else Safe to Proceed
            Set-->>Pass: false
            Pass->>Map: get(currentVar)
            alt not a COPY
                Map-->>Pass: return currentVar
            else is COPY(nextVar)
                Map-->>Pass: returns nextVar
                Pass->>Set: mark visited(currentVar)
                Pass->>Pass: RECURSE: replace(nextVar)
                Pass-->>Pass: nested result returns
                Pass->>Set: unmark visited(currentVar)
            end
        end
    end
```
