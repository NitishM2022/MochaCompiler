package ast;

import java.util.List;

public class FunctionBody extends Node {

    private final List<VariableDeclaration> locals;
    private final StatementSequence statements;

    public FunctionBody(int lineNum, int charPos, List<VariableDeclaration> locals, StatementSequence statements) {
        super(lineNum, charPos);
        this.locals = locals;
        this.statements = statements;
    }

    public List<VariableDeclaration> locals() { return locals; }
    public StatementSequence statements() { return statements; }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
}


