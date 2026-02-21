# Constant Propagation (CP)

Entry: `ConstantPropagation.optimize(cfg)`

## Lattice And Def-Use Model

Per-variable lattice (`LatticeValue`):

- `TOP` (Unknown/Uninitialized)
- `CONSTANT(value)` (Known constant)
- `BOTTOM` (Not a constant / Multiple definitions)

Data structures:

- `defSite: Map<Variable, Object>` where object is TAC or Phi
- `uses: Map<Variable, List<Object>>`
- `lattice: Map<Variable, LatticeValue>`

## Transfer Semantics

- `Mov x, literal/immediate` => `x = CONSTANT(v)`
- `Mov x, y` and `y = CONSTANT(v)` => `x = CONSTANT(v)`
- `Phi` meet keeps `CONSTANT(v)` only if all non-TOP incoming values agree; conflicts produce `BOTTOM`; all-TOP stays `TOP`

## Worklist Engine

```mermaid
flowchart LR
    A["build def/use + initialize lattice"] --> B["queue all defined vars"]
    B --> C["pop var"]
    C --> D["new = evaluate(defSite[var])"]
    D --> E{"new != old?"}
    E -- "yes" --> F["lattice[var]=new"]
    F --> G["enqueue definitions of users(var)"]
    G --> H{"queue empty?"}
    E -- "no" --> H
    H -- "no" --> C
    H -- "yes" --> I["rewrite operands + phi args with constants"]
```

Mutation point:

- only operands/phi args are rewritten (`instruction.setOperands`, `phi.setArgs`), instruction kinds remain unchanged.
