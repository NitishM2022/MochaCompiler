package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class Store extends TAC {
    private Value src;
    private Value addr;
    
    public Store(int id, Value src, Value addr) {
        super(id);
        this.src = src;
        this.addr = addr;
    }
    
    public Value getSrc() {
        return src;
    }
    
    public void setSrc(Value src) {
        this.src = src;
    }
    
    public Value getAddr() {
        return addr;
    }
    
    public void setAddr(Value addr) {
        this.addr = addr;
    }
    
    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        operands.add(src);
        operands.add(addr);
        return operands;
    }
    
    @Override
    public void setOperands(List<Value> operands) {
        if (operands.size() >= 2) {
            this.src = operands.get(0);
            this.addr = operands.get(1);
        }
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "store " + addr + " " + src;
    }
}

