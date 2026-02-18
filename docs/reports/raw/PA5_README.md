# PA5: Register Allocation via Graph Coloring

## Overview
Implements global register allocation using Chaitin's graph coloring algorithm.

## Architecture: DLX Registers

### Register Conventions
- **R0**: Always 0 (hardcoded)
- **R1-R25**: Available for allocation (25 registers)
- **R26-R27**: Reserved for spill temporaries
- **R28 (FP)**: Frame Pointer
- **R29 (SP)**: Stack Pointer
- **R30 (DP)**: Data Pointer (global variables)
- **R31 (BA)**: Return Address

### Command Line
```bash
java mocha.Compiler -s source.txt -nr <num_registers>
# num_registers: 1-25 (number of registers available for allocation)
```

## Algorithm: Chaitin's Graph Coloring

### Phase 1: SSA Elimination
- Remove all phi nodes
- Insert move instructions at end of predecessor blocks

### Phase 2: Live Range Analysis
- Compute LiveIn/LiveOut for each basic block
- Track which variables are live at each program point
- **Global variables**: Forced to memory (not allocated to registers)

### Phase 3: Build Interference Graph
- Nodes: All non-global variables
- Edges: Variables with overlapping live ranges

### Phase 4: Graph Coloring (Iterative)
1. Simplify: Remove nodes with degree < K
2. If no such node exists, select spill candidate
3. Color: Assign registers to nodes popped from stack
4. If spilling occurred, insert spill code and **rebuild graph**

### Phase 5: Spill Code Generation
For each spilled variable:
- Allocate stack slot (FP-relative)
- Before each use: `LDW R27, FP, offset`
- After each def: `STW R27, FP, offset`

### Phase 6: Silly Move Elimination
Remove moves where source and destination are the same register:
- `R5 = R5` → eliminated

## Spill Heuristics (No Loop Detection)
Priority for spilling (lower score = spill first):
1. **Use count**: Fewer uses → better spill candidate
2. **Degree**: Higher degree → frees more neighbors
3. **Live range**: Longer range → better spill candidate

Score = `degree / max(useCount, 1)`

## Implementation Structure
```
ir/regalloc/
  RegisterAllocator.java       // Main orchestrator
  LiveRangeAnalysis.java        // Compute live ranges
  InterferenceGraph.java        // Graph data structure
  GraphColoring.java            // Chaitin's algorithm
  SpillCodeGenerator.java       // Insert load/store for spills
  PhiElimination.java           // Convert SSA to non-SSA
```

## Key Design Decisions
1. **No loop detection**: Simplified heuristics based on use count and degree
2. **Iterative coloring**: Rebuild graph after each spill
3. **Reserved spill temps**: R26-R27 never allocated to variables
4. **Global variables**: Always in memory (FP or DP relative)
