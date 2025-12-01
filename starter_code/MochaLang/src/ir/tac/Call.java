package ir.tac;

import java.util.ArrayList;
import java.util.List;
import mocha.Symbol;

/**
 * Call TAC instruction - represents a function call
 * Format: dest = call function(arg1, arg2, ...)
 */
public class Call extends TAC {
    private Variable dest;
    private Symbol function;
    private List<Value> arguments;

    public Call(int id, Variable dest, Symbol function, List<Value> arguments) {
        super(id);
        this.dest = dest;
        this.function = function;
        this.arguments = arguments != null ? arguments : new ArrayList<>();
    }

    @Override
    public Value getDest() {
        return dest;
    }

    public void setDest(Variable dest) {
        this.dest = dest;
    }

    public Symbol getFunction() {
        return function;
    }

    public void setFunction(Symbol function) {
        this.function = function;
    }

    public List<Value> getArguments() {
        return arguments;
    }

    public void setArguments(List<Value> arguments) {
        this.arguments = arguments;
    }

    @Override
    public List<Value> getOperands() {
        return new ArrayList<>(arguments);
    }

    @Override
    public void setOperands(List<Value> operands) {
        this.arguments = new ArrayList<>(operands);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (dest != null) {
            sb.append(dest).append(" = ");
        }
        
        sb.append("call ").append(function.name()).append("(");
        
        for (int i = 0; i < arguments.size(); i++) {
            sb.append(arguments.get(i));
            if (i < arguments.size() - 1) {
                sb.append(", ");
            }
        }
        
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void accept(TACVisitor visitor) {
        visitor.visit(this);
    }
}

