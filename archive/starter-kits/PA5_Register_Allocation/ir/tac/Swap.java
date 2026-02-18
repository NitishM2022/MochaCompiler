package ir.tac;

import java.util.ArrayList;
import java.util.List;

/**
 * Swap instruction: Swap dest, src
 * Represents swapping the values of two registers/variables.
 * Used for resolving parallel copy cycles during SSA elimination.
 */
public class Swap extends TAC {
    private Variable dest;
    private Variable src;

    public Swap(int id, Variable dest, Variable src) {
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

    public Variable getSrc() {
        return src;
    }

    public void setSrc(Variable src) {
        this.src = src;
    }

    @Override
    public List<Value> getOperands() {
        List<Value> ops = new ArrayList<>();
        ops.add(src);
        return ops;
    }

    @Override
    public void setOperands(List<Value> operands) {
        if (operands.size() > 0 && operands.get(0) instanceof Variable) {
            this.src = (Variable) operands.get(0);
        }
    }

    @Override
    public String toString() {
        return "swap " + dest + ", " + src;
    }

    @Override
    public void accept(TACVisitor visitor) {
        // visitor.visit(this); // Need to update visitor interface if we want to
        // support this fully
    }
}
