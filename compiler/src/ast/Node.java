package ast;

import mocha.Token;
import mocha.Symbol;

public abstract class Node implements Visitable {

    private int lineNum;
    private int charPos;

    protected Node (int lineNum, int charPos) {
        this.lineNum = lineNum;
        this.charPos = charPos;
    }

    public int lineNumber () {
        return lineNum;
    }

    public int charPosition () {
        return charPos;
    }

    public String getClassInfo () {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString () {
        return this.getClass().getSimpleName();
    }

    // Some factory methods for convenience
    public static Statement newAssignment (int lineNum, int charPos, Expression dest, Token assignOp, Expression src) {
        return new Assignment(lineNum, charPos, dest, assignOp, src);
    }

    public static Expression newExpression (Expression leftSide, Token op, Expression rightSide) {
        Node leftNode = (Node) leftSide;
        switch (op.kind()) {
            case ADD:
                return new Addition(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            case SUB:
                return new Subtraction(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            case MUL:
                return new Multiplication(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            case DIV:
                return new Division(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            case POW:
                return new Power(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            case MOD:
                return new Modulo(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            case AND:
                return new LogicalAnd(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            case OR:
                return new LogicalOr(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            case EQUAL_TO:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_EQUAL:
            case GREATER_EQUAL:
            case GREATER_THAN:
                return new Relation(leftNode.lineNumber(), leftNode.charPosition(), leftSide, op, rightSide);
            default:
                throw new RuntimeException("Unknown operator: " + op.kind());
        }
    }

    public static Expression newLiteral (Token tok) {
        switch (tok.kind()) {
            case INT_VAL:
                return new IntegerLiteral(tok);
            case FLOAT_VAL:
                return new FloatLiteral(tok);
            case TRUE:
            case FALSE:
                return new BoolLiteral(tok);
            default:
                throw new RuntimeException("Unknown literal token: " + tok.kind());
        }
    }
}
