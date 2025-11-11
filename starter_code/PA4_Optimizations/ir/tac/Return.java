package ir.tac;

import java.util.ArrayList;
import java.util.List;

/**
 * Return TAC instruction - returns from a function
 * Format: return value (or just "return" for void)
 */
public class Return extends TAC {
    private Value returnValue;

    public Return(int id, Value returnValue) {
        super(id);
        this.returnValue = returnValue;
    }

    @Override
    public Value getDest() {
        return null;
    }


    public Value getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Value returnValue) {
        this.returnValue = returnValue;
    }

    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        if (returnValue != null) {
            operands.add(returnValue);
        }
        return operands;
    }

    @Override
    public void setOperands(List<Value> operands) {
        if (operands != null && !operands.isEmpty()) {
            this.returnValue = operands.get(0);
        } else {
            this.returnValue = null;
        }
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("return");
        if (returnValue != null) {
            sb.append(" ").append(returnValue);
        }
        return sb.toString();
    }
}

