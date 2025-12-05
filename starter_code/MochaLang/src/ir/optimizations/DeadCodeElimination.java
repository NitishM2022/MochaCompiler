package ir.optimizations;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;

public class DeadCodeElimination extends BaseOptimization {
    public DeadCodeElimination(Optimizer optimizer) {
        super(optimizer);
    }

    @Override
    protected String getName() {
        return "DCE";
    }

    @Override
    public boolean optimize(CFG cfg) {
        boolean changed = false;

        Map<Variable, TAC> defs = new HashMap<>();
        Map<Variable, Set<TAC>> uses = new HashMap<>();
        buildDefUseChains(cfg, defs, uses);

        Queue<TAC> worklist = new LinkedList<>();
        for (Variable var : defs.keySet()) {
            if (!uses.containsKey(var) || uses.get(var).isEmpty()) {
                TAC def = defs.get(var);
                if (canEliminate(def)) {
                    worklist.add(def);
                }
            }
        }

        while (!worklist.isEmpty()) {
            TAC deadInst = worklist.poll();

            if (deadInst.isEliminated() || !canEliminate(deadInst)) {
                continue;
            }

            deadInst.setEliminated(true);
            changed = true;
            logInstruction(deadInst, "Eliminated: " + deadInst.toString());

            List<Value> operands = getOperands(deadInst);
            for (Value operand : operands) {
                if (!(operand instanceof Variable))
                    continue;

                Variable var = (Variable) operand;
                TAC def = defs.get(var);
                if (def == null)
                    continue;

                Set<TAC> users = uses.get(var);
                if (users != null) {
                    users.remove(deadInst);

                    if (users.isEmpty() && canEliminate(def) && !worklist.contains(def)) {
                        worklist.add(def);
                    }
                }
            }
        }

        return changed;
    }

    private boolean canEliminate(TAC instruction) {
        return !hasSideEffects(instruction);
    }


    private List<Value> getOperands(TAC instruction) {
        if (instruction instanceof Phi) {
            Phi phi = (Phi) instruction;
            return phi.getArgs() != null ? new ArrayList<>(phi.getArgs().values()) : Collections.emptyList();
        }
        return instruction.getOperands() != null ? instruction.getOperands() : Collections.emptyList();
    }
}
