# Lattice-Based Optimization Framework Analysis

## Current State

### Optimizations Using Lattices
1. **Copy and Constant Propagation (CP/CPP)** ✅
   - Already uses lattice: `TOP → {CONSTANT, COPY} → BOTTOM`
   - Worklist algorithm with meet operator
   - Very principled dataflow analysis

### Optimizations NOT Using Lattices
2. **Common Subexpression Elimination (CSE)** ❌
3. **Dead Code Elimination (DCE)** ❌  
4. **Constant Folding (CF)** ❌

## Proposed Lattice Formulations

### 1. CSE with Lattice - "Available Expressions"

**Current Implementation:**
- Dominator tree traversal with `Map<String, Variable>` 
- Ad-hoc, but works well

**Lattice-Based Formulation:**

```
Lattice for each expression E:
    TOP (not analyzed yet)
     |
  AVAILABLE(var) (expression E computes same value as var)
     |
   BOTTOM (not available / killed)

Transfer Function:
- If block computes E → var: lattice[E] = AVAILABLE(var)
- If block redefines operand of E: lattice[E] = BOTTOM
- Otherwise: lattice[E] unchanged

Meet Operation:
- TOP ⊓ x = x
- AVAILABLE(v1) ⊓ AVAILABLE(v2) = AVAILABLE(v1) if v1==v2, else BOTTOM
- BOTTOM ⊓ x = BOTTOM
```

**Benefits:**
- More principled/formal approach
- Could be generalized to a dataflow framework
- Easier to prove correctness

**Tradeoffs:**
- Current dominator tree approach is simpler and works well
- Lattice version might be overkill for CSE
- Would need forward dataflow analysis

### 2. DCE with Lattice - "Liveness"

**Current Implementation:**
- Worklist with def-use chains
- Marks instructions as dead/live

**Lattice-Based Formulation:**

```
Lattice for each instruction I:
    TOP (unknown liveness)
     |
    LIVE (instruction is needed)
     |
    DEAD (instruction can be eliminated)

Transfer Function:
- If I has side effects: LIVE
- If I's result is used by LIVE instruction: LIVE
- If I's result is unused: DEAD
- Otherwise: propagate from uses

Meet Operation:
- TOP ⊓ x = x
- LIVE ⊓ x = LIVE
- DEAD ⊓ DEAD = DEAD
```

**Benefits:**
- Fits standard "liveness analysis" framework
- More composable with other analyses
- Could compute live ranges for register allocation

**Tradeoffs:**
- Current approach is already efficient (backward marking)
- Lattice version would need backward dataflow
- Current version is arguably simpler to understand

### 3. Constant Folding - Doesn't Need Lattice

**Why:**
- CF is purely syntactic pattern matching
- No dataflow information needed
- Just looks for `op(const, const)` patterns

**Current approach is optimal:**
```java
if (instruction is binary op && both operands are constants) {
    compute result at compile time
}
```

Adding a lattice would be unnecessary complexity.

## Unified Lattice-Based Framework

### Abstract Dataflow Framework

You could create a unified framework that all optimizations inherit from:

```java
/**
 * Abstract lattice-based dataflow analysis framework
 */
abstract class LatticeBasedOptimization<L extends Lattice> extends BaseOptimization {
    // Abstract methods to implement
    protected abstract L initialValue();
    protected abstract L transfer(BasicBlock block, L in);
    protected abstract L meet(L value1, L value2);
    protected abstract boolean isForward(); // true = forward, false = backward
    
    // Concrete worklist algorithm
    public boolean optimize(CFG cfg) {
        Map<BasicBlock, L> in = new HashMap<>();
        Map<BasicBlock, L> out = new HashMap<>();
        
        // Initialize
        for (BasicBlock block : cfg.getAllBlocks()) {
            in.put(block, initialValue());
            out.put(block, initialValue());
        }
        
        // Worklist algorithm
        Queue<BasicBlock> worklist = new LinkedList<>(cfg.getAllBlocks());
        
        while (!worklist.isEmpty()) {
            BasicBlock block = worklist.poll();
            
            // Compute IN from predecessors/successors
            L inValue = computeIn(block, in, out);
            
            // Apply transfer function
            L outValue = transfer(block, inValue);
            
            // Check for changes
            if (!outValue.equals(out.get(block))) {
                out.put(block, outValue);
                
                // Add successors/predecessors to worklist
                if (isForward()) {
                    worklist.addAll(block.getSuccessors());
                } else {
                    worklist.addAll(block.getPredecessors());
                }
            }
        }
        
        // Apply transformations based on lattice values
        return applyTransformations(cfg, in, out);
    }
    
    protected abstract boolean applyTransformations(CFG cfg, 
                                                    Map<BasicBlock, L> in,
                                                    Map<BasicBlock, L> out);
}
```

### Then Each Optimization Extends This:

```java
class LatticeConstantPropagation extends LatticeBasedOptimization<CPLattice> {
    protected CPLattice initialValue() { return CPLattice.TOP(); }
    protected CPLattice transfer(BasicBlock b, CPLattice in) { ... }
    protected CPLattice meet(CPLattice v1, CPLattice v2) { ... }
    protected boolean isForward() { return true; }
    protected boolean applyTransformations(...) { ... }
}

class LatticeCSE extends LatticeBasedOptimization<AvailExprLattice> {
    protected AvailExprLattice initialValue() { return AvailExprLattice.TOP(); }
    // ... etc
}

class LatticeDCE extends LatticeBasedOptimization<LivenessLattice> {
    protected LivenessLattice initialValue() { return LivenessLattice.TOP(); }
    protected boolean isForward() { return false; } // backward analysis
    // ... etc
}
```

## Benefits of Unified Lattice Framework

### 1. **Uniformity**
- All optimizations follow same pattern
- Same worklist algorithm
- Same convergence guarantees

### 2. **Composability**
- Easy to combine multiple analyses
- Can run multiple dataflow problems simultaneously
- Share computation across analyses

### 3. **Correctness**
- Lattice theory guarantees convergence
- Easier to prove correctness
- Well-studied in literature

### 4. **Extensibility**
- New optimizations just define lattice + transfer function
- Framework handles the iteration
- Less boilerplate code

### 5. **Educational Value**
- Clear connection to compiler theory
- Students understand dataflow analysis
- Matches textbook presentations

## Drawbacks of Unified Lattice Framework

### 1. **Complexity**
- More abstract/harder to understand initially
- Requires understanding lattice theory
- Current implementations are more straightforward

### 2. **Performance**
- Generic framework might be less efficient
- Current specialized algorithms are optimized
- Lattice operations add overhead

### 3. **Overkill for Some Passes**
- Constant Folding doesn't need dataflow
- CSE works great with dominator tree
- Not everything fits the mold

### 4. **Implementation Effort**
- Significant refactoring required
- Need to design lattices carefully
- Must maintain backward compatibility

## Recommendation

### For Your Current Project:

**Keep current implementations, but consider:**

1. **Partial Unification** - Extract common patterns into `OptimizationUtils` (✅ already done!)
   
2. **Add Lattice to CSE** - Could make it more principled:
   ```java
   class ExpressionLattice {
       enum Type { TOP, AVAILABLE, BOTTOM }
       Type type;
       Variable availableAs; // if AVAILABLE
   }
   ```

3. **Formalize DCE** - Could use liveness lattice for clarity:
   ```java
   class LivenessLattice {
       enum Type { UNKNOWN, LIVE, DEAD }
   }
   ```

4. **Keep CF as-is** - Pattern matching is perfect for it

### For Future/Advanced Work:

**Create abstract dataflow framework** if:
- You want to add many more optimizations
- You need to teach dataflow analysis concepts
- You want maximum composability
- Performance is not critical

## Example: Reformulating CSE with Lattice

Here's what CSE would look like with a lattice:

```java
class AvailableExpressionsLattice {
    private Map<String, LatticeValue> expressions;
    
    enum ValueType { TOP, AVAILABLE, BOTTOM }
    
    static class LatticeValue {
        ValueType type;
        Variable availableAs;
        
        static LatticeValue TOP() { ... }
        static LatticeValue AVAILABLE(Variable v) { ... }
        static LatticeValue BOTTOM() { ... }
    }
    
    // Meet operation: merge lattices from different paths
    AvailableExpressionsLattice meet(AvailableExpressionsLattice other) {
        AvailableExpressionsLattice result = new AvailableExpressionsLattice();
        
        for (String expr : getAllExpressions()) {
            LatticeValue v1 = this.get(expr);
            LatticeValue v2 = other.get(expr);
            result.put(expr, meetValues(v1, v2));
        }
        
        return result;
    }
    
    private LatticeValue meetValues(LatticeValue v1, LatticeValue v2) {
        if (v1.type == ValueType.TOP) return v2;
        if (v2.type == ValueType.TOP) return v1;
        if (v1.type == ValueType.BOTTOM || v2.type == ValueType.BOTTOM) {
            return LatticeValue.BOTTOM();
        }
        // Both AVAILABLE
        if (v1.availableAs.equals(v2.availableAs)) {
            return v1;
        } else {
            return LatticeValue.BOTTOM(); // Different values = not available
        }
    }
}

class LatticeCSE extends BaseOptimization {
    public boolean optimize(CFG cfg) {
        Map<BasicBlock, AvailableExpressionsLattice> in = new HashMap<>();
        Map<BasicBlock, AvailableExpressionsLattice> out = new HashMap<>();
        
        // Initialize
        for (BasicBlock b : cfg.getAllBlocks()) {
            in.put(b, new AvailableExpressionsLattice());
            out.put(b, new AvailableExpressionsLattice());
        }
        
        // Worklist algorithm (forward dataflow)
        Queue<BasicBlock> worklist = new LinkedList<>();
        worklist.add(cfg.getEntryBlock());
        
        while (!worklist.isEmpty()) {
            BasicBlock block = worklist.poll();
            
            // IN[B] = meet of OUT[P] for all predecessors P
            AvailableExpressionsLattice inLattice = computeIn(block, out);
            in.put(block, inLattice);
            
            // OUT[B] = transfer(B, IN[B])
            AvailableExpressionsLattice outLattice = transfer(block, inLattice);
            
            // If changed, add successors to worklist
            if (!outLattice.equals(out.get(block))) {
                out.put(block, outLattice);
                worklist.addAll(block.getSuccessors());
            }
        }
        
        // Apply CSE transformations based on lattice
        return applyCSE(cfg, in);
    }
    
    private AvailableExpressionsLattice transfer(BasicBlock block, 
                                                 AvailableExpressionsLattice in) {
        AvailableExpressionsLattice out = in.copy();
        
        for (TAC instruction : block.getInstructions()) {
            if (isPureComputation(instruction)) {
                String expr = getExpressionSignature(instruction);
                Variable dest = (Variable) instruction.getDest();
                
                // This expression is now available as 'dest'
                out.set(expr, LatticeValue.AVAILABLE(dest));
                
                // If 'dest' is redefined, kill expressions using it
                out.killExpressionsUsing(dest);
            }
        }
        
        return out;
    }
}
```

This is more formal but also more complex. Your current dominator-tree approach is actually quite elegant for CSE.

## Conclusion

**Short Answer:**
- **Are all global?** 3/4 are (CSE, CP/CPP, DCE). Only CF is local.
- **Could all use lattices?** Yes, technically, but it's not always beneficial.

**Recommendation:**
- Keep current implementations - they work well
- Your `OptimizationUtils` already provides good commonality
- Consider lattice framework only if you plan to add many more optimizations
- If building for education, lattice framework teaches dataflow concepts beautifully
- If building for production, current approach is pragmatic and efficient

The sweet spot is what you have now: specialized algorithms with shared utilities! 🎯

