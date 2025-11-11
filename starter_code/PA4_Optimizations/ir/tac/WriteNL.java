package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class WriteNL extends TAC {
    
    public WriteNL(int id) {
        super(id);
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "writeNL";
    }
}

