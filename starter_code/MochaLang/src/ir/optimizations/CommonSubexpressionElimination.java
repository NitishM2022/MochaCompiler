package ir.optimizations;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.ssa.DominatorAnalysis;
import ir.tac.*;

public class CommonSubexpressionElimination extends BaseOptimization {
    private DominatorAnalysis domAnalysis;
    private boolean changed;

    public CommonSubexpressionElimination(Optimizer optimizer) {
        super(optimizer);
    }

    @Override
    protected String getName() {
        return "CSE";
    }

    @Override
    public boolean optimize(CFG cfg) {
        this.domAnalysis = cfg.getDominatorAnalysis();
        if (this.domAnalysis == null) {
            System.err.println("CSE requires dominator analysis. Run SSA conversion first.");
            return false;
        }

        this.changed = false;
        eliminateRecursive(cfg.getEntryBlock(), new HashMap<>());
        return this.changed;
    }

    private void eliminateRecursive(BasicBlock block, Map<String, Variable> available) {
        Map<String, Variable> local = new HashMap<>(available);

        List<TAC> instructions = block.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            TAC instruction = instructions.get(i);

            // Skip eliminated instructions or instructions that don't have a variable
            // destination
            if (instruction.isEliminated() || !(instruction.getDest() instanceof Variable)) {
                continue;
            }

            // Skip Mov instructions - they should be handled by Copy Propagation, not CSE
            // Including Mov in CSE causes infinite loops with Constant Propagation
            if (isPureComputation(instruction) && !(instruction instanceof Mov)) {
                String signature = getExpressionSignature(instruction);
                Variable dest = (Variable) instruction.getDest();

                if (local.containsKey(signature)) {
                    Variable existing = local.get(signature);
                    Mov replacement = new Mov(instruction.getId(), dest, existing);
                    instructions.set(i, replacement);
                    logInstruction(instruction,
                            "Eliminated: " + instruction.toString() + " -> " + replacement.toString());
                    this.changed = true;
                } else {
                    local.put(signature, dest);
                }
            }
        }

        for (BasicBlock child : domAnalysis.getDomTreeChildren(block)) {
            eliminateRecursive(child, local);
        }
    }
}
