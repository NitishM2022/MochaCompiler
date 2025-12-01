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
        if (this == obj) return true;
        if (!(obj instanceof Variable)) return false;

        Variable other = (Variable) obj;

        // Temps compare only by tempIndex
        if (this.isTemp || other.isTemp) {
            return this.isTemp == other.isTemp &&
                   this.tempIndex == other.tempIndex;
        }

        // Non-temp variables: compare name + version
        String name1 = this.sym != null ? this.sym.name() : null;
        String name2 = other.sym != null ? other.sym.name() : null;

        return Objects.equals(name1, name2) &&
               this.version == other.version;
    }

    @Override
    public int hashCode() {
        if (isTemp) {
            return Objects.hash(true, tempIndex);
        }

        String name = sym != null ? sym.name() : null;
        return Objects.hash(false, name, version);
    }

}