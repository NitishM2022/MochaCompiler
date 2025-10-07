package ast;

public class IfStatement extends Node implements Statement {

    private final Expression condition;
    private final StatementSequence thenBlock;
    private final StatementSequence elseBlock; // may be null

    public IfStatement(int lineNum, int charPos, Expression condition,
                       StatementSequence thenBlock, StatementSequence elseBlock) {
        super(lineNum, charPos);
        this.condition = condition;
        this.thenBlock = thenBlock;
        this.elseBlock = elseBlock;
    }

    public Expression condition() { return condition; }
    public StatementSequence thenBlock() { return thenBlock; }
    public StatementSequence elseBlock() { return elseBlock; }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
}


