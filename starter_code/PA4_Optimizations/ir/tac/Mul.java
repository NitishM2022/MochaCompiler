package ir.tac;

public class Mul extends Assign {
    
    public Mul(int id, Variable dest, Value left, Value right) {
        super(id, dest, left, right);
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "mul " + getDest() + " " + getLeft() + " " + getRight();
    }
}

