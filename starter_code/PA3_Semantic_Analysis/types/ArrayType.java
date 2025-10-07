package types;

import java.util.ArrayList;
import java.util.List;

public class ArrayType extends Type {

    private final Type baseType;
    private final List<Integer> dimensions; // sizes per dimension, outermost first

    public ArrayType(Type baseType) {
        this.baseType = baseType;
        this.dimensions = new ArrayList<>();
    }

    public ArrayType(Type baseType, List<Integer> dimensions) {
        this.baseType = baseType;
        this.dimensions = new ArrayList<>(dimensions);
    }

    public Type getBaseType() {
        return baseType;
    }

    public List<Integer> getDimensions() {
        return dimensions;
    }

    @Override
    public String toString() {
        return "ArrayType(" + baseType + ", dims=" + dimensions + ")";
    }

    @Override
    public boolean equivalent(Type that) {
        if (that instanceof ArrayType) {
            ArrayType other = (ArrayType) that;
            // Arrays are equivalent if base types are equivalent and they have the same number of dimensions.
            // Ignore specific declared sizes for equivalence so that int[] matches int[4].
            return this.baseType.equivalent(other.baseType)
                && this.dimensions.size() == other.dimensions.size();
        }
        return false;
    }

    @Override
    public Type index(Type that) {
        if (!(that instanceof IntType)) {
            return new ErrorType("Array index must be int, not " + that + ".");
        }
        if (dimensions.isEmpty()) {
            return new ErrorType("Cannot index non-array type " + baseType + ".");
        }
        if (dimensions.size() == 1) {
            return baseType;
        }
        // Preserve unknown sizes (-1) and remaining dims
        List<Integer> rest = new ArrayList<>(dimensions.subList(1, dimensions.size()));
        return new ArrayType(baseType, rest);
    }
}
