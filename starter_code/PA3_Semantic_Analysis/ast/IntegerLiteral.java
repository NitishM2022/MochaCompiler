package ast;

import mocha.Token;

public class IntegerLiteral extends Node implements Expression {
    
    private int value;
    private Token token;
    
    public IntegerLiteral(int lineNum, int charPos, int value) {
        super(lineNum, charPos);
        this.value = value;
    }
    
    public IntegerLiteral(Token token) {
        super(token.lineNumber(), token.charPosition());
        this.token = token;
        this.value = Integer.parseInt(token.lexeme());
    }
    
    public int getValue() {
        return value;
    }
    
    public Token getToken() {
        return token;
    }
    
    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "IntegerLiteral[" + value + "]";
    }
}
