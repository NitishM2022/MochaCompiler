package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class Neg extends TAC {
    private Variable dest;
    private Value operand;
    
    public Neg(int id, Variable dest, Value operand) {
        super(id);
        this.dest = dest;
        this.operand = operand;
    }
    
    @Override
    public Value getDest() {
        return dest;
    }
    
    public Value getOperand() {
        return operand;
    }
    
    public void setOperand(Value operand) {
        this.operand = operand;
    }
    
    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        operands.add(operand);
        return operands;
    }
    
    @Override
    public void setOperands(List<Value> operands) {
        if (!operands.isEmpty()) {
            this.operand = operands.get(0);
        }
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "neg " + operand;
    }
}

