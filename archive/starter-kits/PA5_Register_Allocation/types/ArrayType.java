package types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public Type getBaseElementType() {
        Type baseType = this;
        while (baseType instanceof ArrayType) {
            baseType = ((ArrayType) baseType).getElementType();
        }
        return baseType;
    }

    public List<Integer> getDimensions() {
        List<Integer> dims = new ArrayList<>();
        Type currentType = this;
        while (currentType instanceof ArrayType) {
            ArrayType at = (ArrayType) currentType;
            dims.add(at.getSize());
            currentType = at.getElementType();
        }
        return dims;
    }

    public int getAllocationSize() {
        int totalElements = 1;
        for (int dim : getDimensions()) {
            totalElements *= dim;
        }
        return 4 * totalElements;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        Type baseType = getBaseElementType();
        sb.append(baseType.toString());

        for (int size : getDimensions()) {
            sb.append(size < 0 ? "[]" : "[" + size + "]");
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
