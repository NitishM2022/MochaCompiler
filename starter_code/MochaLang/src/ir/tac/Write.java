package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class Write extends TAC {
    private Value src;
    private boolean isFloat;
    
    public Write(int id, Value src) {
        super(id);
        this.src = src;
        this.isFloat = false;
    }
    
    public Write(int id, Value src, boolean isFloat) {
        super(id);
        this.src = src;
        this.isFloat = isFloat;
    }
    
    public Value getSrc() {
        return src;
    }
    
    public void setSrc(Value src) {
        this.src = src;
    }
    
    public boolean isFloat() {
        return isFloat;
    }
    
    public void setFloat(boolean isFloat) {
        this.isFloat = isFloat;
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
        return (isFloat ? "writeF " : "write ") + src;
    }
}

