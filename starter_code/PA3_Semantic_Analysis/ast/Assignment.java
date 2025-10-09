package ast;

import mocha.Token;

public class Assignment extends Statement {
    
    private Expression destination;
    private Token operator;
    private Expression source;
    
    public Assignment(int lineNum, int charPos, Expression dest, Token op, Expression src) {
        super(lineNum, charPos);
        this.destination = dest;
        this.operator = op;
        this.source = src;
    }
    
    public Expression getDestination() {
        return destination;
    }
    
    public Token getOperator() {
        return operator;
    }
    
    public Expression getSource() {
        return source;
    }
    
    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "Assignment[" + destination + " " + operator.kind() + " " + source + "]";
    }
}
