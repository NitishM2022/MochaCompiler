package ast;

public class WhileStatement extends Statement {

    private final Expression condition;
    private final StatementSequence body;

    public WhileStatement(int lineNum, int charPos, Expression condition, StatementSequence body) {
        super(lineNum, charPos);
        this.condition = condition;
        this.body = body;
    }

    public Expression condition() { return condition; }
    public StatementSequence body() { return body; }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
}


