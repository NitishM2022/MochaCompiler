package ir.tac;

import java.util.ArrayList;
import java.util.List;
import ir.cfg.BasicBlock;

public class Bra extends TAC {
    private BasicBlock target;
    
    public Bra(int id, BasicBlock target) {
        super(id);
        this.target = target;
    }
    
    public BasicBlock getTarget() {
        return target;
    }
    
    public void setTarget(BasicBlock target) {
        this.target = target;
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        return "bra BB" + target.getNum();
    }
}

