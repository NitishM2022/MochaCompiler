package ir.tac;

import java.util.ArrayList;
import java.util.List;

public abstract class TAC implements Visitable{
    
    private int id; // instruction id

    private boolean eliminated; // if this instruction is not needed by any optimization, 
                                // note: do not physically remove instructions

    protected TAC(int id) {
        this.id = id;
        this.eliminated = false;

        // saving code position will be helpful in debugging
    } 
    
    public int getId() {
        return id;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public Value getDest() {
        return null;
    }

    public void setDest(Value dest) {
        // Default: do nothing
    }
    
    public List<Value> getOperands() {
        return new ArrayList<>();
    }
    
    public void setOperands(List<Value> operands) {
        // Default: do nothing
    }
    
    public abstract String toString();
}
