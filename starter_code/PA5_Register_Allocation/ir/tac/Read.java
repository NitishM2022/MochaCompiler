package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class Read extends TAC {
    private Variable dest;
    
    public Read(int id, Variable dest) {
        super(id);
        this.dest = dest;
    }
    
    public Variable getDest() {
        return dest;
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
        return dest + " = read";
    }
}

