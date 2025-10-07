package types;

public class IntType extends Type {

    @Override
    public Type assign(Type source) {
        if (source instanceof IntType) {
            return this;
        }
        return new ErrorType("Cannot assign " + source + " to int.");
    }

    @Override
    public Type add(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot add " + that + " to int.");
    }

    @Override
    public Type sub(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot subtract " + that + " from int.");
    }

    @Override
    public Type mul(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot multiply int with " + that + ".");
    }

    @Override
    public Type div(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot divide int by " + that + ".");
    }

    @Override
    public Type compare(Type that) {
        if (that instanceof IntType) {
            return new BoolType();
        }
        return new ErrorType("Cannot compare int with " + that + ".");
    }

    @Override
    public Type power(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot raise int to the power of " + that + ".");
    }

    @Override
    public Type mod(Type that) {
        if (that instanceof IntType) {
            return new IntType();
        }
        return new ErrorType("Cannot perform modulo int % " + that + ".");
    }

    @Override
    public String toString() {
        return "int";
    }
}
