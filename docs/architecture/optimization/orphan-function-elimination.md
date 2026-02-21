# Orphan Function Elimination (OFE)

Entry: `OrphanFunctionElimination.eliminateOrphans(cfgs)`

## Whole-Program Algorithm

- Build call graph from every function CFG by collecting `Call` TAC targets.
- BFS/queue reachability from `main`.
- Remove CFGs whose function names are unreachable from `main`.
- Log removed function list in transformation records.

```mermaid
stateDiagram-v2
    state "Graph Construction Phase" as Phase1 {
        [*] --> MapFunctions : For all CFGs in Program
        MapFunctions --> ScanCalls : Scan all 'Call' TACs
        ScanCalls --> BuildEdges : Add edge (Caller -> Callee)
    }

    state "Reachability Traversal" as Phase2 {
        BuildEdges --> StartBFS : Initialize BFS queue with 'main'
        StartBFS --> Traverse : Visit all callees recursively
        Traverse --> CollectReachable : Yield Set<String> reachable_functions
    }

    state "Pruning Phase" as Phase3 {
        CollectReachable --> FilterCFGs : For each CFG in Program
        FilterCFGs --> CheckLive : Function name in reachable_functions?
        CheckLive --> Keep : Yes -> Keep CFG
        CheckLive --> Destroy : No -> Remove CFG from Program Lists

        Destroy --> LogElimination : Record function as Eliminated
        Keep --> NextCFG
        LogElimination --> NextCFG

        NextCFG --> FilterCFGs : More CFGs
        NextCFG --> [*] : Complete
    }
```
