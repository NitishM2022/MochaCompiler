package ast;

import java.util.List;
import mocha.Token;
import types.Type;

public class FunctionCallExpression extends Expression {

    private final Token name;
    private final ArgumentList args;

    public FunctionCallExpression(int lineNum, int charPos, Token name, ArgumentList args) {
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


