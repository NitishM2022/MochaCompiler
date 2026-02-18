package ir.tac;

import mocha.Symbol;
import java.util.Objects;

public class Variable implements Value {
    private Symbol sym;
    private int version;
    private boolean isTemp;
    private int tempIndex;

    public Variable(Symbol sym) {
        this(sym, 0, false, -1);
    }

    public Variable(Symbol sym, int version) {
        this(sym, version, false, -1);
    }

    public Variable(Symbol sym, boolean isTemp) {
        this(sym, 0, isTemp, -1);
    }

    public Variable(Symbol sym, boolean isTemp, int tempIndex) {
        this(sym, 0, isTemp, tempIndex);
    }

    private Variable(Symbol sym, int version, boolean isTemp, int tempIndex) {
        this.sym = sym;
        this.version = version;
        this.isTemp = isTemp;
        this.tempIndex = tempIndex;
    }

    public Symbol getSymbol() {
        return sym;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isTemp() {
        return isTemp;
    }

    public int getTempIndex() {
        return tempIndex;
    }

    public void setTempIndex(int tempIndex) {
        this.tempIndex = tempIndex;
    }

    public Variable withVersion(int newVersion) {
        return new Variable(sym, newVersion, isTemp, tempIndex);
    }

    @Override
    public String toString() {
        if (isTemp) {
            return "t" + tempIndex;
        } else if (version == -1) {
            return sym.name();
        } else {
            return sym.name() + "_" + version;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Variable))
            return false;
        Variable other = (Variable) obj;

        // 1. Check if both are temps or both are variables
        if (this.isTemp != other.isTemp) {
            return false;
        }

        // 2. If Temps: compare index
        if (this.isTemp) {
            return this.tempIndex == other.tempIndex;
        }

        // 3. If Variables: compare Version AND Symbol Name (String)
        // We use sym.name().equals() to handle different Symbol objects for the same variable
        return this.version == other.version &&
               (this.sym == null ? other.sym == null : this.sym.name().equals(other.sym.name()));
    }

    @Override
    public int hashCode() {
        if (isTemp) {
            return Objects.hash(isTemp, tempIndex);
        }
        // Hash based on the name string, not the Symbol object identity
        return Objects.hash(isTemp, version, (sym == null ? 0 : sym.name()));
    }
}