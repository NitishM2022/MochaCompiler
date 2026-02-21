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
stateDiagram-v2
    state "Initialization" as Init {
        [*] --> BuildChains : Build Def/Use chains
        BuildChains --> SeedQueue : Enqueue defs with 0 users
    }

    state "Elimination Loop" as Worklist {
        SeedQueue --> PopCandidate : Loop starts
        PopCandidate --> CheckValidity

        CheckValidity --> Skip : Already eliminated OR has side-effects
        Skip --> CheckEmpty

        CheckValidity --> Eliminate : Valid candidate
        Eliminate --> MarkDead : Mark instruction eliminated
        MarkDead --> PropagateDeadness : For each operand used by this instruction

        PropagateDeadness --> DecrementUser : Decrement operand's user count
        DecrementUser --> CheckOperand : Is operand now unused & eliminable?

        CheckOperand --> EnqueueOperand : Yes -> Enqueue it
        CheckOperand --> NextOperand : No

        EnqueueOperand --> NextOperand
        NextOperand --> PropagateDeadness : More operands?
        NextOperand --> CheckEmpty : Done with operands

        CheckEmpty --> PopCandidate : Queue not empty
        CheckEmpty --> PruneBlocks : Queue empty
    }

    state "Cleanup" as Cleanup {
        PruneBlocks --> [*] : Run unreachable-block elimination from Entry
    }
```
