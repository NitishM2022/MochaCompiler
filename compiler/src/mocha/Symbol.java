package mocha;

import types.Type;

public class Symbol {

    private String name;
    private Type type;
    private boolean isFunction;

    // FP-relative offset for stack allocation (Locals and Parameters)
    private int fpOffset = 0;

    // GP-relative offset for global data allocation (Globals)
    private int globalOffset = 0;

    private boolean isParameter = false;
    private boolean hasStackSlot = false;
    private boolean isGlobal = false;

    public Symbol(String name) {
        this.name = name;
        this.type = null;
        this.isFunction = false;
    }

    public Symbol(String name, Type type) {
        this.name = name;
        this.type = type;
        this.isFunction = false;
    }

    public Symbol(String name, Type type, boolean isFunction) {
        this.name = name;
        this.type = type;
        this.isFunction = isFunction;
    }

    public String name() {
        return name;
    }

    public Type type() {
        return type;
    }

    public boolean isFunction() {
        return isFunction;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setIsFunction(boolean isFunction) {
        this.isFunction = isFunction;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public void setGlobal(boolean isGlobal) {
        this.isGlobal = isGlobal;
    }

    // Getters and Setters
    public int getFpOffset() {
        return fpOffset;
    }

    public void setFpOffset(int fpOffset) {
        this.fpOffset = fpOffset;
    }

    public int getGlobalOffset() {
        return globalOffset;
    }

    public void setGlobalOffset(int globalOffset) {
        this.globalOffset = globalOffset;
    }

    public boolean isParameter() {
        return isParameter;
    }

    public void setParameter(boolean isParameter) {
        this.isParameter = isParameter;
    }

    public boolean hasStackSlot() {
        return hasStackSlot;
    }

    public void setHasStackSlot(boolean hasStackSlot) {
        this.hasStackSlot = hasStackSlot;
    }

    @Override
    public String toString() {
        if (isGlobal) {
            return name + " (Global: " + globalOffset + "(R30))";
        } else if (hasStackSlot) {
            return name + " (Local: " + fpOffset + "(FP))";
        } else {
            return name;
        }
    }
}