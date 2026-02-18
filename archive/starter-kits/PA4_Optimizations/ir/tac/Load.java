package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class Load extends TAC {
    private Variable dest;
    private Value addr;
    
    public Load(int id, Variable dest, Value addr) {
        super(id);
        this.dest = dest;
        this.addr = addr;
    }
    
    @Override
    public Value getDest() {
        return dest;
    }
    
    public void setDest(Variable dest) {
        this.dest = dest;
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
        operands.add(addr);
        return operands;
    }
    
    @Override
    public void setOperands(List<Value> operands) {
        if (!operands.isEmpty()) {
            this.addr = operands.get(0);
        }
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return dest + " = load " + addr;
    }
}

