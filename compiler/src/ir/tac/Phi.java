package ir.tac;

import ir.cfg.BasicBlock; // Import BasicBlock
import java.util.ArrayList;
import java.util.LinkedHashMap; // Use LinkedHashMap to maintain insertion order (good for debugging/determinism)
import java.util.List;
import java.util.Map;

public class Phi extends TAC {
    private Variable dest;
    // *** FIX: Store arguments as a map from Predecessor Block to Incoming Value ***
    private Map<BasicBlock, Value> arguments;

    public Phi(int id, Variable dest) {
        super(id);
        this.dest = dest;
        // Use LinkedHashMap to keep argument order consistent (based on predecessor processing)
        this.arguments = new LinkedHashMap<>();
    }

    @Override
    public Value getDest() {
        return dest;
    }

    public void setDest(Variable dest) {
        this.dest = dest;
    }

    // *** FIX: Method to add arguments associated with their predecessor ***
    public void addArgument(BasicBlock predecessor, Value value) {
        arguments.put(predecessor, value);
    }

    // *** FIX: Provide access to the argument map ***
    public Map<BasicBlock, Value> getArgs() {
        return arguments;
    }
    
    // *** Optional: If you still need a simple list of operand *values* for other passes ***
    @Override
    public List<Value> getOperands() {
        return new ArrayList<>(arguments.values());
    }
    
    @Override
    public void setOperands(List<Value> operands) {
        // Phi stores operands as a map with predecessors, so this doesn't apply directly
        // No-op to satisfy the interface
    }
    
    // *** Optional: If you need to replace the whole map (use with caution) ***
    public void setArgs(Map<BasicBlock, Value> newArgs) {
        this.arguments.clear();
        this.arguments.putAll(newArgs);
    }


    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(dest).append(" = phi [");
        boolean first = true;
        for (Map.Entry<BasicBlock, Value> entry : arguments.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("BB").append(entry.getKey().getNum()).append(": ").append(entry.getValue());
        }
        sb.append("]");
        return sb.toString();
    }
}