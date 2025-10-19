package ir.tac;

public abstract class Assign extends TAC{
    
    private Variable dest; // lhs
    private Value left; // operand_1 
    private Value right; // operand_2

    protected Assign(int id, Variable dest, Value left, Value right) {
        super(id);
        this.dest = dest;
        this.left = left;
        this.right = right;
    }
}
