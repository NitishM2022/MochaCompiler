package ir.tac;

public class Or extends Assign {
    
    public Or(int id, Variable dest, Value left, Value right) {
        super(id, dest, left, right);
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "or " + getDest() + " " + getLeft() + " " + getRight();
    }
}


