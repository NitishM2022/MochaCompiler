package mocha;

// import java.io.BufferedReader;
import java.io.*;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Scanner implements Iterator<Token> {

    private BufferedReader input;   // buffered reader to read file
    private boolean closed; // flag for whether reader is closed or not

    private int lineNum;    // current line number
    private int charPos;    // character offset on current line

    private String scan;    // current lexeme being scanned in
    private int cache;
    private int nextChar;   // contains the next char (-1 == EOF)

    // reader will be a FileReader over the source file
    public Scanner (String sourceFileName, Reader reader) {
        // TODO: initialize scanner
        input = new BufferedReader(reader);
        // System.out.println("Input: "+sourceFileName);
        closed = false;
        lineNum = 1;
        charPos = 1;

        try{
            nextChar = input.read();
        }catch(IOException e){
            e.printStackTrace();
        }
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
        int c = nextChar;
        //nextChar not EOF -> load new nextChar
        if (c != -1){
            try{
                nextChar = input.read();
            }catch (IOException e){
                e.printStackTrace();
            }
            charPos++;
            if (c == '\n'){
                charPos = 0;
                lineNum++;
            }
        }
        return c;
    }

    /*
     * function to query whether or not more characters can be read
     * depends on closed and nextChar
     */
    @Override
    public boolean hasNext () {
        if (nextChar == -1 && closed){
            return false;
        }
        return true;
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
        String lex = null;
        Token tk = null;
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        // int c = 0;

        while (Character.isWhitespace(nextChar)){
            readChar();
        }
        if (cache == '/' && nextChar == '/'){// for /* [abc] //
            cache = -1;
            while(nextChar != '\n'){
                readChar();
            }
            return next();
        }
        if (nextChar == -1){
            try{
                input.close();
            }catch (IOException e){
                e.printStackTrace();
            }
            closed = true;
            tk = Token.EOF(lineNum, charPos);
            return tk;
        }
        if (Character.isDigit(nextChar)){
            lex = numToken(Character.toString(nextChar));
        }
        else if (Character.isLetter(nextChar)){
            lex = idToken(nextChar);
        }else{
            lex = symToken(nextChar);
            if(lex == "//"){
                return next();
            }
        }
        // System.out.println("next() lex = "+lex);
        if (lex != null){
            tk = new Token(lex, lineNum, charPos);
        }
        return tk;
    }
    
    private String idToken(int i){
        readChar();
        String s = Character.toString(i);
        while (Character.isLetterOrDigit(nextChar) || nextChar == '_'){
            s += (char)nextChar;
            readChar();
        }
        return s;
    }

    private String numToken(String s){
        boolean isFloat = false;
        readChar();
        while (Character.isDigit(nextChar) || nextChar == '.'){
            if(nextChar == '.'){
                if(isFloat){
                    break;
                }else{
                    isFloat = true;
                }
            }
            s += (char)nextChar;
            readChar();
        }
        // if(s.charAt(s.length()-1) == '.'){
        //     //invalid float

        // }
        return s;
    }

    private String symToken(int i){
        String s = Character.toString(i);
        readChar();
        if("(){}[],.:;".indexOf(i)!= -1){
            return s;
        }
        else if("+-=^*/%<>!".indexOf(i)!= -1){
            if (i == '/'){
                if (nextChar == '*' || nextChar == '/'){
                    if (!commentReader()){
                        return "/*/";
                    }else{
                        return "//";
                    }
                }
            }
            if((nextChar == '=') ||
            (i == '+' && nextChar == '+')||
            (i == '-' && nextChar == '-'))
            {
                s += (char)nextChar;
                readChar();
            }else if (i == '-' && Character.isDigit(nextChar)){
                s = numToken("-"+Character.toString(nextChar));
            }
        }else{
            while((!Character.isLetterOrDigit(nextChar)) &&
            ("(){}[],.:;+-=^*/%<>".indexOf(nextChar) == -1)){
                s+= (char)nextChar;
                readChar();
            }
        }
        return s;
    }
    // OPTIONAL: add any additional helper or convenience methods
    //           that you find make for a cleaner design
    //           (useful for handling special case Tokens)

    // validate comment
    private boolean commentReader(){
        //[nextChar]
        // boolean done = false;
        int i = readChar(); // i = / | *
        if (i == '/'){
            while (i != '\n'){
                i = readChar();
            } 
            return true;
        }else if (i=='*'){//multiline [nextChar]
            i = readChar();
            while(nextChar != -1 && !(i=='*' && nextChar == '/')){
                // if /*[any]
                i = readChar();
            }
            if (i == '*' && nextChar == '/'){
                readChar(); // so nc set for next()
                return true;
            }else if (nextChar == -1 ){//eof or new comment
                cache = i;
                return false;
            }
        }
            System.out.println("commentReader invalid char");
            return false;
    }
}
