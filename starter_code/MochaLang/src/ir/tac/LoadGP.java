package ir.tac;

import java.util.ArrayList;
import java.util.List;

/**
 * LoadGP: Load from global pointer-relative address
 * dest = [GP + offset]
 */
public class LoadGP extends TAC {
    private Variable dest;
    private int gpOffset;

    public LoadGP(int id, Variable dest, int gpOffset) {
        super(id);
        this.dest = dest;
        this.gpOffset = gpOffset;
    }

    @Override
    public Variable getDest() {
        return dest;
    }

    public void setDest(Variable dest) {
        this.dest = dest;
    }

    public int getGpOffset() {
        return gpOffset;
    }

    @Override
    public List<Value> getOperands() {
        return new ArrayList<>();
    }

    @Override
    public void setOperands(List<Value> operands) {
        // No operands to set
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        String sign = gpOffset >= 0 ? "+" : "";
        return dest + " = [GP" + sign + gpOffset + "]";
    }
}
