package ir.tac;

import java.util.ArrayList;
import java.util.List;

public abstract class Assign extends TAC{
    
    private Variable dest; // lhs
    private Value left; // operand_1 
    private Value right; // operand_2

    protected Assign(int id, Variable dest, Value left, Value right) {
        super(id);
        this.dest = dest;
        this.left = left;
        this.right = right;
    }
    
    @Override
    public Value getDest() {
        return dest;
    }
    
    public Value getLeft() {
        return left;
    }
    
    public Value getRight() {
        return right;
    }
    
    public void setDest(Variable dest) {
        this.dest = dest;
    }
    
    public void setLeft(Value left) {
        this.left = left;
    }
    
    public void setRight(Value right) {
        this.right = right;
    }
    
    @Override
    public List<Value> getOperands() {
        List<Value> operands = new ArrayList<>();
        operands.add(left);
        operands.add(right);
        return operands;
    }
    
    @Override
    public void setOperands(List<Value> operands) {
        if (operands.size() >= 2) {
            this.left = operands.get(0);
            this.right = operands.get(1);
        }
    }
}
