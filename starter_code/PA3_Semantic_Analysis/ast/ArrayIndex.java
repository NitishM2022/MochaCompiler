package ast;

public class ArrayIndex extends Node implements Expression {

    private final Expression base;
    private final Expression index;

    public ArrayIndex(int lineNum, int charPos, Expression base, Expression index) {
        super(lineNum, charPos);
        this.base = base;
        this.index = index;
    }

    public Expression base() {
        return base;
    }

    public Expression index() {
        return index;
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}


