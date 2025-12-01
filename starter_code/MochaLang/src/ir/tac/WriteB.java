package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class WriteB extends TAC {
    private Value src;
    
    public WriteB(int id, Value src) {
        super(id);
        this.src = src;
    }
    
    public Value getSrc() {
        return src;
    }
    
    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        operands.add(src);
        return operands;
    }
    
    @Override
    public void setOperands(List<Value> operands) {
        if (!operands.isEmpty()) {
            this.src = operands.get(0);
        }
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "writeB " + src;
    }
}

