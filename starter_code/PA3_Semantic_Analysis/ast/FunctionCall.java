package ast;

import java.util.List;
import mocha.Token;

public class FunctionCall extends Node implements Expression, Statement {

    private final Token name;
    private final ArgumentList args;

    public FunctionCall(int lineNum, int charPos, Token name, ArgumentList args) {
        super(lineNum, charPos);
        this.name = name;
        this.args = args;
    }

    public Token name() {
        return name;
    }

    public ArgumentList arguments() {
        return args;
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}


