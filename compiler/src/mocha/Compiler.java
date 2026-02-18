package mocha;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.io.InputStream;

import ast.*;
import types.*;

// mocha imports
import mocha.Scanner;
import mocha.Symbol;
import mocha.Token;
import mocha.NonTerminal;

public class Compiler {

    // Error Reporting ============================================================
    private StringBuilder errorBuffer = new StringBuilder();

    private String reportSyntaxError(NonTerminal nt) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() + ")[Expected a token from " + nt.name()
                + " but got " + currentToken.kind() + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    private String reportSyntaxError(Token.Kind kind) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() + ")[Expected " + kind + " but got "
                + currentToken.kind() + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    public String errorReport() {
        return errorBuffer.toString();
    }

    public boolean hasError() {
        return errorBuffer.length() != 0;
    }

    private class QuitParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public QuitParseException(String errorMessage) {
            super(errorMessage);
        }
    }

    private int lineNumber() {
        return currentToken.lineNumber();
    }

    private int charPosition() {
        return currentToken.charPosition();
    }

    // Compiler ===================================================================
    private Scanner scanner;
    private Token currentToken;
    private String sourceFileName;
    private boolean firstPass = true; // For two-pass parsing
    private int savedTokenIndex; // Store token position for reset
    private ast.AST parsedAST; // Store the parsed AST

    private int numDataRegisters; // available registers are [1..numDataRegisters]
    private List<Integer> instructions;

    // Need to map from IDENT to memory offset

    public Compiler(Scanner scanner, int numRegs) {
        this.scanner = scanner;
        currentToken = this.scanner.next();
        numDataRegisters = numRegs;
        instructions = new ArrayList<>();
        this.sourceFileName = scanner.getSourceFileName();
    }

    // TODO
    public ast.AST genAST() {
        initSymbolTable();
        try {
            Computation comp = computation();
            parsedAST = new ast.AST(comp, symbolTable);
            return parsedAST;
        } catch (QuitParseException q) {
            parsedAST = new ast.AST();
            return parsedAST;
        }
    }

    public void interpret(InputStream in) {
        if (hasError()) {
            System.out.println("Error parsing file.");
            System.out.println(errorReport());
            return;
        }

        // Use the stored AST - it should have been set by genAST() call
        if (parsedAST == null || parsedAST.getComputation() == null) {
            genAST();
        }

        Interpreter interpreter = new Interpreter(this.symbolTable, in);
        interpreter.interpret(parsedAST);
        System.out.flush();
    }

    public int[] compile() {
        initSymbolTable();
        try {
            computation();
            return instructions.stream().mapToInt(Integer::intValue).toArray();
        } catch (QuitParseException q) {
            // errorBuffer.append("SyntaxError(" + lineNumber() + "," + charPosition() +
            // ")");
            // errorBuffer.append("[Could not complete parsing.]");
            return new ArrayList<Integer>().stream().mapToInt(Integer::intValue).toArray();
        }
    }

    // IR Generation ===========================================================
    public ir.IROutput genIR(ast.AST ast) {
        // Generate SSA IR for visualization (autograder expects SSA form)
        java.util.List<ir.cfg.CFG> cfgs = genSSA(ast);
        return new ir.IROutput(cfgs);
    }

    public java.util.List<ir.cfg.CFG> genSSA(ast.AST ast) {
        ir.IRGenerator generator = new ir.IRGenerator(this.symbolTable);
        java.util.List<ir.cfg.CFG> cfgs = generator.generate(ast);
        this.currentCFGs = cfgs;

        for (ir.cfg.CFG cfg : cfgs) {
                ir.ssa.SSAConverter converter = new ir.ssa.SSAConverter(cfg);
                converter.convertToSSA();
        }

        return cfgs;
    }

    public String optimization(java.util.List<String> opts, boolean loop, boolean max) {
        // Reuse already-generated CFGs instead of regenerating
        if (this.currentCFGs == null) {
            genSSA(parsedAST);
        }
        return optimization(opts, this.currentCFGs, loop, max);
    }

    public String optimization(java.util.List<String> opts, java.util.List<ir.cfg.CFG> cfgs, boolean loop,
            boolean max) {
        ir.optimizations.Optimizer optimizer = new ir.optimizations.Optimizer();
        optimizer.setSourceFileName(this.sourceFileName);
        optimizer.setOptimizationFlags(opts, loop, max);
        String result = optimizer.applyOptimizations(opts, cfgs, loop, max);

        return result;
    }

    public void registerAllocation(java.util.List<ir.cfg.CFG> cfgs) {
        // Use the new SSA-based Chordal Register Allocator
        new ir.regalloc.RegisterAllocator(numDataRegisters).allocate(cfgs);
    }
    
    // Store reference to CFGs for code generation
    public void regAlloc(int numRegs) {
        if (this.currentCFGs == null) {
             // Should have been generated
             genSSA(parsedAST);
        }
        new ir.regalloc.RegisterAllocator(numRegs).allocate(this.currentCFGs);
    }

    public int[] genCode() {
        if (currentCFGs == null) {
            throw new RuntimeException("Code generation requires prior IR generation and allocation.");
        }
        
        ir.codegen.CodeGenerator codegen = new ir.codegen.CodeGenerator();
        return codegen.generate(currentCFGs);
    }
    
    private java.util.List<ir.cfg.CFG> currentCFGs;
    
    public java.util.List<ir.cfg.CFG> getCurrentCFGs() {
        return currentCFGs;
    }
    
    // SymbolTable Management =====================================================
    private SymbolTable symbolTable;

    private void initSymbolTable() {
        symbolTable = new SymbolTable();
    }

    private void enterScope() {
        symbolTable.enterScope();
    }

    private void exitScope() {
        symbolTable.exitScope();
    }

    private Symbol tryResolveVariable(Token ident) {
        try {
            return symbolTable.lookup(ident.lexeme());
        } catch (SymbolNotFoundError e) {
            reportResolveSymbolError(ident.lexeme(), ident.lineNumber(), ident.charPosition());
            return null;
        }
    }

    private Symbol tryResolveFunction(Token ident) {
        try {
            return symbolTable.lookup(ident.lexeme());
        } catch (SymbolNotFoundError e) {
            reportResolveSymbolError(ident.lexeme(), ident.lineNumber(), ident.charPosition());
            return null;
        }
    }

    private Symbol tryDeclareVariable(Token ident, types.Type type) {
        try {
            return symbolTable.insert(ident.lexeme(), type);
        } catch (RedeclarationError e) {
            reportDeclareSymbolError(ident.lexeme(), ident.lineNumber(), ident.charPosition());
            return null;
        }
    }

    private Symbol tryDeclareFunction(Token ident, types.Type type) {
        try {
            return symbolTable.insertFunction(ident.lexeme(), type);
        } catch (RedeclarationError e) {
            reportDeclareSymbolError(ident.lexeme(), ident.lineNumber(), ident.charPosition());
            return null;
        }
    }

    private String reportResolveSymbolError(String name, int lineNum, int charPos) {
        String message = "ResolveSymbolError(" + lineNum + "," + charPos + ")[Could not find " + name + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    private String reportDeclareSymbolError(String name, int lineNum, int charPos) {
        String message = "DeclareSymbolError(" + lineNum + "," + charPos + ")[" + name + " already exists.]";
        errorBuffer.append(message + "\n");
        return message;
    }

    // Helper Methods =============================================================
    private boolean have(Token.Kind kind) {
        return currentToken.is(kind);
    }

    private boolean have(NonTerminal nt) {
        return nt.firstSet().contains(currentToken.kind());
    }

    private boolean accept(Token.Kind kind) {
        if (have(kind)) {
            try {
                currentToken = scanner.next();
            } catch (NoSuchElementException e) {
                if (!kind.equals(Token.Kind.EOF)) {
                    String errorMessage = reportSyntaxError(kind);
                    throw new QuitParseException(errorMessage);
                }
            }
            return true;
        }
        return false;
    }

    private boolean accept(NonTerminal nt) {
        if (have(nt)) {
            currentToken = scanner.next();
            return true;
        }
        return false;
    }

    private boolean expect(Token.Kind kind) {
        if (accept(kind)) {
            return true;
        }
        String errorMessage = reportSyntaxError(kind);
        throw new QuitParseException(errorMessage);
    }

    private boolean expect(NonTerminal nt) {
        if (accept(nt)) {
            return true;
        }
        String errorMessage = reportSyntaxError(nt);
        throw new QuitParseException(errorMessage);
    }

    private Token expectRetrieve(Token.Kind kind) {
        Token tok = currentToken;
        if (accept(kind)) {
            return tok;
        }
        String errorMessage = reportSyntaxError(kind);
        throw new QuitParseException(errorMessage);
    }

    private Token expectRetrieve(NonTerminal nt) {
        Token tok = currentToken;
        if (accept(nt)) {
            return tok;
        }
        String errorMessage = reportSyntaxError(nt);
        throw new QuitParseException(errorMessage);
    }

    private Type tokenToType(Token t) {
        switch (t.kind()) {
            case INT:
                return new IntType();
            case FLOAT:
                return new FloatType();
            case BOOL:
                return new BoolType();
            case VOID:
                return new VoidType();
            default:
                return new ErrorType("Unknown type token: " + t.kind());
        }
    }

    // Grammar Rules ==============================================================

    // function for matching rule that only expects nonterminal's FIRST set
    private Token matchNonTerminal(NonTerminal nt) {
        return expectRetrieve(nt);
    }

    private Expression literal() {
        Token token = matchNonTerminal(NonTerminal.LITERAL);
        switch (token.kind()) {
            case INT_VAL:
                return new IntegerLiteral(token);
            case FLOAT_VAL:
                return new FloatLiteral(token);
            case TRUE:
            case FALSE:
                return new BoolLiteral(token);
            default:
                throw new RuntimeException("Unexpected literal type: " + token.kind());
        }
    }

    private Expression designator() {
        Token ident = expectRetrieve(Token.Kind.IDENT);
        tryResolveVariable(ident);
        Expression base = new Designator(ident);

        while (have(Token.Kind.OPEN_BRACKET)) {
            Token openBr = expectRetrieve(Token.Kind.OPEN_BRACKET);
            Expression index = relExpr();
            base = new ArrayIndex(openBr.lineNumber(), openBr.charPosition(), base, index);
            expect(Token.Kind.CLOSE_BRACKET);
        }

        return base;
    }

    private Expression groupExpr() {
        if (have(NonTerminal.LITERAL)) {
            return literal();
        } else if (have(NonTerminal.DESIGNATOR)) {
            return designator();
        } else if (have(Token.Kind.NOT)) {
            Token notTok = expectRetrieve(Token.Kind.NOT);
            Expression expr = relExpr();
            return new LogicalNot(notTok.lineNumber(), notTok.charPosition(), expr);
        } else if (have(NonTerminal.RELATION)) {
            return relation();
        } else if (have(NonTerminal.FUNC_CALL)) {
            return funcCall();
        } else {
            String errorMessage = reportSyntaxError(NonTerminal.GROUP_EXPR);
            throw new QuitParseException(errorMessage);
        }
    }

    private Expression powExpr() {
        Expression left = groupExpr();
        while (have(NonTerminal.POW_OP)) {
            Token op = currentToken;
            accept(NonTerminal.POW_OP);
            Expression right = groupExpr();
            left = new Power(op.lineNumber(), op.charPosition(), left, op, right);
        }
        return left;
    }

    private Expression multExpr() {
        Expression left = powExpr();
        while (have(NonTerminal.MUL_OP)) {
            Token op = currentToken;
            accept(NonTerminal.MUL_OP);
            Expression right = powExpr();
            switch (op.kind()) {
                case MUL:
                    left = new Multiplication(op.lineNumber(), op.charPosition(), left, op, right);
                    break;
                case DIV:
                    left = new Division(op.lineNumber(), op.charPosition(), left, op, right);
                    break;
                case MOD:
                    left = new Modulo(op.lineNumber(), op.charPosition(), left, op, right);
                    break;
                case AND:
                    left = new LogicalAnd(op.lineNumber(), op.charPosition(), left, op, right);
                    break;
            }
        }
        return left;
    }

    private Expression addExpr() {
        Expression left = multExpr();
        while (have(NonTerminal.ADD_OP)) {
            Token op = currentToken;
            accept(NonTerminal.ADD_OP);
            Expression right = multExpr();
            switch (op.kind()) {
                case ADD:
                    left = new Addition(op.lineNumber(), op.charPosition(), left, op, right);
                    break;
                case SUB:
                    left = new Subtraction(op.lineNumber(), op.charPosition(), left, op, right);
                    break;
                case OR:
                    left = new LogicalOr(op.lineNumber(), op.charPosition(), left, op, right);
                    break;
            }
        }
        return left;
    }

    private Expression relExpr() {
        Expression left = addExpr();
        while (have(NonTerminal.REL_OP)) {
            Token op = currentToken;
            accept(NonTerminal.REL_OP);
            Expression right = addExpr();
            left = new Relation(op.lineNumber(), op.charPosition(), left, op, right);
        }
        return left;
    }

    private Expression relation() {
        expect(Token.Kind.OPEN_PAREN);
        Expression expr = relExpr();
        expect(Token.Kind.CLOSE_PAREN);
        return expr;
    }

    // assign = designator ( ( assignOp relExpr ) | unaryOp )
    private Assignment assign() {
        Expression dest = designator();

        if (have(NonTerminal.ASSIGN_OP)) {
            Token op = currentToken;
            accept(NonTerminal.ASSIGN_OP);
            Expression src = relExpr();
            return new Assignment(op.lineNumber(), op.charPosition(), dest, op, src);
        } else if (have(NonTerminal.UNARY_OP)) {
            Token op = currentToken;
            accept(NonTerminal.UNARY_OP);
            // For unary ops like ++ and --, we'll treat them as assignments
            return new Assignment(op.lineNumber(), op.charPosition(), dest, op, null);
        } else {
            String errorMessage = reportSyntaxError(NonTerminal.ASSIGN);
            throw new QuitParseException(errorMessage);
        }
    }

    // funcCall = "call" ident "(" [ relExpr { "," relExpr } ] ")"
    private FunctionCallExpression funcCall() {
        expect(Token.Kind.CALL);
        Token name = expectRetrieve(Token.Kind.IDENT);
        expect(Token.Kind.OPEN_PAREN);

        ArgumentList args = new ArgumentList(lineNumber(), charPosition());

        if (have(NonTerminal.REL_EXPR)) {
            Expression arg = relExpr();
            args.add(arg);

            while (accept(Token.Kind.COMMA)) {
                arg = relExpr();
                args.add(arg);
            }
        }

        expect(Token.Kind.CLOSE_PAREN);
        tryResolveFunction(name);

        return new FunctionCallExpression(name.lineNumber(), name.charPosition(), name, args);
    }

    // ifStat = "if" relation "then" statSeq [ "else" statSeq ] "fi"
    private IfStatement ifStat() {
        Token ifTok = expectRetrieve(Token.Kind.IF);
        Expression condition = relation();
        expect(Token.Kind.THEN);
        StatementSequence thenBlock = statSeq();

        StatementSequence elseBlock = null;
        if (accept(Token.Kind.ELSE)) {
            elseBlock = statSeq();
        }

        expect(Token.Kind.FI);
        return new IfStatement(ifTok.lineNumber(), ifTok.charPosition(), condition, thenBlock, elseBlock);
    }

    // whileStat = "while" relation "do" statSeq "od"
    private WhileStatement whileStat() {
        Token whileTok = expectRetrieve(Token.Kind.WHILE);
        Expression condition = relation();
        expect(Token.Kind.DO);
        StatementSequence body = statSeq();
        expect(Token.Kind.OD);
        return new WhileStatement(whileTok.lineNumber(), whileTok.charPosition(), condition, body);
    }

    // repeatStat = "repeat" statSeq "until" relation
    private RepeatStatement repeatStat() {
        Token repeatTok = expectRetrieve(Token.Kind.REPEAT);
        StatementSequence body = statSeq();
        expect(Token.Kind.UNTIL);
        Expression condition = relation();
        return new RepeatStatement(repeatTok.lineNumber(), repeatTok.charPosition(), body, condition);
    }

    // returnStat = "return" [ relExpr ]
    private ReturnStatement returnStat() {
        Token retTok = expectRetrieve(Token.Kind.RETURN);

        Expression value = null;
        if (have(NonTerminal.REL_EXPR)) {
            value = relExpr();
        }
        return new ReturnStatement(retTok.lineNumber(), retTok.charPosition(), value);
    }

    // statement = assign | funcCall | ifStat | whileStat | repeatStat | returnStat
    private Statement statement() {
        if (have(NonTerminal.ASSIGN)) {
            return assign();
        } else if (have(NonTerminal.FUNC_CALL)) {
            FunctionCallExpression funcCall = funcCall();
            return new FunctionCallStatement(funcCall.lineNumber(), funcCall.charPosition(), funcCall);
        } else if (have(NonTerminal.IF_STAT)) {
            return ifStat();
        } else if (have(NonTerminal.WHILE_STAT)) {
            return whileStat();
        } else if (have(NonTerminal.REPEAT_STAT)) {
            return repeatStat();
        } else if (have(NonTerminal.RETURN_STAT)) {
            return returnStat();
        } else {
            String errorMessage = reportSyntaxError(NonTerminal.STATEMENT);
            throw new QuitParseException(errorMessage);
        }
    }

    // statSeq = statement ";" { statement ";" }
    private StatementSequence statSeq() {
        int startLine = lineNumber();
        int startChar = charPosition();

        StatementSequence seq = new StatementSequence(startLine, startChar);

        seq.addStatement(statement());
        expect(Token.Kind.SEMICOLON);

        while (have(NonTerminal.STATEMENT)) {
            seq.addStatement(statement());
            expect(Token.Kind.SEMICOLON);
        }

        return seq;
    }

    private types.Type typeDecl() {
        Token base = expectRetrieve(NonTerminal.TYPE_DECL);
        List<Integer> dims = new ArrayList<>();

        while (accept(Token.Kind.OPEN_BRACKET)) {
            Token sizeTok = expectRetrieve(Token.Kind.INT_VAL);
            expect(Token.Kind.CLOSE_BRACKET);
            dims.add(Integer.valueOf(sizeTok.lexeme()));
        }

        if (dims.isEmpty())
            return tokenToType(base);

        // Create recursive ArrayType structure
        Type currentType = tokenToType(base);
        for (int i = dims.size() - 1; i >= 0; i--) {
            currentType = new types.ArrayType(currentType, dims.get(i));
        }
        return currentType;
    }

    private VariableDeclaration varDecl() {
        int startLine = lineNumber();
        int startChar = charPosition();

        Type type = typeDecl();
        List<Token> names = new ArrayList<>();

        names.add(expectRetrieve(Token.Kind.IDENT));

        while (accept(Token.Kind.COMMA)) {
            names.add(expectRetrieve(Token.Kind.IDENT));
        }

        expect(Token.Kind.SEMICOLON);

        for (Token name : names) {
            tryDeclareVariable(name, type);
        }

        return new VariableDeclaration(startLine, startChar, type, names);
    }

    private types.Type paramType() {
        Token base = expectRetrieve(NonTerminal.PARAM_TYPE);
        List<Integer> dims = new ArrayList<>();
        while (accept(Token.Kind.OPEN_BRACKET)) {
            expect(Token.Kind.CLOSE_BRACKET);
            dims.add(Integer.valueOf(-1));
        }
        if (dims.isEmpty())
            return tokenToType(base);

        // Create recursive ArrayType structure
        Type currentType = tokenToType(base);
        for (int i = dims.size() - 1; i >= 0; i--) {
            currentType = new types.ArrayType(currentType, dims.get(i));
        }
        return currentType;
    }

    private Symbol paramDecl() {
        types.Type type = paramType();
        Token name = expectRetrieve(Token.Kind.IDENT);
        return new Symbol(name.lexeme(), type);
    }

    private List<Symbol> formalParam() {
        expect(Token.Kind.OPEN_PAREN);

        List<Symbol> params = new ArrayList<>();
        if (have(NonTerminal.PARAM_DECL)) {
            params.add(paramDecl());
            while (accept(Token.Kind.COMMA)) {
                params.add(paramDecl());
            }
        }

        expect(Token.Kind.CLOSE_PAREN);
        return params;
    }

    // funcBody = "{" { varDecl } statSeq "}" ";"
    private FunctionBody funcBody() {
        int startLine = lineNumber();
        int startChar = charPosition();

        expect(Token.Kind.OPEN_BRACE);

        List<VariableDeclaration> locals = new ArrayList<>();
        while (have(NonTerminal.VAR_DECL)) {
            locals.add(varDecl());
        }

        StatementSequence statements = statSeq();
        expect(Token.Kind.CLOSE_BRACE);
        expect(Token.Kind.SEMICOLON);

        return new FunctionBody(startLine, startChar, locals, statements);
    }

    // funcDecl = "function" ident formalParam ":" ( "void" | type ) funcBody
    private FunctionDeclaration funcDecl() {
        int startLine = lineNumber();
        int startChar = charPosition();

        expect(Token.Kind.FUNC);
        Token name = expectRetrieve(Token.Kind.IDENT);
        List<Symbol> formals = formalParam();
        expect(Token.Kind.COLON);

        types.Type returnType;
        if (have(Token.Kind.VOID)) {
            returnType = tokenToType(expectRetrieve(Token.Kind.VOID));
        } else if (have(Token.Kind.BOOL) || have(Token.Kind.INT) || have(Token.Kind.FLOAT)) {
            returnType = typeDecl();
        } else {
            String errorMessage = reportSyntaxError(NonTerminal.TYPE_DECL);
            throw new QuitParseException(errorMessage);
        }

        // Build function type for symbol table
        types.TypeList paramTypes = new types.TypeList();
        for (Symbol param : formals) {
            paramTypes.append(param.type());
        }

        types.Type funcReturnType = returnType;
        types.FuncType funcType = new types.FuncType(paramTypes, funcReturnType);

        // Only declare function in symbol table during first pass
        if (firstPass) {
            tryDeclareFunction(name, funcType);

            // Skip function body during first pass
            expect(Token.Kind.OPEN_BRACE);
            skipFunctionBody();
            expect(Token.Kind.SEMICOLON);

            return new FunctionDeclaration(startLine, startChar, name, formals, returnType, null);
        } else {
            enterScope();
            for (Symbol param : formals) {
                try {
                    symbolTable.insert(param.name(), param.type());
                } catch (Error ignored) {
                }
            }
            FunctionBody body = funcBody();
            exitScope();
            return new FunctionDeclaration(startLine, startChar, name, formals, returnType, body);
        }
    }

    // computation = "main" {varDecl} {funcDecl} "{" statSeq "}" "."
    private Computation computation() {
        int startLine = lineNumber();
        int startChar = charPosition();

        expect(Token.Kind.MAIN);

        DeclarationList vars = new DeclarationList(startLine, startChar);
        DeclarationList funcs = new DeclarationList(startLine, startChar);

        // deal with varDecl
        while (have(NonTerminal.VAR_DECL)) {
            vars.add(varDecl());
        }

        // PASS 1: Forward declare all functions (for mutual recursion support)
        // Store current token position BEFORE parsing functions and set firstPass =
        // true
        savedTokenIndex = scanner.getCurrentTokenIndex();
        firstPass = true;

        while (have(NonTerminal.FUNC_DECL)) {
            funcDecl();
        }

        // PASS 2: Parse function bodies (signatures already declared)
        // Reset scanner to saved token position and set firstPass = false
        scanner.resetToToken(savedTokenIndex - 1);
        currentToken = scanner.next();
        firstPass = false;

        while (have(NonTerminal.FUNC_DECL)) {
            funcs.add(funcDecl());
        }

        expect(Token.Kind.OPEN_BRACE);
        StatementSequence mainSeq = statSeq();
        expect(Token.Kind.CLOSE_BRACE);
        expect(Token.Kind.PERIOD);

        Symbol mainSymbol = new Symbol("main");
        return new Computation(startLine, startChar, mainSymbol, vars, funcs, mainSeq);
    }

    private void skipFunctionBody() {
        int braceCount = 1; // Already consumed the opening brace

        while (braceCount > 0) {
            Token token = currentToken;
            currentToken = scanner.next();

            if (token.kind() == Token.Kind.OPEN_BRACE) {
                braceCount++;
            } else if (token.kind() == Token.Kind.CLOSE_BRACE) {
                braceCount--;
            }
        }
    }
}