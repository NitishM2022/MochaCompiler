package ast;

import java.util.ArrayList;
import java.util.List;

public class DeclarationList extends Node {

    private final List<Node> decls = new ArrayList<>();

    public DeclarationList(int lineNum, int charPos) {
        super(lineNum, charPos);
    }

    public void add(Node d) { decls.add(d); }
    public List<Node> declarations() { return decls; }

    @Override
    public void accept(NodeVisitor visitor) { visitor.visit(this); }
}


