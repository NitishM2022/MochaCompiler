package ast;

import mocha.Token;
import types.Type;

public class Designator extends Expression {

    private final Token name;

    public Designator(int lineNum, int charPos, Token name) {
        super(lineNum, charPos);
        this.name = name;
    }

    public Designator(Token name) {
        this(name.lineNumber(), name.charPosition(), name);
    }

    public Token name() { return name; }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}


