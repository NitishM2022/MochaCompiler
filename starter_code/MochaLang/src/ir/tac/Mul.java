package ir.tac;

public class Mul extends Assign {

    private boolean isFloat;

    public Mul(int id, Variable dest, Value left, Value right) {
        this(id, dest, left, right, false);
    }

    public Mul(int id, Variable dest, Value left, Value right, boolean isFloat) {
        super(id, dest, left, right);
        this.isFloat = isFloat;
    }

    public boolean isFloat() {
        return isFloat;
    }

    public void setFloat(boolean isFloat) {
        this.isFloat = isFloat;
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return getDest() + " = " + getLeft() + " * " + getRight();
    }
}
