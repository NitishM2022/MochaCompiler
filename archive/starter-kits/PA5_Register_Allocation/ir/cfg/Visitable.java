package ir.cfg;

public interface Visitable {
    void accept(CFGVisitor visitor);
}
