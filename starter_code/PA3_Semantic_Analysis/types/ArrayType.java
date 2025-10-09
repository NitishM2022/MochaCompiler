package types;

public class ArrayType extends Type {

    private final Type elementType; // type of each element; can be ArrayType itself
    private final int size;         // -1 if unknown

    public ArrayType(Type elementType, int size) {
        this.elementType = elementType;
        this.size = size;
    }

    public Type getElementType() {
        return elementType;
    }

    public int getSize() {
        return size;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Find the base type (non-array type)
        Type baseType = this;
        int depth = 0;
        while (baseType instanceof ArrayType) {
            baseType = ((ArrayType) baseType).getElementType();
            depth++;
        }
        
        // Start with base type
        sb.append(baseType.toString());
        
        // Add dimensions from outermost to innermost
        Type currentType = this;
        for (int i = 0; i < depth; i++) {
            ArrayType at = (ArrayType) currentType;
            int size = at.getSize();
            sb.append(size < 0 ? "[]" : "[" + size + "]");
            currentType = at.getElementType();
        }
        
        return sb.toString();
    }

    @Override
    public boolean equivalent(Type that) {
        if (!(that instanceof ArrayType)) return false;
        ArrayType other = (ArrayType) that;
        // element types must be equivalent; ignore exact size
        return this.elementType.equivalent(other.elementType);
    }

    @Override
    public Type index(Type that) {
        if (!(that instanceof IntType)) {
            return new ErrorType("Cannot index " + this + " with " + that + ".");
        }
        // Indexing reduces one level of array
        return elementType; // elementType may itself be an ArrayType for multi-dim arrays
    }

    @Override
    public Type assign(Type source) {
        if (source instanceof ArrayType) {
            ArrayType other = (ArrayType) source;
            if (this.equivalent(other)) {
                return this;
            }
        }
        return new ErrorType("Cannot assign " + source + " to " + this + ".");
    }
}
