package ast;

public class LogicalNot extends Expression {

    private final Expression operand;

    public LogicalNot(int lineNum, int charPos, Expression operand) {
        super(lineNum, charPos);
        this.operand = operand;
    }

    public Expression operand() {
        return operand;
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}


