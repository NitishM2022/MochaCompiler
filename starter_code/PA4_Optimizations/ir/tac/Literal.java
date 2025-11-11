package ir.tac;

import ast.Expression;

public class Literal implements Value {
    private Expression val;
    
    public Literal(Expression val) {
        this.val = val;
    }
    
    public Expression getValue() {
        return val;
    }
    
    @Override
    public String toString() {
        if (val instanceof ast.BoolLiteral) {
            return Boolean.toString(((ast.BoolLiteral) val).getValue());
        } else if (val instanceof ast.IntegerLiteral) {
            return Integer.toString(((ast.IntegerLiteral) val).getValue());
        } else if (val instanceof ast.FloatLiteral) {
            return Float.toString(((ast.FloatLiteral) val).getValue());
        }
        return "LiteralValueError";
    }
}
