package types;

public class AddressType extends Type {

    private Type baseType;

    public AddressType(Type baseType) {
        this.baseType = baseType;
    }

    public Type getBaseType() {
        return baseType;
    }

    @Override
    public String toString() {
        return "AddressType(" + baseType + ")";
    }

    @Override
    public boolean equivalent(Type that) {
        if (that instanceof AddressType) {
            AddressType other = (AddressType) that;
            return this.baseType.equivalent(other.baseType);
        }
        return false;
    }

    @Override
    public Type deref() {
        return baseType;
    }

}

