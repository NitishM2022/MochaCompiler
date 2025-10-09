package ast;

public class PrettyPrinter implements NodeVisitor {

    private int depth = 0;
    private StringBuilder sb = new StringBuilder();

    private void println (Node n, String message) {
        String indent = "";
        for (int i = 0; i < depth; i++) {
            indent += "  ";
        }
        sb.append(indent + n.getClassInfo() + message + "\n");
    }

    @Override
    public String toString () {
        return sb.toString();
    }

    // Literals
    @Override
    public void visit(BoolLiteral node) {
        println(node, "[" + node.getValue() + "]");
    }

    @Override
    public void visit(IntegerLiteral node) {
        println(node, "[" + node.getValue() + "]");
    }

    @Override
    public void visit(FloatLiteral node) {
        println(node, "[" + node.getValue() + "]");
    }

    // Designators
    @Override
    public void visit(Designator node) {
        println(node, "[" + node.name() + "]");
    }

    @Override
    public void visit(ArrayIndex node) {
        println(node, "");
        depth++;
        node.base().accept(this);
        node.index().accept(this);
        depth--;
    }

    @Override
    public void visit(Dereference node) {
        println(node, "");
        depth++;
        node.operand().accept(this);
        depth--;
    }

    // Expressions
    @Override
    public void visit(LogicalNot node) {
        println(node, "");
        depth++;
        node.operand().accept(this);
        depth--;
    }

    @Override
    public void visit(Power node) {
        println(node, "");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    @Override
    public void visit(Multiplication node) {
        println(node, "");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    @Override
    public void visit(Division node) {
        println(node, "");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    @Override
    public void visit(Modulo node) {
        println(node, "");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    @Override
    public void visit(LogicalAnd node) {
        println(node, "");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    @Override
    public void visit(Addition node) {
        println(node, "");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    @Override
    public void visit(Subtraction node) {
        println(node, "");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    @Override
    public void visit(LogicalOr node) {
        println(node, "");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    @Override
    public void visit(Relation node) {
        println(node, "[" + node.getOperator().kind() + "]");
        depth++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        depth--;
    }

    // Statements
    @Override
    public void visit(Assignment node) {
        println(node, "");
        depth++;
        node.getDestination().accept(this);
        node.getSource().accept(this);
        depth--;
    }

    @Override
    public void visit(ArgumentList node) {
        println(node, "");
        depth++;
        for (Expression expr : node.args()) {
            expr.accept(this);
        }
        depth--;
    }

    @Override
    public void visit(FunctionCallExpression node) {
        println(node, "[" + node.name().lexeme() + "]");
        depth++;
        node.arguments().accept(this);
        depth--;
    }

    @Override
    public void visit(FunctionCallStatement node) {
        println(node, "[" + node.getFunctionCall().name().lexeme() + "]");
        depth++;
        node.getFunctionCall().arguments().accept(this);
        depth--;
    }

    @Override
    public void visit(IfStatement node) {
        println(node, "");
        depth++;
        node.condition().accept(this);
        node.thenBlock().accept(this);
        if (node.elseBlock() != null) {
            node.elseBlock().accept(this);
        }
        depth--;
    }

    @Override
    public void visit(WhileStatement node) {
        println(node, "");
        depth++;
        node.condition().accept(this);
        node.body().accept(this);
        depth--;
    }

    @Override
    public void visit(RepeatStatement node) {
        println(node, "");
        depth++;
        node.body().accept(this);
        node.condition().accept(this);
        depth--;
    }

    @Override
    public void visit(ReturnStatement node) {
        println(node, "");
        if (node.value() != null) {
            depth++;
            node.value().accept(this);
            depth--;
        }
    }

    @Override
    public void visit(StatementSequence node) {
        println(node, "");
        depth++;
        for (Statement s : node.getStatements()) {
            s.accept(this);
        }
        depth--;
    }

    // Declarations
    @Override
    public void visit(VariableDeclaration node) {
        println(node, "[" + node.type() + "]");
        depth++;
        for (mocha.Token name : node.names()) {
            println(node, "[" + name.lexeme() + "]");
        }
        depth--;
    }

    @Override
    public void visit(FunctionBody node) {
        println(node, "");
        depth++;
        for (VariableDeclaration local : node.locals()) {
            local.accept(this);
        }
        node.statements().accept(this);
        depth--;
    }

    @Override
    public void visit(FunctionDeclaration node) {
        println(node, "[" + node.name().lexeme() + "]");
        depth++;
        node.body().accept(this);
        depth--;
    }

    @Override
    public void visit(DeclarationList node) {
        println(node, "");
        depth++;
        for (Node d : node.declarations()) {
            d.accept(this);
        }
        depth--;
    }

    @Override
    public void visit(Computation node) {
        println(node, "");
        depth++;
        node.variables().accept(this);
        node.functions().accept(this);
        node.mainStatementSequence().accept(this);
        depth--;
    }
}
