package ir.optimizations;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;

public class CopyPropagation extends BaseOptimization {

    private enum LatticeType {
        TOP, COPY, BOTTOM
    }

    private static class LatticeValue {
        LatticeType type;
        Variable value;

        static LatticeValue TOP() {
            LatticeValue lv = new LatticeValue();
            lv.type = LatticeType.TOP;
            return lv;
        }

        static LatticeValue COPY(Variable var) {
            LatticeValue lv = new LatticeValue();
            lv.type = LatticeType.COPY;
            lv.value = var;
            return lv;
        }

        static LatticeValue BOTTOM() {
            LatticeValue lv = new LatticeValue();
            lv.type = LatticeType.BOTTOM;
            return lv;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof LatticeValue))
                return false;
            LatticeValue other = (LatticeValue) obj;
            if (type != other.type)
                return false;
            if (type == LatticeType.COPY) {
                return Objects.equals(value, other.value);
            }
            return true;
        }
    }

    public CopyPropagation(Optimizer optimizer) {
        super(optimizer);
    }

    @Override
    protected String getName() {
        return "CPP";
    }

    @Override
    public boolean optimize(CFG cfg) {
        Map<Variable, Object> defSite = new HashMap<>();
        Map<Variable, List<Object>> uses = new HashMap<>();
        Map<Variable, LatticeValue> lattice = new HashMap<>();
        buildDefUseChains(cfg, defSite, uses, lattice);
        runWorklist(defSite, uses, lattice);
        return applyPropagation(cfg, lattice);
    }

    private void buildDefUseChains(CFG cfg, Map<Variable, Object> defSite,
            Map<Variable, List<Object>> uses,
            Map<Variable, LatticeValue> lattice) {
        for (BasicBlock block : cfg.getAllBlocks()) {
            if (block == null)
                continue;

            for (Phi phi : block.getPhis()) {
                if (phi.isEliminated())
                    continue;

                Variable dest = (Variable) phi.getDest();
                defSite.put(dest, phi);
                lattice.put(dest, LatticeValue.TOP());

                if (phi.getArgs() != null) {
                    for (Value arg : phi.getArgs().values()) {
                        if (arg instanceof Variable) {
                            Variable argVar = (Variable) arg;
                            uses.putIfAbsent(argVar, new ArrayList<>());
                            uses.get(argVar).add(phi);
                        }
                    }
                }
            }

            for (TAC instruction : block.getInstructions()) {
                if (instruction.isEliminated())
                    continue;

                Value dest = instruction.getDest();
                if (dest instanceof Variable) {
                    Variable destVar = (Variable) dest;
                    defSite.put(destVar, instruction);
                    lattice.put(destVar, LatticeValue.TOP());
                }

                List<Value> operands = instruction.getOperands();
                if (operands != null) {
                    for (Value op : operands) {
                        if (op instanceof Variable) {
                            Variable opVar = (Variable) op;
                            uses.putIfAbsent(opVar, new ArrayList<>());
                            uses.get(opVar).add(instruction);
                            lattice.putIfAbsent(opVar, LatticeValue.BOTTOM());
                        }
                    }
                }
            }
        }
    }

    private void runWorklist(Map<Variable, Object> defSite,
            Map<Variable, List<Object>> uses,
            Map<Variable, LatticeValue> lattice) {
        Queue<Variable> worklist = new LinkedList<>(defSite.keySet());
        Set<Variable> inWorklist = new HashSet<>(worklist);

        while (!worklist.isEmpty()) {
            Variable var = worklist.poll();
            inWorklist.remove(var);

            LatticeValue oldValue = lattice.get(var);
            LatticeValue newValue = evaluate(defSite.get(var), lattice);

            if (!newValue.equals(oldValue)) {
                lattice.put(var, newValue);

                List<Object> userList = uses.get(var);
                if (userList != null) {
                    for (Object use : userList) {
                        Variable useDef = getDefinedVariable(use);
                        if (useDef != null && !inWorklist.contains(useDef)) {
                            worklist.add(useDef);
                            inWorklist.add(useDef);
                        }
                    }
                }
            }
        }
    }

    private LatticeValue evaluate(Object def, Map<Variable, LatticeValue> lattice) {
        if (def instanceof Phi)
            return evaluatePhi((Phi) def, lattice);
        if (def instanceof Mov)
            return evaluateMov((Mov) def, lattice);
        return LatticeValue.BOTTOM();
    }

    private LatticeValue evaluatePhi(Phi phi, Map<Variable, LatticeValue> lattice) {
        Map<BasicBlock, Value> args = phi.getArgs();
        if (args == null || args.isEmpty())
            return LatticeValue.BOTTOM();

        LatticeValue result = null;
        for (Value arg : args.values()) {
            LatticeValue argValue = getLatticeValue(arg, lattice);

            // TOP is the identity element for meet: LOW ^ TOP = LOW
            if (argValue.type == LatticeType.TOP)
                continue;

            // BOTTOM is the absorbing element: LOW ^ BOTTOM = BOTTOM
            if (argValue.type == LatticeType.BOTTOM)
                return LatticeValue.BOTTOM();

            if (result == null) {
                result = argValue;
            } else {
                if (result.type != argValue.type)
                    return LatticeValue.BOTTOM();
                if (result.type == LatticeType.COPY) {
                    if (!result.value.equals(argValue.value))
                        return LatticeValue.BOTTOM();
                }
            }
        }

        // If result is still null, it means all args were TOP
        return result != null ? result : LatticeValue.TOP();
    }

    private LatticeValue evaluateMov(Mov mov, Map<Variable, LatticeValue> lattice) {
        Value src = mov.getSrc();

        // Ignore constant assignments (not a copy)
        if (src instanceof Literal || src instanceof Immediate) {
            return LatticeValue.BOTTOM();
        }

        // Handle copy assignment: x = y
        if (src instanceof Variable) {
            Variable srcVar = (Variable) src;
            LatticeValue srcValue = lattice.getOrDefault(srcVar, LatticeValue.BOTTOM());

            if (srcValue.type == LatticeType.COPY) {
                // Follow copy chain: x = y, where y = z, so x = z
                return LatticeValue.COPY(srcValue.value);
            } else if (srcValue.type == LatticeType.BOTTOM) {
                // Direct copy: x = y
                return LatticeValue.COPY(srcVar);
            }
        }

        return LatticeValue.BOTTOM();
    }

    private LatticeValue getLatticeValue(Value value, Map<Variable, LatticeValue> lattice) {
        if (value instanceof Variable) {
            return lattice.getOrDefault((Variable) value, LatticeValue.BOTTOM());
        }
        // Constants are not copies
        return LatticeValue.BOTTOM();
    }

    private boolean applyPropagation(CFG cfg, Map<Variable, LatticeValue> lattice) {
        boolean changed = false;

        for (BasicBlock block : cfg.getAllBlocks()) {
            if (block == null)
                continue;

            for (Phi phi : block.getPhis()) {
                if (phi.isEliminated())
                    continue;

                Map<BasicBlock, Value> args = phi.getArgs();
                if (args != null) {
                    Map<BasicBlock, Value> newArgs = new HashMap<>();
                    boolean phiChanged = false;

                    for (Map.Entry<BasicBlock, Value> entry : args.entrySet()) {
                        Value replacement = getReplacement(entry.getValue(), lattice);
                        newArgs.put(entry.getKey(), replacement);
                        if (replacement != entry.getValue())
                            phiChanged = true;
                    }

                    if (phiChanged) {
                        phi.setArgs(newArgs);
                        changed = true;
                        logInstruction(phi, "Copy propagated in phi: " + phi.toString());
                    }
                }
            }

            for (TAC instruction : block.getInstructions()) {
                if (instruction.isEliminated())
                    continue;

                List<Value> operands = instruction.getOperands();
                if (operands != null && !operands.isEmpty()) {
                    List<Value> newOperands = new ArrayList<>();
                    boolean instChanged = false;

                    for (Value op : operands) {
                        Value replacement = getReplacement(op, lattice);
                        newOperands.add(replacement);
                        if (replacement != op)
                            instChanged = true;
                    }

                    if (instChanged) {
                        instruction.setOperands(newOperands);
                        changed = true;
                        logInstruction(instruction, "Copy propagated in: " + instruction.toString());
                    }
                }
            }
        }

        return changed;
    }

    private Value getReplacement(Value value, Map<Variable, LatticeValue> lattice) {
        return getReplacement(value, lattice, new HashSet<>());
    }

    private Value getReplacement(Value value, Map<Variable, LatticeValue> lattice, Set<Variable> visited) {
        if (!(value instanceof Variable))
            return value;

        Variable var = (Variable) value;

        // Cycle detection: if we've seen this variable before, return it to avoid
        // infinite recursion
        if (visited.contains(var)) {
            return var;
        }

        LatticeValue lv = lattice.get(var);

        if (lv != null && lv.type == LatticeType.COPY) {
            // Follow copy chain recursively with cycle detection
            visited.add(var);
            Value result = getReplacement(lv.value, lattice, visited);
            visited.remove(var);
            return result;
        }

        return value;
    }

    private Variable getDefinedVariable(Object defOrUse) {
        if (defOrUse instanceof Phi) {
            Value dest = ((Phi) defOrUse).getDest();
            return dest instanceof Variable ? (Variable) dest : null;
        } else if (defOrUse instanceof TAC) {
            Value dest = ((TAC) defOrUse).getDest();
            return dest instanceof Variable ? (Variable) dest : null;
        }
        return null;
    }
}
