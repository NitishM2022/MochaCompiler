package ast;

import java.util.ArrayList;
import java.util.List;

public class StatementSequence extends Node implements Statement {
    
    private List<Statement> statements;
    
    public StatementSequence(int lineNum, int charPos) {
        super(lineNum, charPos);
        this.statements = new ArrayList<>();
    }
    
    public StatementSequence(int lineNum, int charPos, List<Statement> statements) {
        super(lineNum, charPos);
        this.statements = statements;
    }
    
    public void addStatement(Statement statement) {
        statements.add(statement);
    }
    
    public List<Statement> getStatements() {
        return statements;
    }
    
    public int size() {
        return statements.size();
    }
    
    public Statement get(int index) {
        return statements.get(index);
    }
    
    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "StatementSequence[" + statements.size() + " statements]";
    }
}
