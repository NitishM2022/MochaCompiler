package types;

public class ErrorType extends Type {

    private String message;

    public ErrorType() {
        this.message = "Error";
    }
    
    public ErrorType(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }
    
    @Override
    public String toString() {
        return "ErrorType(" + message + ")";
    }
    
    @Override
    public boolean equivalent(Type that) {
        return that instanceof ErrorType;
    }

}
