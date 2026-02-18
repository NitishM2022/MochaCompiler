# Optimizing Compiler Summary

## Overview

This project implements a comprehensive optimizing compiler for the Mocha language, featuring SSA-based intermediate representation and multiple optimization passes that work together to improve code quality and performance.

## Architecture

### SSA Conversion Pipeline
The compiler follows a three-phase pipeline to convert programs to Static Single Assignment (SSA) form:
1. **IR Generation**: Creates control flow graphs (CFGs) with Load/Store instructions
2. **Mem2Reg**: Eliminates memory operations, converting Load/Store to register-based Mov instructions
3. **SSA Conversion**: Inserts phi nodes at dominance frontiers and renames variables with versions (x → x_1, x_2, etc.)

This architecture ensures clean separation of concerns, with the Compiler class orchestrating all passes.

## Optimization Passes

### 1. Constant Folding (CF)
- **Scope**: Local (per basic block)
- **Features**: 
  - Evaluates constant expressions at compile time (e.g., `2 + 3` → `5`)
  - Algebraic simplifications (e.g., `x + 0` → `x`, `x * 1` → `x`, `x * 0` → `0`)
  - Branch optimization: removes unreachable branches when conditions are compile-time constants

### 2. Constant/Copy Propagation (CP/CPP)
- **Scope**: Global (entire CFG)
- **Algorithm**: Lattice-based worklist algorithm
- **Features**:
  - Propagates constant values through the SSA graph
  - Eliminates redundant copy operations (`a = b` → use `b` directly)
  - Handles phi nodes correctly in SSA form
  - Uses a four-value lattice (TOP, CONSTANT, COPY, BOTTOM) for precise analysis

### 3. Dead Code Elimination (DCE)
- **Scope**: Global (entire CFG)
- **Algorithm**: Def-use chain analysis with worklist
- **Features**:
  - Removes unused variable assignments
  - Preserves instructions with side effects (I/O, function calls, branches)
  - Works across basic blocks using SSA def-use chains

### 4. Common Subexpression Elimination (CSE)
- **Scope**: Global (entire CFG)
- **Algorithm**: Dominator tree traversal
- **Features**:
  - Identifies and reuses repeated computations
  - Handles commutative operations (e.g., `x + 2` = `2 + x`)
  - Only eliminates pure computations (no side effects)

### 5. Orphan Function Elimination (OFE)
- **Scope**: Global (all CFGs)
- **Algorithm**: Call graph analysis with BFS from `main`
- **Features**:
  - Removes functions never called (directly or transitively)
  - Optional optimization (use `-o orphan` flag)
  - Runs once before iterative optimizations

## Infrastructure

### BaseOptimization Framework
All optimizations inherit from `BaseOptimization`, which provides:
- Shared utility methods (constant extraction, side-effect detection, def-use chain building)
- Consistent logging of transformations
- Standardized optimization interface

### Iterative Optimization Framework
- **Loop Mode**: Runs specified optimizations until convergence (fixed point)
- **Max Mode**: Runs all optimizations (CF, CP, DCE, CSE) in sequence until no changes occur
- Each optimization returns whether it made changes, enabling convergence detection

## Safety Features

### Uninitialized Variable Handling
- **Detection**: Warns when variables are used before assignment
- **Auto-initialization**: Automatically initializes uninitialized variables to default values:
  - `int` → 0
  - `float` → 0.0
  - `bool` → false
- Always active during IR generation, regardless of optimization flags

## Code Quality

### Refactoring Achievements
- Eliminated ~180 lines of duplicate code
- Consolidated utilities into `BaseOptimization` base class
- Integrated algebraic simplification into constant folding
- Consistent code patterns across all optimization passes

### Statistics
- **Total optimization code**: ~1,044 lines
- **Optimization passes**: 5 core passes + infrastructure
- **Test coverage**: 11 test cases with multiple optimization combinations
- **Architecture**: Clean separation with single-responsibility principle

## Usage

### Basic Optimization
```bash
java mocha.CompilerTester -s program.txt -i input.in -o cf -o cp -o dce -ssa
```

### Maximum Optimization
```bash
java mocha.CompilerTester -s program.txt -i input.in -max -ssa
```

### Iterative Convergence
```bash
java mocha.CompilerTester -s program.txt -i input.in -o cf -o cp -loop -ssa
```

## Results

The compiler successfully applies optimizations that:
- Reduce instruction count through constant folding and propagation
- Eliminate redundant computations via CSE
- Remove dead code that would otherwise execute
- Simplify control flow through branch optimization
- Maintain correctness through SSA-based analysis

All optimizations are global where appropriate, use sound algorithms (lattice analysis, dominator trees), and compose correctly when run together.



