package ast;

import mocha.Token;

public class Division extends Node implements Expression {
    private Expression left;
    private Token operator;
    private Expression right;
    
    public Division(int lineNum, int charPos, Expression left, Token op, Expression right) {
        super(lineNum, charPos);
        this.left = left;
        this.operator = op;
        this.right = right;
    }
    
    public Expression getLeft() { return left; }
    public Token getOperator() { return operator; }
    public Expression getRight() { return right; }
    
    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
    
    @Override
    public String toString() { return "Division[" + left + " / " + right + "]"; }
}
