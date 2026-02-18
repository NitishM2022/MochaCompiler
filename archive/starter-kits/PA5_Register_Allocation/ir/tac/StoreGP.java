package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class StoreGP extends TAC {
    private Value src;
    private int gpOffset;

    public StoreGP(int id, Value src, int gpOffset) {
        super(id);
        this.src = src;
        this.gpOffset = gpOffset;
    }

    public Value getSrc() {
        return src;
    }

    public void setSrc(Value src) {
        this.src = src;
    }

    public int getGpOffset() {
        return gpOffset;
    }

    public void setGpOffset(int gpOffset) {
        this.gpOffset = gpOffset;
    }

    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        operands.add(src);
        operands.add(new Immediate(gpOffset));
        return operands;
    }

    @Override
    public void setOperands(List<Value> operands) {
        if (operands.size() >= 2) {
            this.src = operands.get(0);
            this.gpOffset = (int) ((Immediate) operands.get(1)).getValue();
        }
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "[GP" + (gpOffset >= 0 ? "+" : "") + gpOffset + "] = " + src;
    }
}
