package ir.tac;

import java.util.ArrayList;
import java.util.List;

/**
 * AddaFP: Compute address from Frame Pointer
 * dest = FP + offset + index
 */
public class AddaFP extends TAC {
    private Variable dest;
    private int fpOffset;
    private Value index;

    public AddaFP(int id, Variable dest, int fpOffset, Value index) {
        super(id);
        this.dest = dest;
        this.fpOffset = fpOffset;
        this.index = index;
    }

    public Variable getDest() {
        return dest;
    }

    public void setDest(Variable dest) {
        this.dest = dest;
    }

    public int getFpOffset() {
        return fpOffset;
    }

    public Value getIndex() {
        return index;
    }

    @Override
    public List<Value> getOperands() {
        List<Value> ops = new ArrayList<>();
        ops.add(new Immediate(fpOffset));
        ops.add(index);
        return ops;
    }

    @Override
    public void setOperands(List<Value> operands) {
        if (operands.size() >= 2) {
            this.fpOffset = (int) ((Immediate) operands.get(0)).getValue();
            this.index = operands.get(1);
        }
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return dest + " = adda FP, " + fpOffset + ", " + index;
    }
}
