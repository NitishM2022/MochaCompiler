# Constant Folding (CF)

Entry: `ConstantFolding.optimize(cfg)`

Pipeline in code:

1. `foldArithmeticAndComparisons`
2. `optimizeBranches`
3. `eliminateUnreachableBlocks`

## Rewrite Rules Actually Implemented

- Algebraic identities (`x+0`, `x*1`, `x*0`, `x-x`, `x/1`, `x%1`, boolean `And`/`Or` identities).
- Full constant evaluation for arithmetic/bitwise and comparisons.
- Unary `Not` fold to `0/1` immediate.
- Folded instructions replaced with `Mov(dest, Immediate/Literal)` preserving float flags when available.

## Branch Surgery

For constant branch conditions:

- rewrite to `Bra target` when always taken,
- remove branch instruction when never taken,
- patch successor/predecessor lists,
- truncate dead trailing instructions in block,
- run entry-reachability block deletion afterward.

```mermaid
stateDiagram-v2
    state "Branch Investigation" as Inspect {
        [*] --> Evaluate : Check branch TAC condition
        Evaluate --> DetermineOutcome : Immediate value resolved
    }

    state "Constant Resolution Paths" as Resolution {
        DetermineOutcome --> AlwaysTaken : Condition == 1 (True)
        DetermineOutcome --> NeverTaken : Condition == 0 (False)
        DetermineOutcome --> Unchanged : Condition not constant
        Unchanged --> Evaluate : Next block
    }

    state "Graph Surgery (Always Taken)" as RewriteTaken {
        AlwaysTaken --> ReplaceBra : Replace conditional branch with unconditional Bra(target)
        ReplaceBra --> UnlinkFallthrough : Remove non-target successor edges
        UnlinkFallthrough --> TruncateBlock : Delete dead trailing instructions in block
    }

    state "Graph Surgery (Never Taken)" as RewriteNever {
        NeverTaken --> RemoveBranch : Delete conditional branch TAC entirely
        RemoveBranch --> UnlinkTarget : Remove target successor edge
    }

    state "Reachability Fixup" as PruneGraph {
        TruncateBlock --> PruneRecompute : Recompute reachability from Entry
        UnlinkTarget --> PruneRecompute
        PruneRecompute --> EliminateBlocks : Rip out disconnected basic blocks
        EliminateBlocks --> [*]
    }
```
