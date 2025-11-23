package ir.tac;

public class Not extends Assign {
    
    public Not(int id, Variable dest, Value operand) {
        super(id, dest, operand, null);
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return getDest() + " = !" + getLeft();
    }
}


