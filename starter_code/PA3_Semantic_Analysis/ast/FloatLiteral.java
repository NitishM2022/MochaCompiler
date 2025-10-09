package ast;

import mocha.Token;

public class FloatLiteral extends Expression {
    
    private float value;
    private Token token;
    
    public FloatLiteral(int lineNum, int charPos, float value) {
        super(lineNum, charPos);
        this.value = value;
    }
    
    public FloatLiteral(Token token) {
        super(token.lineNumber(), token.charPosition());
        this.token = token;
        this.value = Float.parseFloat(token.lexeme());
    }
    
    public float getValue() {
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
        return "FloatLiteral[" + value + "]";
    }
}
