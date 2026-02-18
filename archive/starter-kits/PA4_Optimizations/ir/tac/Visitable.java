package ir.tac;

public interface Visitable {
    
    public void accept(TACVisitor visitor);
}
