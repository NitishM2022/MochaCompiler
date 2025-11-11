package ir.tac;

public class Mod extends Assign {
    
    public Mod(int id, Variable dest, Value left, Value right) {
        super(id, dest, left, right);
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "mod " + getDest() + " " + getLeft() + " " + getRight();
    }
}


