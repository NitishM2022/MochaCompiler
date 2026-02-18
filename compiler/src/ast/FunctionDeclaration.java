package ast;

import java.util.List;

import types.*;

import mocha.Symbol;
import mocha.Token;

public class FunctionDeclaration extends Node {

    private final Token name;
    private final List<Symbol> formals;
    private final Type returnType;
    private final FunctionBody body;

    public FunctionDeclaration(int lineNum, int charPos, Token name, List<Symbol> formals, Type returnType, FunctionBody body) {
        super(lineNum, charPos);
        this.name = name;
        this.formals = formals;
        this.returnType = returnType;
        this.body = body;
    }

    public Token name() { return name; }
    public List<Symbol> formals() { return formals; }
    public Type returnType() { return returnType; }
    public FunctionBody body() { return body; }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
}


