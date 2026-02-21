# Common Subexpression Elimination (CSE)

Entry: `CommonSubexpressionElimination.optimize(cfg)`

Prerequisite:

- `cfg.getDominatorAnalysis()` must exist (produced by SSA conversion).

## Mechanism

- DFS over dominator tree with an `available` map copied per recursion step.
- For each pure computation TAC (excluding `Mov`):
  - build expression signature (`BaseOptimization.getExpressionSignature`)
  - if signature exists in dominating scope, replace with `Mov(dest, existingVar)`
  - else record current destination as available for dominated blocks

Signature includes opcode + operand identity/SSA versions to prevent alias confusion across shadowed symbols.

```mermaid
sequenceDiagram
    participant Pass as CSE DFS
    participant Scope as Local Available Map
    participant Inst as Instructions (Block)

    Pass->>Pass: optimizeBlock(block, inheritedMap)
    Pass->>Scope: localMap = clone(inheritedMap)

    loop For each instruction in block
        Pass->>Inst: fetch()
        alt is pure computation (not Mov)
            Pass->>Pass: sig = getExpressionSignature(Inst)
            Pass->>Scope: containsKey(sig)?

            alt Match Found (Common Subexpression)
                Scope-->>Pass: true, returns existingVar
                Pass->>Inst: replace with Mov(dest, existingVar)
            else No Match (New Expression)
                Scope-->>Pass: false
                Pass->>Scope: put(sig, dest)
            end
        else has side-effects / is Mov
            Pass->>Pass: skip instruction
        end
    end

    loop For each Dom-Tree child block
        Pass->>Pass: RECURSE: optimizeBlock(child, localMap)
    end
```
