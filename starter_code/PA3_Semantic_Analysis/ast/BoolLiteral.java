package ast;

import mocha.Token;

public class BoolLiteral extends Expression {
    
    private boolean value;
    private Token token;
    
    public BoolLiteral(int lineNum, int charPos, boolean value) {
        super(lineNum, charPos);
        this.value = value;
    }
    
    public BoolLiteral(Token token) {
        super(token.lineNumber(), token.charPosition());
        this.token = token;
        this.value = token.kind() == Token.Kind.TRUE;
    }
    
    public boolean getValue() {
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
        return "BoolLiteral[" + value + "]";
    }
}
