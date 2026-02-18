package ir.tac;

public class Immediate implements Value {
    private Object value; // Can be Integer, Float, or Boolean
    
    public Immediate(Object value) {
        this.value = value;
    }
    
    public Object getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return "#" + value.toString();
    }
}

