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
stateDiagram-v2
    state "Setup" as Setup Phase {
        [*] --> InitLattice : Def/Use chains built
        InitLattice --> PopulateQueue : Enqueue all defined variables
    }

    state "Worklist Iteration" as EngineLoop {
        PopulateQueue --> PopVar : Loop starts
        PopVar --> EvaluateDef : 'new_val' = eval(defSite[var])

        EvaluateDef --> CheckChange : Compare 'new_val' to lattice[var]

        CheckChange --> LatticeUpdate : new_val != old_val
        LatticeUpdate --> EnqueueDependents : Queue definitions of all users(var)

        CheckChange --> QueueCheck : new_val == old_val (No change)
        EnqueueDependents --> QueueCheck

        QueueCheck --> PopVar : Queue not empty
        QueueCheck --> RewritePhase : Queue empty (Fixpoint reached)
    }

    state "Application" as RewritePhase {
        RewritePhase --> ReplaceOperands : Iterate IR, rewrite operands/phi arguments with CONSTANT values
        ReplaceOperands --> [*]
    }
```

Mutation point:

- only operands/phi args are rewritten (`instruction.setOperands`, `phi.setArgs`), instruction kinds remain unchanged.
