package ast;

public class FunctionCallStatement extends Statement {
    
    private final FunctionCallExpression functionCall;
    
    public FunctionCallStatement(int lineNum, int charPos, FunctionCallExpression functionCall) {
        super(lineNum, charPos);
        this.functionCall = functionCall;
    }
    
    public FunctionCallExpression getFunctionCall() {
        return functionCall;
    }
    
    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}
