package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class Read extends TAC {
    private Variable dest;
    private boolean isFloat;

    public Read(int id, Variable dest) {
        super(id);
        this.dest = dest;
        this.isFloat = false;
    }
    
    public Read(int id, Variable dest, boolean isFloat) {
        super(id);
        this.dest = dest;
        this.isFloat = isFloat;
    }

    public Variable getDest() {
        return dest;
    }

    public void setDest(Variable dest) {
        this.dest = dest;
    }
    
    public boolean isFloat() {
        return isFloat;
    }
    
    public void setFloat(boolean isFloat) {
        this.isFloat = isFloat;
    }

    @Override
    public List<Value> getOperands() {
        return new ArrayList<>();
    }

    @Override
    public void setOperands(List<Value> operands) {
        // Read has no operands
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return dest + " = " + (isFloat ? "readF" : "read");
    }
}
