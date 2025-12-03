package ir.optimizations;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;
import ast.BoolLiteral;
import ast.IntegerLiteral;

public abstract class BaseOptimization {
    protected Optimizer optimizer;

    public BaseOptimization(Optimizer optimizer) {
        this.optimizer = optimizer;
    }

    public abstract boolean optimize(CFG cfg);

    protected abstract String getName();

    protected void log(String message) {
        optimizer.logTransformation(getName() + ": " + message);
    }

    protected void logInstruction(TAC instruction, String message) {
        optimizer.logTransformation(instruction.getId() + ": " + getName() + ": " + message);
    }

    protected static Integer getIntegerValue(Value value) {
        if (value instanceof Immediate) {
            Object val = ((Immediate) value).getValue();
            if (val instanceof Integer)
                return (Integer) val;
            if (val instanceof Boolean)
                return ((Boolean) val) ? 1 : 0;
        } else if (value instanceof Literal) {
            ast.Expression expr = ((Literal) value).getValue();
            if (expr instanceof IntegerLiteral)
                return ((IntegerLiteral) expr).getValue();
            if (expr instanceof BoolLiteral)
                return ((BoolLiteral) expr).getValue() ? 1 : 0;
        }
        return null;
    }

    protected static boolean isConstant(Value value) {
        return value instanceof Immediate || value instanceof Literal;
    }

    protected static boolean constantEquals(Value v1, Value v2) {
        Integer int1 = getIntegerValue(v1);
        Integer int2 = getIntegerValue(v2);
        if (int1 != null && int2 != null)
            return int1.equals(int2);

        if (v1 instanceof Immediate && v2 instanceof Immediate) {
            return Objects.equals(((Immediate) v1).getValue(), ((Immediate) v2).getValue());
        }
        if (v1 instanceof Literal && v2 instanceof Literal) {
            return v1.toString().equals(v2.toString());
        }
        return Objects.equals(v1, v2);
    }

    protected static void buildDefUseChains(CFG cfg, Map<Variable, TAC> defs, Map<Variable, Set<TAC>> uses) {
        for (BasicBlock block : cfg.getAllBlocks()) {
            if (block == null)
                continue;

            for (Phi phi : block.getPhis()) {
                if (phi.isEliminated())
                    continue;
                defs.put((Variable) phi.getDest(), phi);
                if (phi.getArgs() != null) {
                    for (Value arg : phi.getArgs().values()) {
                        if (arg instanceof Variable) {
                            uses.putIfAbsent((Variable) arg, new HashSet<>());
                            uses.get((Variable) arg).add(phi);
                        }
                    }
                }
            }

            for (TAC instruction : block.getInstructions()) {
                if (instruction.isEliminated())
                    continue;

                Value dest = instruction.getDest();
                if (dest instanceof Variable)
                    defs.put((Variable) dest, instruction);

                List<Value> operands = instruction.getOperands();
                if (operands != null) {
                    for (Value op : operands) {
                        if (op instanceof Variable) {
                            uses.putIfAbsent((Variable) op, new HashSet<>());
                            uses.get((Variable) op).add(instruction);
                        }
                    }
                }
            }
        }
    }

    protected static boolean hasSideEffects(TAC instruction) {
        return instruction instanceof Write ||
                instruction instanceof WriteB ||
                instruction instanceof WriteNL ||
                instruction instanceof Call ||
                instruction instanceof Return ||
                instruction instanceof Store ||
                instruction instanceof StoreGP ||
                instruction instanceof Bra ||
                instruction instanceof Beq ||
                instruction instanceof Bne ||
                instruction instanceof Blt ||
                instruction instanceof Ble ||
                instruction instanceof Bgt ||
                instruction instanceof Bge ||
                instruction instanceof End;
    }

    protected static boolean isPureComputation(TAC instruction) {
        return instruction instanceof Add ||
                instruction instanceof Sub ||
                instruction instanceof Mul ||
                instruction instanceof Div ||
                instruction instanceof Cmp ||
                instruction instanceof Mov;
    }

    protected static boolean isBinaryArithmetic(TAC instruction) {
        return instruction instanceof Add ||
                instruction instanceof Sub ||
                instruction instanceof Mul ||
                instruction instanceof Div ||
                instruction instanceof Mod ||
                instruction instanceof Pow ||
                instruction instanceof And ||
                instruction instanceof Or ||
                instruction instanceof Adda;
    }

    protected static String getExpressionSignature(TAC instruction) {
        StringBuilder sig = new StringBuilder(instruction.getClass().getSimpleName());
        List<Value> operands = instruction.getOperands();
        if (operands != null) {
            for (Value op : operands) {
                if (op instanceof Variable) {
                    Variable var = (Variable) op;
                    // Use symbol identity hash + version to ensure uniqueness across shadowed
                    // variables
                    if (var.isTemp()) {
                        sig.append(":t").append(var.getTempIndex());
                    } else {
                        sig.append(":").append(System.identityHashCode(var.getSymbol()))
                                .append("_").append(var.getVersion());
                    }
                } else {
                    sig.append(":").append(op.toString());
                }
            }
        }
        return sig.toString();
    }
}
