package types;

public class IntType extends Type {

    @Override
    public Type assign(Type source) {
        if (source instanceof IntType) {
            return this;
        }
        return new ErrorType("Cannot assign " + source + " to " + this + ".");
    }

    @Override
    public Type add(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot add " + this + " to " + that + ".");
    }

    @Override
    public Type sub(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot subtract " + that + " from " + this + ".");
    }

    @Override
    public Type mul(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot multiply " + this + " with " + that + ".");
    }

    @Override
    public Type div(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot divide " + this + " by " + that + ".");
    }

    @Override
    public Type compare(Type that) {
        if (that instanceof IntType) {
            return new BoolType();
        }
        return new ErrorType("Cannot compare " + this + " with " + that + ".");
    }

    @Override
    public Type power(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot raise " + this + " to " + that + ".");
    }

    @Override
    public Type mod(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot modulo " + this + " by " + that + ".");
    }

    @Override
    public String toString() {
        return "int";
    }
}
