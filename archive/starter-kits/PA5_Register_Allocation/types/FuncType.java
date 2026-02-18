package types;

public class FuncType extends Type {

    private TypeList params;
    private Type returnType;

    public FuncType(TypeList params, Type returnType) {
        this.params = params;
        this.returnType = returnType;
    }
    
    public TypeList getParams() {
        return params;
    }
    
    public Type getReturnType() {
        return returnType;
    }
    
    @Override
    public String toString() {
        return "FuncType(" + params + " -> " + returnType + ")";
    }
    
    @Override
    public boolean equivalent(Type that) {
        if (that instanceof FuncType) {
            FuncType other = (FuncType) that;
            return this.returnType.equivalent(other.returnType) && 
                   this.params.equivalent(other.params);
        }
        return false;
    }

}
