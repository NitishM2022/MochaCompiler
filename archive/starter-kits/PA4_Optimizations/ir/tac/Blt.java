package ir.tac;

import java.util.ArrayList;
import java.util.List;
import ir.cfg.BasicBlock;

public class Blt extends TAC {
    private Value condition;
    private BasicBlock target;
    
    public Blt(int id, Value condition, BasicBlock target) {
        super(id);
        this.condition = condition;
        this.target = target;
    }
    
    public Value getCondition() {
        return condition;
    }
    
    public void setCondition(Value condition) {
        this.condition = condition;
    }
    
    public BasicBlock getTarget() {
        return target;
    }
    
    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        operands.add(condition);
        return operands;
    }
    
    @Override
    public void setOperands(List<Value> operands) {
        if (!operands.isEmpty()) {
            this.condition = operands.get(0);
        }
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "blt " + condition + " BB" + target.getNum();
    }
}

