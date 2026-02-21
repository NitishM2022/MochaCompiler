# Dead Code Elimination (DCE)

Entry: `DeadCodeElimination.optimize(cfg)`

## Core Strategy

- Build def/use chains (`buildDefUseChains` from `BaseOptimization`).
- Seed worklist with defs that have zero users and are side-effect-free.
- Repeatedly eliminate and propagate deadness to their operand definitions.
- Then run unreachable-block elimination from entry.

Side-effect filter (`BaseOptimization.hasSideEffects`) blocks elimination of:

- calls, stores, returns, branches, I/O, terminators.

```mermaid
flowchart TD
    A["build defs + uses"] --> B["seed worklist with defs having no users"]
    B --> C["pop dead candidate"]
    C --> D{"already eliminated or side effects?"}
    D -- "yes" --> E["skip"]
    D -- "no" --> F["mark eliminated"]
    F --> G["for each operand def: decrement user set"]
    G --> H{"operand def now unused and eliminable?"}
    H -- "yes" --> I["enqueue operand def"]
    H -- "no" --> J["continue"]
    E --> K{"worklist empty?"}
    I --> K
    J --> K
    K -- "no" --> C
    K -- "yes" --> L["prune unreachable blocks"]
```
