package ast;

import mocha.SymbolTable;

public class AST {

    private Computation computation;
    private SymbolTable symbolTable;

    public AST() {
        this.computation = null;
        this.symbolTable = null;
    }

    public AST(Computation computation) {
        this.computation = computation;
        this.symbolTable = null;
    }

    public AST(Computation computation, SymbolTable symbolTable) {
        this.computation = computation;
        this.symbolTable = symbolTable;
    }

    public Computation getComputation() {
        return computation;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public String printPreOrder(){
        if (computation == null) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        PreOrderVisitor visitor = new PreOrderVisitor(result);
        computation.accept(visitor);
        return result.toString();
    }
    
    private static class PreOrderVisitor implements NodeVisitor {
        private StringBuilder result;
        
        public PreOrderVisitor(StringBuilder result) {
            this.result = result;
        }
        
        @Override
        public void visit(Computation node) {
            result.append(ASTNonTerminal.Computation.toString());
            result.append("\n");
            
            if (node.variables() != null) {
                node.variables().accept(this);
            }
            if (node.functions() != null) {
                node.functions().accept(this);
            }
            if (node.mainStatementSequence() != null) {
                node.mainStatementSequence().accept(this);
            }
        }
        
        @Override
        public void visit(StatementSequence node) {
            result.append(ASTNonTerminal.StatementSequence.toString());
            result.append("\n");
            
            for (Statement stmt : node.getStatements()) {
                stmt.accept(this);
            }
        }
        
        @Override
        public void visit(Assignment node) {
            result.append(ASTNonTerminal.Assignment.toString());
            result.append("\n");
            
            node.getDestination().accept(this);
            node.getSource().accept(this);
        }
        
        @Override
        public void visit(IfStatement node) {
            result.append(ASTNonTerminal.IfStatement.toString());
            result.append("\n");
            
            node.condition().accept(this);
            node.thenBlock().accept(this);
            if (node.elseBlock() != null) {
                node.elseBlock().accept(this);
            }
        }
        
        @Override
        public void visit(WhileStatement node) {
            result.append(ASTNonTerminal.WhileStatement.toString());
            result.append("\n");
            
            node.condition().accept(this);
            node.body().accept(this);
        }
        
        @Override
        public void visit(RepeatStatement node) {
            result.append(ASTNonTerminal.RepeatStatement.toString());
            result.append("\n");
            
            node.body().accept(this);
            node.condition().accept(this);
        }
        
        @Override
        public void visit(ReturnStatement node) {
            result.append(ASTNonTerminal.ReturnStatement.toString());
            result.append("\n");
            
            if (node.value() != null) {
                node.value().accept(this);
            }
        }
        
        @Override
        public void visit(FunctionCallExpression node) {
            result.append(ASTNonTerminal.FunctionCall.toString());
            result.append("\n");
            
            node.arguments().accept(this);
        }
        
        @Override
        public void visit(FunctionCallStatement node) {
            result.append(ASTNonTerminal.FunctionCall.toString());
            result.append("\n");
            
            node.getFunctionCall().accept(this);
        }
        
        @Override
        public void visit(ArgumentList node) {
            result.append(ASTNonTerminal.ArgumentList.toString());
            result.append("\n");
            
            for (Expression expr : node.args()) {
                expr.accept(this);
            }
        }
        
        @Override
        public void visit(Designator node) {
            result.append("Designator[").append(node.name().lexeme()).append("]");
            result.append("\n");
        }
        
        @Override
        public void visit(ArrayIndex node) {
            result.append(ASTNonTerminal.ArrayIndex.toString());
            result.append("\n");
            
            node.base().accept(this);
            node.index().accept(this);
        }
        
        @Override
        public void visit(Addition node) {
            result.append(ASTNonTerminal.Addition.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(Subtraction node) {
            result.append(ASTNonTerminal.Subtraction.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(Multiplication node) {
            result.append(ASTNonTerminal.Multiplication.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(Division node) {
            result.append(ASTNonTerminal.Division.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(Power node) {
            result.append(ASTNonTerminal.Power.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(Modulo node) {
            result.append(ASTNonTerminal.Modulo.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(LogicalAnd node) {
            result.append(ASTNonTerminal.LogicalAnd.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(LogicalOr node) {
            result.append(ASTNonTerminal.LogicalOr.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(LogicalNot node) {
            result.append(ASTNonTerminal.LogicalNot.toString());
            result.append("\n");
            
            node.operand().accept(this);
        }
        
        @Override
        public void visit(Relation node) {
            result.append(ASTNonTerminal.Relation.toString());
            result.append("\n");
            
            node.getLeft().accept(this);
            node.getRight().accept(this);
        }
        
        @Override
        public void visit(IntegerLiteral node) {
            result.append(ASTNonTerminal.IntegerLiteral.toString());
            result.append("[").append(node.getValue()).append("]");
            result.append("\n");
        }
        
        @Override
        public void visit(FloatLiteral node) {
            result.append(ASTNonTerminal.FloatLiteral.toString());
            result.append("[").append(node.getValue()).append("]");
            result.append("\n");
        }
        
        @Override
        public void visit(BoolLiteral node) {
            result.append(ASTNonTerminal.BoolLiteral.toString());
            result.append("[").append(node.getValue()).append("]");
            result.append("\n");
        }
        
        @Override
        public void visit(VariableDeclaration node) {
            result.append(ASTNonTerminal.VariableDeclaration.toString());
            result.append("\n");
        }
        
        @Override
        public void visit(FunctionDeclaration node) {
            result.append(ASTNonTerminal.FunctionDeclaration.toString());
            result.append("\n");
            
            node.body().accept(this);
        }
        
        @Override
        public void visit(FunctionBody node) {
            result.append(ASTNonTerminal.FunctionBody.toString());
            result.append("\n");
            
            for (VariableDeclaration local : node.locals()) {
                local.accept(this);
            }
            node.statements().accept(this);
        }
        
        @Override
        public void visit(DeclarationList node) {
            result.append(ASTNonTerminal.DeclarationList.toString());
            result.append("\n");
            
            for (Node decl : node.declarations()) {
                decl.accept(this);
            }
        }
        
        @Override
        public void visit(Dereference node) {
            result.append(ASTNonTerminal.Dereference.toString());
            result.append("\n");
            
            node.operand().accept(this);
        }
    }
}
