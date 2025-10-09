package ast;

public abstract class Statement extends Node implements Visitable {
    
    protected Statement(int lineNum, int charPos) {
        super(lineNum, charPos);
    }
}
