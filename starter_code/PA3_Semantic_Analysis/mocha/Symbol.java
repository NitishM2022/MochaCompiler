package mocha;

import types.Type;

public class Symbol {

    private String name;
    private Type type;
    private boolean isFunction;

    public Symbol (String name) {
        this.name = name;
        this.type = null;
        this.isFunction = false;
    }
    
    public Symbol (String name, Type type) {
        this.name = name;
        this.type = type;
        this.isFunction = false;
    }
    
    public Symbol (String name, Type type, boolean isFunction) {
        this.name = name;
        this.type = type;
        this.isFunction = isFunction;
    }
    
    public String name () {
        return name;
    }
    
    public Type type () {
        return type;
    }
    
    public boolean isFunction () {
        return isFunction;
    }
    
    public void setType(Type type) {
        this.type = type;
    }
    
    public void setIsFunction(boolean isFunction) {
        this.isFunction = isFunction;
    }

}
