package ir.tac;

import java.util.ArrayList;
import java.util.List;

/**
 * LoadFP TAC instruction - loads a value from an FP-relative stack location.
 * Used for loading parameters and locals from the stack.
 * Format: dest = load [FP + offset]
 */
public class LoadFP extends TAC {
    private Variable dest;
    private int fpOffset;  // Offset from Frame Pointer (positive for params, negative for locals)
    
    public LoadFP(int id, Variable dest, int fpOffset) {
        super(id);
        this.dest = dest;
        this.fpOffset = fpOffset;
    }
    
    @Override
    public Value getDest() {
        return dest;
    }
    
    public void setDest(Variable dest) {
        this.dest = dest;
    }
    
    public int getFpOffset() {
        return fpOffset;
    }
    
    public void setFpOffset(int fpOffset) {
        this.fpOffset = fpOffset;
    }
    
    @Override
    public List<Value> getOperands() {
        // No operands - the offset is an immediate value
        return new ArrayList<>();
    }
    
    @Override
    public void setOperands(List<Value> operands) {
        // No operands to set
    }
    
    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
    
    @Override
    public String toString() {
        String sign = fpOffset >= 0 ? "+" : "";
        return dest + " = [FP" + sign + fpOffset + "]";
    }
}


