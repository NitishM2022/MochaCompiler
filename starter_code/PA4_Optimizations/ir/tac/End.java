package ir.tac;

import java.util.ArrayList;
import java.util.List;

public class End extends TAC {
    
    public End(int id) {
        super(id);
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "end";
    }
}

