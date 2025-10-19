package ir.tac;

public class Literal implements Value{

    private Expression val;

    @Override
    public void accept(TACVisitor visitor) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'accept'");
    }
    
    @Override
    public String toString () {
        if (val instanceof ast.BoolLiteral) {
            return ((ast.BoolLiteral) val).value().toString();
        }
        else if (val instanceof ast.IntegerLiteral) {
            return ((ast.IntegerLiteral) val).value().toString();
        }
        else if (val instanceof ast.FloatLiteral) {
            return ((ast.FloatLiteral) val).value().toString();
        }
        return "LiteralValueError";
}
