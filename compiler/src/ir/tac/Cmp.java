package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class Cmp extends TAC {
    private Variable dest;
    private Value left;
    private Value right;
    private String op; // "eq", "ne", "lt", "le", "gt", "ge"

    private boolean isFloat;

    public Cmp(int id, Variable dest, Value left, Value right, String op) {
        this(id, dest, left, right, op, false);
    }

    public Cmp(int id, Variable dest, Value left, Value right, String op, boolean isFloat) {
        super(id);
        this.dest = dest;
        this.left = left;
        this.right = right;
        this.op = op;
        this.isFloat = isFloat;
    }

    public boolean isFloat() {
        return isFloat;
    }

    public void setFloat(boolean isFloat) {
        this.isFloat = isFloat;
    }

    public Variable getDest() {
        return dest;
    }

    public void setDest(Variable dest) {
        this.dest = dest;
    }

    // Removed @Override getDest() to avoid duplication

    public Value getLeft() {
        return left;
    }

    public void setLeft(Value left) {
        this.left = left;
    }

    public Value getRight() {
        return right;
    }

    public void setRight(Value right) {
        this.right = right;
    }

    public String getOp() {
        return op;
    }

    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        operands.add(left);
        operands.add(right);
        return operands;
    }

    @Override
    public void setOperands(List<Value> operands) {
        if (operands.size() >= 2) {
            this.left = operands.get(0);
            this.right = operands.get(1);
        }
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        String opSymbol = mapOpToSymbol(op);
        return getDest() + " = " + getLeft() + " " + opSymbol + " " + getRight();
    }

    private String mapOpToSymbol(String op) {
        return switch (op) {
            case "eq" -> "==";
            case "ne" -> "!=";
            case "lt" -> "<";
            case "le" -> "<=";
            case "gt" -> ">";
            case "ge" -> ">=";
            default -> op;
        };
    }
}
