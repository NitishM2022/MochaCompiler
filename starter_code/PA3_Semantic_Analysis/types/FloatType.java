package types;

public class FloatType extends Type {

    @Override
    public Type assign(Type source) {
        if (source instanceof FloatType) {
            return this;
        }
        return new ErrorType("Cannot assign " + source + " to float.");
    }

    @Override
    public Type add(Type that) {
        if (that instanceof FloatType) {
            return new FloatType();
        }
        return new ErrorType("Cannot add " + that + " to float.");
    }

    @Override
    public Type sub(Type that) {
        if (that instanceof FloatType) {
            return new FloatType();
        }
        return new ErrorType("Cannot subtract " + that + " from float.");
    }

    @Override
    public Type mul(Type that) {
        if (that instanceof FloatType) {
            return new FloatType();
        }
        return new ErrorType("Cannot multiply float with " + that + ".");
    }

    @Override
    public Type div(Type that) {
        if (that instanceof FloatType) {
            return new FloatType();
        }
        return new ErrorType("Cannot divide float by " + that + ".");
    }

    @Override
    public Type compare(Type that) {
        if (that instanceof FloatType) {
            return new BoolType();
        }
        return new ErrorType("Cannot compare float with " + that + ".");
    }

    @Override
    public Type power(Type that) {
        if (that instanceof FloatType) {
            return new FloatType();
        }
        return new ErrorType("Cannot raise float to the power of " + that + ".");
    }

    @Override
    public String toString() {
        return "float";
    }
}
