package mocha;

public class Token {

    public enum Kind {
        // boolean operators
        AND("and"),
        OR("or"),
        NOT("not"),

        // arithmetic operators
        POW("^"),

        MUL("*"),
        DIV("/"),
        MOD("%"),

        ADD("+"),
        SUB("-"),

        // relational operators
        EQUAL_TO("=="),
        NOT_EQUAL("!="),
        LESS_THAN("<"),
        LESS_EQUAL("<="),
        GREATER_EQUAL(">="),
        GREATER_THAN(">"),

        // assignment operators
        ASSIGN("="),
        ADD_ASSIGN("+="),
        SUB_ASSIGN("-="),
        MUL_ASSIGN("*="),
        DIV_ASSIGN("/="),
        MOD_ASSIGN("%="),
        POW_ASSIGN("^="),

        // unary increment/decrement
        UNI_INC("++"),
        UNI_DEC("--"),

        // primitive types
        VOID("void"),
        BOOL("bool"),
        INT("int"),
        FLOAT("float"),

        // boolean literals
        TRUE("true"),
        FALSE("false"),

        // region delimiters
        OPEN_PAREN("("),
        CLOSE_PAREN(")"),
        OPEN_BRACE("{"),
        CLOSE_BRACE("}"),
        OPEN_BRACKET("["),
        CLOSE_BRACKET("]"),

        // field/record delimiters
        COMMA(","),
        COLON(":"),
        SEMICOLON(";"),
        PERIOD("."),

        // control flow statements
        IF("if"),
        THEN("then"),
        ELSE("else"),
        FI("fi"),

        WHILE("while"),
        DO("do"),
        OD("od"),

        REPEAT("repeat"),
        UNTIL("until"),

        CALL("call"),
        RETURN("return"),

        // keywords
        MAIN("main"),
        FUNC("function"),

        // special cases
        INT_VAL(),
        FLOAT_VAL(),
        IDENT(),

        EOF(),

        ERROR();

        private String defaultLexeme;

        Kind () {
            defaultLexeme = "";
        }

        Kind (String lexeme) {
            defaultLexeme = lexeme;
        }

        public boolean hasStaticLexeme () {
            return defaultLexeme != null;
        }

        public String getLexeme(){
            return this.defaultLexeme;
        }
        // OPTIONAL: convenience function - boolean matches (String lexeme)
        //           to report whether a Token.Kind has the given lexeme
        //           may be useful

    }

    private int lineNum;
    private int charPos;
    Kind kind;  // package-private
    private String lexeme = "";


    // TODO: implement remaining factory functions for handling special cases (EOF below)

    public static Token EOF (int linePos, int charPos) {
        Token tok = new Token(linePos, charPos);
        tok.kind = Kind.EOF;
        return tok;
    }

    private Token (int lineNum, int charPos) {
        this.lineNum = lineNum;
        this.charPos = charPos;

        // no lexeme provide, signal error
        this.kind = Kind.ERROR;
        this.lexeme = "No Lexeme Given";
    }

    public Token (String lexeme, int lineNum, int charPos) {
        this.lineNum = lineNum;
        this.charPos = charPos;
        
        // TODO: based on the given lexeme determine and set the actual kind
        this.lexeme = lexeme;
        
        // if we don't match anything, signal error
        this.kind = this.kind();
        if ((this.kind.name()).equals("ERROR")){
            if(Character.isLetter(lexeme.charAt(0))){
                this.kind = Kind.IDENT;
            }else if (Character.isDigit(lexeme.charAt(0)) || lexeme.charAt(0)=='-'){
                int l = lexeme.indexOf('.');
                if(l==-1){
                    this.kind = Kind.INT_VAL;
                }else{
                    if(l != lexeme.length()-1){
                        this.kind = Kind.FLOAT_VAL;
                    }
                }
            }
        }
        if(lexeme == "/*/"){
            this.kind = Kind.ERROR;
        }
        // this.lexeme = "Unrecognized lexeme: " + lexeme;
        // System.out.println("TK lex: <"+lexeme+"> lineNum: "+lineNum+" charPos: "+charPos);
    }

    public int lineNumber () {
        return lineNum;
    }

    public int charPosition () {
        return charPos;
    }

    public String lexeme () {
        
        return this.lexeme;
    }

    public Kind kind () {
        Kind[] kList = Kind.values();
        for (Kind k : kList){
            if(k.getLexeme().equals(this.lexeme)){
                return k;
            }
        }
        return Kind.ERROR;
    }

    // TODO: function to query a token about its kind - boolean is (Token.Kind kind)

    // OPTIONAL: add any additional helper or convenience methods
    //           that you find make for a cleaner design

    @Override
    public String toString () {
        return "Line: " + lineNum + ", Char: " + charPos + ", Lexeme: " + lexeme;
    }
}
