package types;

import java.util.Stack;
import java.util.List;
import java.util.ArrayList;

import ast.*;

import mocha.Token;
import mocha.Symbol;
import mocha.SymbolTable;

public class TypeChecker implements NodeVisitor {
    
    private StringBuilder errorBuffer;
    private Symbol currentFunction;
    private SymbolTable symbolTable;
    private Stack<Type> typeStack;

    public TypeChecker() {
        this.errorBuffer = new StringBuilder();
        this.currentFunction = null;
        this.symbolTable = null;
        this.typeStack = new Stack<>();
    }

    public boolean check(AST ast) {
        this.symbolTable = ast.getSymbolTable();
        ast.getComputation().accept(this);
        return !hasError();
    }

    /* 
     * Useful error strings:
     *
     * "Call with args " + argTypes + " matches no function signature."
     * "Call with args " + argTypes + " matches multiple function signatures."
     * 
     * "IfStat requires relation condition not " + cond.getClass() + "."
     * "WhileStat requires relation condition not " + cond.getClass() + "."
     * "RepeatStat requires relation condition not " + cond.getClass() + "."
     * 
     * "Function " + currentFunction.name() + " returns " + statRetType + " instead of " + funcRetType + "."
     * 
     * "Variable " + var.name() + " has invalid type " + var.type() + "."
     * "Array " + var.name() + " has invalid base type " + baseType + "."
     * 
     * 
     * "Function " + currentFunction.name() + " has a void arg at pos " + i + "."
     * "Function " + currentFunction.name() + " has an error in arg at pos " + i + ": " + ((ErrorType) t).message())
     * "Not all paths in function " + currentFunction.name() + " return."
     */

    

    private void reportError (int lineNum, int charPos, String message) {
        errorBuffer.append("TypeError(" + lineNum + "," + charPos + ")");
        errorBuffer.append("[" + message + "]" + "\n");
    }

    public boolean hasError () {
        return errorBuffer.length() != 0;
    }


    public String errorReport () {
        return errorBuffer.toString();
    }

    // Helper to retrieve root array variable name for error messages
    private String getArrayName(Expression expr) {
        if (expr instanceof Designator) {
            return ((Designator) expr).name().lexeme();
        }
        if (expr instanceof ArrayIndex) {
            return getArrayName(((ArrayIndex) expr).base());
        }
        return "<array>";
    }

    private Type tokenToType(Token t) {
        switch (t.kind()) {
            case INT: return new IntType();
            case FLOAT: return new FloatType();
            case BOOL: return new BoolType();
            case VOID: return new VoidType();
            default: return new ErrorType("Unknown type token: " + t.kind());
        }
    }

    private boolean guaranteesReturn (Statement stmt) {
        if (stmt instanceof ReturnStatement) {
            return true;
        }

        if (stmt instanceof IfStatement) {
            IfStatement ifs = (IfStatement) stmt;
            boolean thenReturns = guaranteesReturn(ifs.thenBlock());
            boolean elseReturns = false;
            if (ifs.elseBlock() != null) {
                elseReturns = guaranteesReturn(ifs.elseBlock());
            }
            // Only guarantees a return if both branches return
            return thenReturns && elseReturns;
        }

        if (stmt instanceof RepeatStatement) {
            // repeat-until executes at least once; it guarantees return
            // iff its body guarantees return
            RepeatStatement rep = (RepeatStatement) stmt;
            return guaranteesReturn(rep.body());
        }

        if (stmt instanceof StatementSequence) {
            StatementSequence seq = (StatementSequence) stmt;
            // As soon as a statement guarantees return, subsequent statements
            // are unreachable; short-circuit to true.
            for (Statement s : seq.getStatements()) {
                if (guaranteesReturn(s)) {
                    return true;
                }
            }
            return false;
        }

        // All other statements (assignments, calls, etc.) don't guarantee return
        return false;
    }

    @Override
    public void visit (Computation node) {
        // Don't process global variables - they're already in symbol table from parsing
        // node.variables().accept(this);        
        node.functions().accept(this);        
        node.mainStatementSequence().accept(this);
    }

    @Override
    public void visit(DeclarationList node) {
        for (Node decl : node.declarations()) {
            decl.accept(this);
        }
    }

    @Override
    public void visit(BoolLiteral node) {
        typeStack.push(new BoolType());
    }

    @Override
    public void visit(IntegerLiteral node) {
        typeStack.push(new IntType());
    }

    @Override
    public void visit(FloatLiteral node) {
        typeStack.push(new FloatType());
    }

    @Override
    public void visit(Designator node) {
        try {
            Symbol symbol = symbolTable.lookup(node.name().lexeme());
            typeStack.push(symbol.type());
        } catch (Error e) {
            reportError(node.lineNumber(), node.charPosition(), 
                "Unknown variable: " + node.name().lexeme());
            typeStack.push(new ErrorType("Unknown variable: " + node.name().lexeme()));
        }
    }

    @Override
    public void visit(AddressOf node) {
        node.operand().accept(this);
        Type operandType = typeStack.pop();        
        Type resultType = new AddressType(operandType);
        typeStack.push(resultType);
    }

    @Override
    public void visit(ArrayIndex node) {
        node.base().accept(this);
        node.index().accept(this);
        
        Type indexType = typeStack.pop();
        Type baseType = typeStack.pop();
        
        if (baseType instanceof ErrorType){
            typeStack.push(baseType);
            return;
        }

        if (indexType instanceof ErrorType){
            typeStack.push(indexType);
            return;
        }

        // Compile-time bounds check
        if (baseType instanceof ArrayType && node.index() instanceof IntegerLiteral) {
            ArrayType at = (ArrayType) baseType;
            List<Integer> dims = at.getDimensions();
            if (!dims.isEmpty()) {
                int declared = dims.get(0);
                int actual = ((IntegerLiteral) node.index()).getValue();
                if (declared != -1 && (actual < 0 || actual >= declared)) {
                    String arrName = getArrayName(node.base());
                    String msg = "Array Index Out of Bounds : " + actual + " for array " + arrName;
                    reportError(node.lineNumber(), node.charPosition(), msg);
                    typeStack.push(new ErrorType(msg));
                    return;
                }
            }
        }
        Type resultType = baseType.index(indexType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Dereference node) {
        node.operand().accept(this);
        Type operandType = typeStack.pop();
        Type resultType = operandType.deref();
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(LogicalNot node) {
        node.operand().accept(this);
        Type operandType = typeStack.pop();
        Type resultType = operandType.not();
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Power node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();
        Type resultType = leftType.power(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Multiplication node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();
        Type resultType = leftType.mul(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Division node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();
        Type resultType = leftType.div(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Modulo node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);        
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();
        Type resultType = leftType.mod(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(LogicalAnd node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();        
        Type resultType = leftType.and(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Addition node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();        
        Type resultType = leftType.add(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Subtraction node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();        
        Type resultType = leftType.sub(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(LogicalOr node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();
        Type resultType = leftType.or(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Relation node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Type rightType = typeStack.pop();
        Type leftType = typeStack.pop();
        Type resultType = leftType.compare(rightType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        typeStack.push(resultType);
    }

    @Override
    public void visit(Assignment node) {
        node.getDestination().accept(this);
        if (node.getSource() != null) {
            node.getSource().accept(this);
            Type sourceType = typeStack.pop();
            Type destType = typeStack.pop();
            Type resultType = destType.assign(sourceType);
            if (resultType instanceof ErrorType) {
                // Report at the start of the assignment statement (destination start)
                int line = node.getDestination() instanceof Designator ? ((Designator) node.getDestination()).name().lineNumber() : node.lineNumber();
                int col = node.getDestination() instanceof Designator ? ((Designator) node.getDestination()).name().charPosition() : node.charPosition();
                reportError(line, col, ((ErrorType) resultType).getMessage());
            }
            // Don't push result type for assignment - it's a statement, not an expression
        } else {
            // No source - just pop destination type
            typeStack.pop();
        }
    }

    @Override
    public void visit(ArgumentList node) {
        for (Expression arg : node.args()) {
            arg.accept(this);
        }
    }

    @Override
    public void visit(FunctionCall node) {
        node.arguments().accept(this);
        List<Type> argTypes = new ArrayList<>();
        for (int i = 0; i < node.arguments().args().size(); i++) {
            argTypes.add(0, typeStack.pop()); // Add to front to maintain order
        }
        
        // Try to resolve function with signature matching
        try {
            Symbol functionSymbol = symbolTable.lookupFunction(node.name().lexeme(), argTypes);
            // Function found with matching signature - push return type
            if (functionSymbol.type() instanceof FuncType) {
                FuncType funcType = (FuncType) functionSymbol.type();
                Type returnType = funcType.getReturnType();
                
                // Check for void arguments
                for (int i = 0; i < argTypes.size(); i++) {
                    Type argType = argTypes.get(i);
                    if (argType instanceof VoidType) {
                        reportError(node.lineNumber(), node.charPosition(), 
                            "Function " + node.name().lexeme() + " has a void arg at pos " + i + ".");
                    } else if (argType instanceof ErrorType) {
                        reportError(node.lineNumber(), node.charPosition(), 
                            "Function " + node.name().lexeme() + " has an error in arg at pos " + i + ": " + ((ErrorType) argType).getMessage());
                    }
                }
                
                typeStack.push(returnType);
            } else {
                typeStack.push(new ErrorType("Function symbol has non-function type"));
            }
        } catch (Error e) {
            // Check if function exists at all (for better error message)
            try {
                symbolTable.lookup(node.name().lexeme());
                // Function exists but signature doesn't match
                reportError(node.lineNumber(), node.charPosition(), 
                    "Call with args " + argTypes + " matches no function signature.");
            } catch (Error e2) {
                // Function doesn't exist at all
                reportError(node.lineNumber(), node.charPosition(), 
                    "Call with args " + argTypes + " matches no function signature.");
            }
            typeStack.push(new ErrorType("Function call failed"));
        }
    }

    @Override
    public void visit(IfStatement node) {
        node.condition().accept(this);
        Type condType = typeStack.pop();

        if (!(condType instanceof BoolType)) {
            reportError(node.lineNumber(), node.charPosition(), 
                "IfStat requires relation condition not " + condType.toString() + ".");
        }
        
        node.thenBlock().accept(this);
        
        if (node.elseBlock() != null) {
            node.elseBlock().accept(this);
        }
    }

    @Override
    public void visit(WhileStatement node) {
        node.condition().accept(this);
        Type condType = typeStack.pop();
        if (!(condType instanceof BoolType)) {
            reportError(node.lineNumber(), node.charPosition(), 
                "WhileStat requires relation condition not " + condType.toString() + ".");
        }        
        node.body().accept(this);
    }

    @Override
    public void visit(RepeatStatement node) {
        node.body().accept(this);
        node.condition().accept(this);
        Type condType = typeStack.pop();
        if (!(condType instanceof BoolType)) {
            reportError(node.lineNumber(), node.charPosition(), 
                "RepeatStat requires relation condition not " + condType.toString() + ".");
        }
    }

    @Override
    public void visit(ReturnStatement node) {
        if (node.value() != null) {
            node.value().accept(this);            
            Type returnType = typeStack.pop();
            Type expectedType = currentFunction.type();
            
            FuncType funcType = (FuncType) expectedType;
            Type funcReturnType = funcType.getReturnType();
            
            if (!returnType.equivalent(funcReturnType)) {
                reportError(node.lineNumber(), node.charPosition(), 
                    "Function " + currentFunction.name() + " returns " + returnType + 
                    " instead of " + funcReturnType + ".");
            }
        } else {
            Type expectedType = currentFunction.type();
            if (expectedType instanceof FuncType) {
                FuncType funcType = (FuncType) expectedType;
                Type funcReturnType = funcType.getReturnType();
                
                if (!(funcReturnType instanceof VoidType)) {
                    reportError(node.lineNumber(), node.charPosition(), 
                        "Function " + currentFunction.name() + " expects return value of type " + funcReturnType);
                }
            }
        }
    }

    @Override
    public void visit(StatementSequence node) {
        for (Statement stmt : node.getStatements()) {
            stmt.accept(this);
        }
    }

    @Override
    public void visit(VariableDeclaration node) {
        Type varType = node.type();
        
        if (varType instanceof ArrayType) {
            ArrayType arrType = (ArrayType) varType;
            List<Integer> dims = arrType.getDimensions();
            for (Token nameTok : node.names()) {
                for (Integer d : dims) {
                    if (d == null || d <= 0) {
                        reportError(node.lineNumber(), node.charPosition(),
                            "Array " + nameTok.lexeme() + " has invalid size " + d + ".");
                    }
                }
            }
        }
        
        if (varType == null) {
            varType = new ErrorType("Unknown variable type");
        }
        
        for (Token nameTok : node.names()) {
            // Check if variable already exists (global variables)
            try {
                Symbol existing = symbolTable.lookup(nameTok.lexeme());
                if (!existing.type().equivalent(varType)) {
                    reportError(nameTok.lineNumber(), nameTok.charPosition(), 
                        "Variable " + nameTok.lexeme() + " type mismatch");
                }
            } catch (Error e) {
                // Variable doesn't exist, so insert it (local variables)
                symbolTable.insert(nameTok.lexeme(), varType);
            }
        }
    }

    @Override
    public void visit(FunctionBody node) {
        for (VariableDeclaration local : node.locals()) {
            local.accept(this);
        }
        node.statements().accept(this);
    }

    @Override
    public void visit(FunctionDeclaration node) {
        // Set current function for return type checking
        this.currentFunction = symbolTable.lookup(node.name().lexeme());
        
        symbolTable.enterScope();

        // Add formal parameters into scope
        for (Symbol param : node.formals()) {
            try { 
                symbolTable.insert(param.name(), param.type()); 
            } catch (Error ignored) {}
        }

        node.body().accept(this);

        // After normal type checking, ensure non-void functions return on all paths
        FuncType funcType = (FuncType) currentFunction.type();
        Type expected = funcType.getReturnType();
        if (!(expected instanceof VoidType)) {
            boolean allPathsReturn = guaranteesReturn(node.body().statements());
            if (!allPathsReturn) {
                reportError(node.lineNumber(), node.charPosition(),
                    "Not all paths in function " + currentFunction.name() + " return.");
            }
        }

        symbolTable.exitScope();
        
        this.currentFunction = null;
    }
}
