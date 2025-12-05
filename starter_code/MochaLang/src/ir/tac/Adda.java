package ir.tac;

public class Adda extends Assign {

    public Adda(int id, Variable dest, Value left, Value right) {
        super(id, dest, left, right);
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return getDest() + " = &(" + getLeft() + " + " + getRight() + ")";
    }
}
