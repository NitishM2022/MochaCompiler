package types;

public class BoolType extends Type {
    
    @Override
    public Type assign(Type source) {
        if (source instanceof BoolType) {
            return this;
        }
        return new ErrorType("Cannot assign " + source + " to bool.");
    }

    @Override
    public Type and(Type that) {
        if (that instanceof BoolType) {
            return new BoolType();
        }
        return new ErrorType("Cannot perform logical and with " + that + ".");
    }

    @Override
    public Type or(Type that) {
        if (that instanceof BoolType) {
            return new BoolType();
        }
        return new ErrorType("Cannot perform logical or with " + that + ".");
    }

    @Override
    public Type not() {
        return new BoolType();
    }

    @Override
    public String toString() {
        return "bool";
    }
}
