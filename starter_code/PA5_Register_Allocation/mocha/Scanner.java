package mocha;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Scanner implements Iterator<Token> {

    private BufferedReader input;   // buffered reader to read file
    private boolean closed; // flag for whether reader is closed or not
    private String sourceFileName; // store source file name for reset functionality

    private int lineNum;    // current line number
    private int charPos;    // character offset on current line

    private String scan;    // current lexeme being scanned in
    private int nextChar;   // contains the next char (-1 == EOF)
    private Token.Kind lastTokenKind;
    private int currentTokenIndex; // Current token position for reset functionality

    // reader will be a FileReader over the source file
    public Scanner (String sourceFileName, Reader reader) {
        // TODO: initialize scanner
        this.sourceFileName = sourceFileName;
        this.input = new BufferedReader(reader);
        this.closed = false;
        this.lineNum = 1;
        this.charPos = 0;
        this.scan = "";
        this.lastTokenKind = null;
        this.nextChar = readChar();
        this.currentTokenIndex = 0;
    }

    public Scanner (Reader reader) {
        this("unknown", reader);
    }

    // Reset scanner to beginning of file (for two-pass parsing)
    public void reset() {
        try {
            // Close current input
            if (input != null) {
                input.close();
            }
            
            // Reopen the file
            java.io.FileReader reader = new java.io.FileReader(sourceFileName);
            this.input = new BufferedReader(reader);
            this.closed = false;
            this.lineNum = 1;
            this.charPos = 0;
            this.scan = "";
            this.lastTokenKind = null;
            this.nextChar = readChar();
            this.currentTokenIndex = 0;
        } catch (java.io.IOException e) {
            Error("Failed to reset scanner: " + e.getMessage(), e);
        }
    }

    // Reset scanner to a specific token index
    public void resetToToken(int targetTokenIndex) {
        // Reset to beginning first
        reset();
        
        // Skip tokens until we reach the target index
        while (currentTokenIndex < targetTokenIndex && hasNext()) {
            next();
        }
    }

    // Get current token index
    public int getCurrentTokenIndex() {
        return currentTokenIndex;
    }

    // Get source file name
    public String getSourceFileName() {
        return sourceFileName;
    }

    // signal an error message
    public void Error (String msg, Exception e) {
        System.err.println("Scanner: Line - " + lineNum + ", Char - " + charPos);
        if (e != null) {
            e.printStackTrace();
        }
        System.err.println(msg);
    }

    /*
     * helper function for reading a single char from input
     * can be used to catch and handle any IOExceptions,
     * advance the charPos or lineNum, etc.
     */
    private int readChar () {
        // TODO: implement
        try {
            int ch = input.read();
            if (ch == '\n') {
                lineNum++;
                charPos = 0;            
            } else if (ch != -1) {
                charPos++;
            }
            return ch;
        } catch (IOException e) {
            return -1;
        }
    }

    /*
     * function to query whether or not more characters can be read
     * depends on closed and nextChar
     */
    @Override
    public boolean hasNext () {
        return !closed;
    }

    // Helper method to increment token index and return token
    private Token returnToken(Token token) {
        currentTokenIndex++;
        return token;
    }

    /*
     *	returns next Token from input
     *
     *  invariants:
     *  1. call assumes that nextChar is already holding an unread character
     *  2. return leaves nextChar containing an untokenized character
     *  3. closes reader when emitting EOF
     */
    @Override
    public Token next () {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        // Skip whitespace
        while (nextChar != -1 && Character.isWhitespace(nextChar)) {
            nextChar = readChar();
        }

        if (nextChar == -1) {
            closed = true;
            try { input.close(); } catch (IOException e) {}
            Token token = Token.EOF(lineNum, charPos);
            lastTokenKind = token.kind();
            return returnToken(token);
        }

        int startLine = lineNum;
        int startChar = charPos;

        // comments
        if (nextChar == '/') {
            int tempNext = peekNextChar();
            if (tempNext == '/') {
                // single line
                nextChar = readChar();
                nextChar = readChar();
                while (nextChar != -1 && nextChar != '\n') {
                    nextChar = readChar();
                }
                return next();
            } else if (tempNext == '*') {
                // block comment
                nextChar = readChar();
                nextChar = readChar();
                boolean commentClosed = false;
                while (nextChar != -1) {
                    if (nextChar == '*') {
                        nextChar = readChar();
                        if (nextChar == '/') {
                            nextChar = readChar();
                            commentClosed = true;
                            break;
                        }
                    } else {
                        nextChar = readChar();
                    }
                }
                if (!commentClosed) {
                    Token token = Token.Error("/*", startLine, startChar);
                    lastTokenKind = token.kind();
                    return returnToken(token);
                }
                return next();
            }
        }
        
        if (Character.isDigit(nextChar)) {
            Token token = handleNumber(startLine, startChar);
            lastTokenKind = token.kind();
            return returnToken(token);
        }
        
        // only treat '-' as part of number if previous token was NOT a number or identifier
        if (nextChar == '-' && Character.isDigit(peekNextChar())) {
            if (lastTokenKind == Token.Kind.INT_VAL || lastTokenKind == Token.Kind.FLOAT_VAL || 
                lastTokenKind == Token.Kind.IDENT) {
                // fall through to handleOperator
            } else {
                // negative number
                Token token = handleNumber(startLine, startChar);
                lastTokenKind = token.kind();
                return returnToken(token);
            }
        }

        if (Character.isLetter(nextChar)) {
            Token token = handleIdentifier(startLine, startChar);
            lastTokenKind = token.kind();
            return returnToken(token);
        }

        //need to allow ! for != match
        if (isSingleCharOperatorOrDelimiter((char) nextChar) || nextChar == '!') {
            Token token = handleOperator(startLine, startChar);
            lastTokenKind = token.kind();
            return returnToken(token);
        }
        
        String invalidRun = consumeInvalidRun();
        Token token = Token.Error(invalidRun, startLine, startChar);
        lastTokenKind = token.kind();
        return returnToken(token);
    }

    // OPTIONAL: add any additional helper or convenience methods
    //           that you find make for a cleaner design
    //           (useful for handling special case Tokens)
    private int peekNextChar() {
        try {
            input.mark(1);
            int ch = input.read();
            input.reset();
            return ch;
        } catch (IOException e) {
            return -1;
        }
    }

    // consume longest run of invalid chars
    private String consumeInvalidRun() {
        String invalid = "";
        while (nextChar != -1
                && !Character.isWhitespace(nextChar)
                && !isSingleCharOperatorOrDelimiter((char) nextChar)
                && !Character.isLetterOrDigit(nextChar)) {
            invalid += (char) nextChar;
            nextChar = readChar();
        }
        return invalid;
    }

    private boolean isTwoCharOperator(String op) {
        return op.equals("==") || op.equals("!=") || op.equals("<=") || 
               op.equals(">=") || op.equals("+=") || op.equals("-=") || 
               op.equals("*=") || op.equals("/=") || op.equals("%=") || 
               op.equals("^=") || op.equals("++") || op.equals("--");
    }

    // Helper: check if a char is a valid single-character operator or delimiter
    private boolean isSingleCharOperatorOrDelimiter(char c) {
        switch (c) {
            case '^':
            case '*':
            case '/':
            case '%':
            case '+':
            case '-':
            case '<':
            case '>':
            case '=':
            case '(':
            case ')':
            case '{':
            case '}':
            case '[':
            case ']':
            case ',':
            case ':': 
            case ';':
            case '.':
                return true;
            default:
                return false;
        }
    }

    private Token handleNumber(int startLine, int startChar) {
        String lexeme = "";
        
        if (nextChar == '-') {
            lexeme += (char) nextChar;
            nextChar = readChar();
        }
        
        while (nextChar != -1 && Character.isDigit(nextChar)) {
            lexeme += (char) nextChar;
            nextChar = readChar();
        }
        
        if (nextChar == '.') {
            lexeme += (char) nextChar;
            nextChar = readChar();

            // must have digit after decimal point
            if (!Character.isDigit(nextChar)) {
                String invalidPart = consumeInvalidRun();
                return Token.Error(lexeme + invalidPart, startLine, startChar);
            }
            
            while (nextChar != -1 && Character.isDigit(nextChar)) {
                lexeme += (char) nextChar;;
                nextChar = readChar();
            }
            return Token.FloatVal(lexeme, startLine, startChar);
        }
        return Token.IntVal(lexeme, startLine, startChar);        
    }

    private Token handleIdentifier(int startLine, int startChar) {
        String lexeme = "";
        
        while (nextChar != -1 && (Character.isLetterOrDigit(nextChar) || nextChar == '_')) {
            lexeme += (char) nextChar;
            nextChar = readChar();
        }
        
        return new Token(lexeme, startLine, startChar);
    }

    private Token handleOperator(int startLine, int startChar) {
        String lexeme = "";
        
        lexeme += (char) nextChar;
        nextChar = readChar();
        
        if (nextChar != -1) {
            String twoCharOp = lexeme + (char) nextChar;
            if (isTwoCharOperator(twoCharOp)) {
                lexeme += (char) nextChar;
                nextChar = readChar();
                return Token.Operator(lexeme, startLine, startChar);
            }
        }
        
        if (isSingleCharOperatorOrDelimiter(lexeme.charAt(0))) {
            return Token.Operator(lexeme, startLine, startChar);
        } else {
            String invalidRun = lexeme + consumeInvalidRun();
            return Token.Error(invalidRun, startLine, startChar);
        }
    }
}
