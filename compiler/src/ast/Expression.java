package ast;

import types.Type;

public abstract class Expression extends Node {
    
    private Type type;
    private boolean isLValue = false;
    
    protected Expression(int lineNum, int charPos) {
        super(lineNum, charPos);
    }
    
    public Type getType() {
        return type;
    }
    
    public boolean isLValue() {
        return isLValue;
    }
    
    public void setType(Type type) {
        this.type = type;
    }
    
    public void setLValue(boolean isLValue) {
        this.isLValue = isLValue;
    }
}
