package ast;

import java.util.ArrayList;
import java.util.List;

public class ArgumentList extends Node {

    private final List<Expression> list = new ArrayList<>();

    public ArgumentList(int lineNum, int charPos) {
        super(lineNum, charPos);
    }

    public void add(Expression e) {
        list.add(e);
    }

    public List<Expression> args() {
        return list;
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}


