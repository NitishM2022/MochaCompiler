package ir.tac;

import java.util.ArrayList;
import java.util.List;

/**
 * AddaGP: Compute address from Global Pointer
 * dest = GP + offset + index
 */
public class AddaGP extends TAC {
    private Variable dest;
    private int gpOffset;
    private Value index;

    public AddaGP(int id, Variable dest, int gpOffset, Value index) {
        super(id);
        this.dest = dest;
        this.gpOffset = gpOffset;
        this.index = index;
    }

    public Variable getDest() {
        return dest;
    }

    public void setDest(Variable dest) {
        this.dest = dest;
    }

    public int getGpOffset() {
        return gpOffset;
    }

    public Value getIndex() {
        return index;
    }

    @Override
    public List<Value> getOperands() {
        List<Value> ops = new ArrayList<>();
        ops.add(new Immediate(gpOffset));
        ops.add(index);
        return ops;
    }

    @Override
    public void setOperands(List<Value> operands) {
        if (operands.size() >= 2) {
            this.gpOffset = (int) ((Immediate) operands.get(0)).getValue();
            this.index = operands.get(1);
        }
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return dest + " = adda GP, " + gpOffset + ", " + index;
    }
}
