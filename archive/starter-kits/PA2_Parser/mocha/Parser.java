package mocha;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class Parser {

    // Error Reporting ============================================================
    private StringBuilder errorBuffer = new StringBuilder();

    private String reportSyntaxError (NonTerminal nt) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() + ")[Expected a token from " + nt.name() + " but got " + currentToken.kind + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    private String reportSyntaxError (Token.Kind kind) {
        String message = "SyntaxError(" + lineNumber() + "," + charPosition() + ")[Expected " + kind + " but got " + currentToken.kind + ".]";
        errorBuffer.append(message + "\n");
        return message;
    }

    public String errorReport () {
        return errorBuffer.toString();
    }

    public boolean hasError () {
        return errorBuffer.length() != 0;
    }

    private class QuitParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public QuitParseException (String errorMessage) {
            super(errorMessage);
        }
    }

    private int lineNumber () {
        return currentToken.lineNumber();
    }

    private int charPosition () {
        return currentToken.charPosition();
    }

// Parser ============================================================
    private Scanner scanner;
    private Token currentToken;

    private BufferedReader reader;
    private StringTokenizer st;

    // TODO: add maps from Token IDENT to int/float/bool

    public Parser (Scanner scanner, InputStream in) {
        this.scanner = scanner;
        currentToken = this.scanner.next();

        reader = new BufferedReader(new InputStreamReader(in));
        st = null;
    }

    public void parse () {
        try {
            computation();
        }
        catch (QuitParseException q) {
            // too verbose
            // errorBuffer.append("SyntaxError(" + lineNumber() + "," + charPosition() + ")");
            // errorBuffer.append("[Could not complete parsing.]");
        }
    }

// Helper Methods =============================================================
    private boolean have (Token.Kind kind) {
        return currentToken.is(kind);
    }

    private boolean have (NonTerminal nt) {
        return nt.firstSet().contains(currentToken.kind);
    }

    private boolean accept (Token.Kind kind) {
        if (have(kind)) {
            try {
                currentToken = scanner.next();
            }
            catch (NoSuchElementException e) {
                if (!kind.equals(Token.Kind.EOF)) {
                    String errorMessage = reportSyntaxError(kind);
                    throw new QuitParseException(errorMessage);
                }
            }
            return true;
        }
        return false;
    }

    private boolean accept (NonTerminal nt) {
        if (have(nt)) {
            currentToken = scanner.next();
            return true;
        }
        return false;
    }

    private boolean expect (Token.Kind kind) {
        if (accept(kind)) {
            return true;
        }
        String errorMessage = reportSyntaxError(kind);
        throw new QuitParseException(errorMessage);
    }

    private boolean expect (NonTerminal nt) {
        if (accept(nt)) {
            return true;
        }
        String errorMessage = reportSyntaxError(nt);
        throw new QuitParseException(errorMessage);
    }

    private Token expectRetrieve (Token.Kind kind) {
        Token tok = currentToken;
        if (accept(kind)) {
            return tok;
        }
        String errorMessage = reportSyntaxError(kind);
        throw new QuitParseException(errorMessage);
    }

    private Token expectRetrieve (NonTerminal nt) {
        Token tok = currentToken;
        if (accept(nt)) {
            return tok;
        }
        String errorMessage = reportSyntaxError(nt);
        throw new QuitParseException(errorMessage);
    }

// Grammar Rules ==============================================================

    // function for matching rule that only expects nonterminal's FIRST set
    private Token matchNonTerminal (NonTerminal nt) {
        return expectRetrieve(nt);
    }

    // TODO: implement operators and type grammar rules

    // literal = boolLit | integerLit | floatLit
    private Token literal () {
        return matchNonTerminal(NonTerminal.LITERAL);
    }

    // designator = ident { "[" relExpr "]" }
    private void designator () {
        Token ident = expectRetrieve(Token.Kind.IDENT);
        
        while (accept(Token.Kind.OPEN_BRACKET)) {
            relExpr();
            expect(Token.Kind.CLOSE_BRACKET);
        }
    }

    // groupExpr = literal | designator | "not" relExpr | relation | funcCall
    private void groupExpr () {
        if (have(NonTerminal.LITERAL)) {
            literal();
        } else if (have(NonTerminal.DESIGNATOR)) {
            designator();
        } else if (have(Token.Kind.NOT)) {
            expect(Token.Kind.NOT);
            relExpr();
        } else if (have(NonTerminal.RELATION)) {
            relation();
        } else if (have(NonTerminal.FUNC_CALL)) {
            funcCall();
        } else {
            String errorMessage = reportSyntaxError(NonTerminal.GROUP_EXPR);
            throw new QuitParseException(errorMessage);
        }
    }

    // powExpr = groupExpr { powOp groupExpr }
    private void powExpr () {
        groupExpr();
        while (accept(NonTerminal.POW_OP)) {
            groupExpr();
        }
    }

    // multExpr = powExpr { multOp powExpr }
    private void multExpr () {
        powExpr();
        while (accept(NonTerminal.MUL_OP)) {
            powExpr();
        }
    }

    // addExpr = multExpr { addOp multExpr }
    private void addExpr () {
        multExpr();
        while (accept(NonTerminal.ADD_OP)) {
            multExpr();
        }
    }

    // relExpr = addExpr { relOp addExpr }
    private void relExpr () {
        addExpr();
        while (accept(NonTerminal.REL_OP)) {
            addExpr();
        }
    }

    // relation = "(" relExpr ")"
    private void relation () {
        expect(Token.Kind.OPEN_PAREN);
        relExpr();
        expect(Token.Kind.CLOSE_PAREN);
    }

    // assign = designator ( ( assignOp relExpr ) | unaryOp )
    private void assign () {
        designator();
        
        if (accept(NonTerminal.ASSIGN_OP)) {
            relExpr();
        } else if (accept(NonTerminal.UNARY_OP)) {
        } else {
            String errorMessage = reportSyntaxError(NonTerminal.ASSIGN);
            throw new QuitParseException(errorMessage);
        }
    }

    // funcCall = "call" ident "(" [ relExpr { "," relExpr } ] ")"
    private void funcCall () {
        expect(Token.Kind.CALL);
        expect(Token.Kind.IDENT);
        expect(Token.Kind.OPEN_PAREN);
        
        if (have(NonTerminal.REL_EXPR)) {
            relExpr();
            while (accept(Token.Kind.COMMA)) {
                relExpr();
            }
        }
        
        expect(Token.Kind.CLOSE_PAREN);
    }

    // ifStat = "if" relation "then" statSeq [ "else" statSeq ] "fi"
    private void ifStat () {
        expect(Token.Kind.IF);
        relation();
        expect(Token.Kind.THEN);
        statSeq();
        
        if (accept(Token.Kind.ELSE)) {
            statSeq();
        }
        
        expect(Token.Kind.FI);
    }

    // whileStat = "while" relation "do" statSeq "od"
    private void whileStat () {
        expect(Token.Kind.WHILE);
        relation();
        expect(Token.Kind.DO);
        statSeq();
        expect(Token.Kind.OD);
    }

    // repeatStat = "repeat" statSeq "until" relation
    private void repeatStat () {
        expect(Token.Kind.REPEAT);
        statSeq();
        expect(Token.Kind.UNTIL);
        relation();
    }

    // returnStat = "return" [ relExpr ]
    private void returnStat () {
        expect(Token.Kind.RETURN);
        
        if (have(NonTerminal.REL_EXPR)) {
            relExpr();
        }
    }

    // statement = assign | funcCall | ifStat | whileStat | repeatStat | returnStat
    private void statement () {
        if (have(NonTerminal.ASSIGN)) {
            assign();
        } else if (have(NonTerminal.FUNC_CALL)) {
            funcCall();
        } else if (have(NonTerminal.IF_STAT)) {
            ifStat();
        } else if (have(NonTerminal.WHILE_STAT)) {
            whileStat();
        } else if (have(NonTerminal.REPEAT_STAT)) {
            repeatStat();
        } else if (have(NonTerminal.RETURN_STAT)) {
            returnStat();
        } else {
            String errorMessage = reportSyntaxError(NonTerminal.STATEMENT);
            throw new QuitParseException(errorMessage);
        }
    }

    // statSeq = statement ";" { statement ";" }
    private void statSeq () {
        statement();
        expect(Token.Kind.SEMICOLON);
        
        while (have(NonTerminal.STATEMENT)) {
            statement();
            expect(Token.Kind.SEMICOLON);
        }
    }

    // typeDecl = type { "[" integerLit "]" }
    private void typeDecl () {
        if (!accept(NonTerminal.TYPE_DECL)) {
            String errorMessage = reportSyntaxError(NonTerminal.TYPE_DECL);
            throw new QuitParseException(errorMessage);
        }
        
        while (accept(Token.Kind.OPEN_BRACKET)) {
            expect(Token.Kind.INT_VAL);
            expect(Token.Kind.CLOSE_BRACKET);
        }
    }

    // varDecl = typeDecl ident { "," ident } ";"
    private void varDecl () {
        typeDecl();
        expect(Token.Kind.IDENT);
        
        while (accept(Token.Kind.COMMA)) {
            expect(Token.Kind.IDENT);
        }
        
        expect(Token.Kind.SEMICOLON);
    }

    // paramType = type { "[" "]" }
    private void paramType () {
        if (!accept(NonTerminal.PARAM_TYPE)) {
            String errorMessage = reportSyntaxError(NonTerminal.PARAM_TYPE);
            throw new QuitParseException(errorMessage);
        }
        
        while (accept(Token.Kind.OPEN_BRACKET)) {
            expect(Token.Kind.CLOSE_BRACKET);
        }
    }

    // paramDecl = paramType ident
    private void paramDecl () {
        paramType();
        expect(Token.Kind.IDENT);
    }

    // formalParam = "(" [ paramDecl { "," paramDecl } ] ")"
    private void formalParam () {
        expect(Token.Kind.OPEN_PAREN);
        
        if (have(NonTerminal.PARAM_DECL)) {
            paramDecl();
            while (accept(Token.Kind.COMMA)) {
                paramDecl();
            }
        }
        
        expect(Token.Kind.CLOSE_PAREN);
    }

    // funcBody = "{" { varDecl } statSeq "}" ";"
    private void funcBody () {
        expect(Token.Kind.OPEN_BRACE);
        
        while (have(NonTerminal.VAR_DECL)) {
            varDecl();
        }
        
        statSeq();
        expect(Token.Kind.CLOSE_BRACE);
        expect(Token.Kind.SEMICOLON);
    }

    // funcDecl = "function" ident formalParam ":" ( "void" | type ) funcBody
    private void funcDecl () {
        expect(Token.Kind.FUNC);
        expect(Token.Kind.IDENT);
        formalParam();
        expect(Token.Kind.COLON);
        
        if (have(Token.Kind.VOID)) {
            expect(Token.Kind.VOID);
        } else if (have(Token.Kind.BOOL) || have(Token.Kind.INT) || have(Token.Kind.FLOAT)) {
            typeDecl();
        } else {
            String errorMessage = reportSyntaxError(NonTerminal.TYPE_DECL);
            throw new QuitParseException(errorMessage);
        }
        
        funcBody();
    }

    // computation = "main" {varDecl} {funcDecl} "{" statSeq "}" "."
    private void computation () {
        
        expect(Token.Kind.MAIN);
        
        // deal with varDecl
        while (have(NonTerminal.VAR_DECL)) {
            varDecl();
        }

        while (have(NonTerminal.FUNC_DECL)) {
            funcDecl();
        }

        expect(Token.Kind.OPEN_BRACE);
        statSeq();
        expect(Token.Kind.CLOSE_BRACE);
        expect(Token.Kind.PERIOD);       
    }
}
