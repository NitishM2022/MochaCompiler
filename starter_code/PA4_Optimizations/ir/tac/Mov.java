package ir.tac;

import java.util.ArrayList;
import java.util.List;

/**
 * Mov TAC instruction - moves/copies a value
 * Format: dest = mov src
 * This is cleaner than "add dest, src, #0" for SSA pseudo-assignments
 */
public class Mov extends TAC {
    private Variable dest;
    private Value src;

    public Mov(int id, Variable dest, Value src) {
        super(id);
        this.dest = dest;
        this.src = src;
    }

    @Override
    public Value getDest() {
        return dest;
    }

    public void setDest(Variable dest) {
        this.dest = dest;
    }

    public Value getSrc() {
        return src;
    }

    public void setSrc(Value src) {
        this.src = src;
    }

    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        operands.add(src);
        return operands;
    }

    @Override
    public void setOperands(List<Value> operands) {
        if (operands != null && !operands.isEmpty()) {
            this.src = operands.get(0);
        }
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "mov " + dest + " " + src;
    }
}

