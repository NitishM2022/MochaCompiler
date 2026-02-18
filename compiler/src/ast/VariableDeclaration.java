package ast;

import java.util.List;

import mocha.Token;

import types.Type;

public class VariableDeclaration extends Node {

    private final Type type;
    private final List<Token> names;

    public VariableDeclaration(int lineNum, int charPos, Type type, List<Token> names) {
        super(lineNum, charPos);
        this.type = type;
        this.names = names;
    }

    public Type type() { return type; }
    public List<Token> names() { return names; }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
}


