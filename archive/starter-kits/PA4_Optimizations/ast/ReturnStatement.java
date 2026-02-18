package ast;

public class ReturnStatement extends Statement {

    private final Expression value; // may be null

    public ReturnStatement(int lineNum, int charPos, Expression value) {
        super(lineNum, charPos);
        this.value = value;
    }

    public Expression value() { return value; }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
}


