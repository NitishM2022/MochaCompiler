package ast;

public class RepeatStatement extends Statement {

    private final StatementSequence body;
    private final Expression condition; // until condition

    public RepeatStatement(int lineNum, int charPos, StatementSequence body, Expression condition) {
        super(lineNum, charPos);
        this.body = body;
        this.condition = condition;
    }

    public StatementSequence body() { return body; }
    public Expression condition() { return condition; }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
}


