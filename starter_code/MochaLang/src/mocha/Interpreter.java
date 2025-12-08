package mocha;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import ast.*;
import types.*;

public class Interpreter implements NodeVisitor {
    private SymbolTable symbolTable;
    private Map<String, Object> memory;
    private Scanner inputScanner;
    private Stack<Object> valueStack;
        
    public Interpreter(SymbolTable symbolTable, InputStream input) {
        this.symbolTable = symbolTable;
        this.memory = new HashMap<>();
        this.inputScanner = new Scanner(input);
        this.valueStack = new Stack<>();
    }
    
    // Helper methods to work with recursive ArrayType structure
    private List<Integer> getArrayDimensions(ArrayType arrayType) {
        List<Integer> dims = new ArrayList<>();
        Type currentType = arrayType;
        
        while (currentType instanceof ArrayType) {
            ArrayType at = (ArrayType) currentType;
            dims.add(at.getSize());
            currentType = at.getElementType();
        }
        
        return dims;
    }
    
    private Type getArrayBaseType(ArrayType arrayType) {
        Type currentType = arrayType;
        
        while (currentType instanceof ArrayType) {
            ArrayType at = (ArrayType) currentType;
            currentType = at.getElementType();
        }
        
        return currentType;
    }
    
    
    public void interpret(AST ast) {
        Computation comp = ast.getComputation();
        comp.accept(this);
    }
    
    // Helper methods
    private Object getStoredValue() {
        return valueStack.isEmpty() ? null : valueStack.pop();
    }

    private static Object defaultFor(Type t) {
        if (t instanceof IntType) return 0;
        if (t instanceof FloatType) return 0.0f;
        if (t instanceof BoolType) return false;
        return null;
    }

    // ----- Array Helper Methods (Needed for Flat Access) -----

    /**
     * Traverses a left-recursive ArrayIndex chain to the base Designator and
     * collects all index expressions (outermost to innermost) into indexExprsOut.
     */
    private Symbol unwindArrayIndexChain(Expression expr, List<Expression> indexExprsOut) {
        if (expr instanceof ArrayIndex) {
            ArrayIndex ai = (ArrayIndex) expr;
            Symbol baseSym = unwindArrayIndexChain(ai.base(), indexExprsOut);
            indexExprsOut.add(ai.index());
            return baseSym;
        } else if (expr instanceof Designator) {
            String name = ((Designator) expr).name().lexeme();
            try {
                return symbolTable.lookup(name);
            } catch (mocha.SymbolNotFoundError e) {
                throw new RuntimeException("Unknown array variable: " + name);
            }
        } else {
            throw new RuntimeException("Invalid base expression for array access: " + expr.getClass().getSimpleName());
        }
    }

    private static List<Integer> computeStrides(List<Integer> dims) {
        ArrayList<Integer> strides = new ArrayList<>(dims.size());
        int acc = 1;
        for (int i = dims.size() - 1; i >= 0; i--) {
            strides.add(0, acc);
            acc *= dims.get(i);
        }
        return strides;
    }

    private static int computeFlatOffset(List<Integer> dims, List<Integer> indices) {
        if (indices.size() != dims.size()) {
            throw new RuntimeException("Index count mismatch. Expected " + dims.size() + " indices, got " + indices.size());
        }
        List<Integer> strides = computeStrides(dims);
        int off = 0;
        for (int i = 0; i < indices.size(); i++) {
            int idx = indices.get(i);
            int dim = dims.get(i);
            if (idx < 0 || idx >= dim) throw new RuntimeException("Array index out of bounds: " + idx);
            off += idx * strides.get(i);
        }
        return off;
    }
    
    // Expression evaluation
    @Override
    public void visit(IntegerLiteral node) {
        valueStack.push(node.getValue());
    }
    
    @Override
    public void visit(FloatLiteral node) {
        valueStack.push(node.getValue());
    }
    
    @Override
    public void visit(BoolLiteral node) {
        valueStack.push(node.getValue());
    }
    
    @Override
    public void visit(Designator node) {
        String name = node.name().lexeme();
        try { symbolTable.lookup(name); } catch (mocha.SymbolNotFoundError e) { throw new RuntimeException("Unknown variable: " + name); }
        Object value = memory.get(name);
        if (value == null) {
            throw new RuntimeException("Variable " + name + " not initialized");
        }
        valueStack.push(value);
    }
    
    @Override
    public void visit(ArrayIndex node) {
        ArrayList<Expression> indexExprs = new ArrayList<>();
        Symbol sym = unwindArrayIndexChain(node, indexExprs);

        ArrayList<Integer> indices = new ArrayList<>();
        for (Expression idxExpr : indexExprs) {
            idxExpr.accept(this);
            Object v = getStoredValue();
            if (!(v instanceof Integer)) throw new RuntimeException("Array index must be integer");
            indices.add((Integer) v);
        }

        if (!(sym.type() instanceof ArrayType)) throw new RuntimeException("Variable is not an array: " + sym.name());
        ArrayType at = (ArrayType) sym.type();
        List<Integer> dims = getArrayDimensions(at);
        Object[] data = (Object[]) memory.get(sym.name());
        int offset = computeFlatOffset(dims, indices);
        valueStack.push(data[offset]);
    }
    
    @Override
    public void visit(Dereference node) {
        throw new RuntimeException("Dereference operator not supported in interpreter");
    }
    
    @Override
    public void visit(Addition node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        
        Object right = getStoredValue();
        Object left = getStoredValue();
        
        if (left instanceof Integer && right instanceof Integer) {
            valueStack.push((Integer) left + (Integer) right);
        } else if (left instanceof Float && right instanceof Float) {
            valueStack.push((Float) left + (Float) right);
        } else {
            throw new RuntimeException("Invalid addition operands");
        }
    }
    
    @Override
    public void visit(Subtraction node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        
        Object right = getStoredValue();
        Object left = getStoredValue();
        
        if (left instanceof Integer && right instanceof Integer) {
            valueStack.push((Integer) left - (Integer) right);
        } else if (left instanceof Float && right instanceof Float) {
            valueStack.push((Float) left - (Float) right);
        } else {
            throw new RuntimeException("Invalid subtraction operands");
        }
    }
    
    @Override
    public void visit(Multiplication node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        
        Object right = getStoredValue();
        Object left = getStoredValue();
        
        if (left instanceof Integer && right instanceof Integer) {
            valueStack.push((Integer) left * (Integer) right);
        } else if (left instanceof Float && right instanceof Float) {
            valueStack.push((Float) left * (Float) right);
        } else {
            throw new RuntimeException("Invalid multiplication operands");
        }
    }
    
    @Override
    public void visit(Division node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        
        Object right = getStoredValue();
        Object left = getStoredValue();
        
        if (left instanceof Integer && right instanceof Integer) {
            if ((Integer) right == 0) {
                throw new RuntimeException("Division by zero");
            }
            valueStack.push((Integer) left / (Integer) right);
        } else if (left instanceof Float && right instanceof Float) {
            valueStack.push((Float) left / (Float) right);
        } else {
            throw new RuntimeException("Invalid division operands");
        }
    }
    
    @Override
    public void visit(LogicalAnd node) {
        // Short-circuit version 
        // node.getLeft().accept(this);
        // Object left = getStoredValue();
        // if (!(left instanceof Boolean && (Boolean) left)) {
        //     valueStack.push(false);
        //     return;
        // }
        // node.getRight().accept(this);
        // Object right = getStoredValue();
        // valueStack.push(left instanceof Boolean && right instanceof Boolean && (Boolean) left && (Boolean) right);

        // Assignment semantics: evaluate both sides (no short-circuit) to preserve side-effects
        node.getLeft().accept(this);
        Object left = getStoredValue();
        node.getRight().accept(this);
        Object right = getStoredValue();
        boolean lv = (left instanceof Boolean) ? (Boolean) left : false;
        boolean rv = (right instanceof Boolean) ? (Boolean) right : false;
        valueStack.push(lv && rv);
    }
    
    @Override
    public void visit(LogicalOr node) {
        // Short-circuit version
        // node.getLeft().accept(this);
        // Object left = getStoredValue();
        // if (left instanceof Boolean && (Boolean) left) {
        //     valueStack.push(true);
        //     return;
        // }
        // node.getRight().accept(this);
        // Object right = getStoredValue();
        // valueStack.push(right instanceof Boolean && (Boolean) right);

        // Assignment semantics: evaluate both sides (no short-circuit) to preserve side-effects
        node.getLeft().accept(this);
        Object left = getStoredValue();
        node.getRight().accept(this);
        Object right = getStoredValue();
        boolean lv = (left instanceof Boolean) ? (Boolean) left : false;
        boolean rv = (right instanceof Boolean) ? (Boolean) right : false;
        valueStack.push(lv || rv);
    }
    
    @Override
    public void visit(LogicalNot node) {
        node.operand().accept(this);
        valueStack.push(!(Boolean) getStoredValue());
    }
    
    @Override
    public void visit(Relation node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        
        Object right = getStoredValue();
        Object left = getStoredValue();
        
        // Type coercion for relations
        if (left instanceof Integer && right instanceof Float) {
            left = ((Integer) left).floatValue();
        } else if (left instanceof Float && right instanceof Integer) {
            right = ((Integer) right).floatValue();
        }
        
        boolean result = false;
        switch (node.getOperator().kind()) {
            case EQUAL_TO:
                result = left.equals(right);
                break;
            case NOT_EQUAL:
                result = !left.equals(right);
                break;
            case GREATER_THAN:
                result = ((Comparable) left).compareTo(right) > 0;
                break;
            case LESS_THAN:
                result = ((Comparable) left).compareTo(right) < 0;
                break;
            case GREATER_EQUAL:
                result = ((Comparable) left).compareTo(right) >= 0;
                break;
            case LESS_EQUAL:
                result = ((Comparable) left).compareTo(right) <= 0;
                break;
        }
        valueStack.push(result);
    }
    
    @Override
    public void visit(Power node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        
        Object right = getStoredValue();
        Object left = getStoredValue();
        
        if (left instanceof Integer && right instanceof Integer) {
            valueStack.push((int) Math.pow((Integer) left, (Integer) right));
        } else if (left instanceof Number && right instanceof Number) {
            valueStack.push((float) Math.pow(((Number) left).doubleValue(), ((Number) right).doubleValue()));
        } else {
            throw new RuntimeException("Invalid power operands");
        }
    }
    
    @Override
    public void visit(Modulo node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        
        Object right = getStoredValue();
        Object left = getStoredValue();
        
        if (left instanceof Integer && right instanceof Integer) {
            if ((Integer) right == 0) {
                throw new RuntimeException("Modulo by zero");
            }
            valueStack.push((Integer) left % (Integer) right);
        } else if (left instanceof Float && right instanceof Float) {
            float rv = (Float) right;
            if (rv == 0.0f) {
                throw new RuntimeException("Modulo by zero");
            }
            valueStack.push((Float) left % (Float) right);
        } else {
            throw new RuntimeException("Invalid modulo operands");
        }
    }
    
    @Override
    public void visit(FunctionCallStatement node) {
        // Function call statements are just function call expressions used for side effects
        node.getFunctionCall().accept(this);
    }
    
    @Override
    public void visit(FunctionCallExpression node) {
        String funcName = node.name().lexeme();
        
        // Handle predefined functions
        switch (funcName) {
            case "readInt":
                System.out.print("int? ");
                if (inputScanner.hasNextInt()) {
                    int value = inputScanner.nextInt();
                    valueStack.push(value);
                } else {
                    throw new RuntimeException("No integer input available");
                }
                break;
            case "readFloat":
                System.out.print("float? ");
                if (inputScanner.hasNextFloat()) {
                    float value = inputScanner.nextFloat();
                    valueStack.push(value);
                } else {
                    throw new RuntimeException("No float input available");
                }
                break;
            case "readBool":
                System.out.print("true or false? ");
                if (inputScanner.hasNextBoolean()) {
                    boolean value = inputScanner.nextBoolean();
                    valueStack.push(value);
                } else {
                    // Default to false if no boolean input available
                    valueStack.push(false);
                }
                break;
            case "printInt":
                node.arguments().accept(this);
                Object intVal = getStoredValue();
                System.out.print(intVal.toString() + " ");
                break;
            case "printFloat":
                node.arguments().accept(this);
                Object floatVal = getStoredValue();
                if (floatVal instanceof Number) {
                    String s = String.format(Locale.US, "%.2f", ((Number) floatVal).doubleValue());
                    System.out.print(s + " ");
                } else {
                    System.out.print(floatVal.toString() + " ");
                }
                break;
            case "printBool":
                node.arguments().accept(this);
                Object boolVal = getStoredValue();
                System.out.print(boolVal.toString() + " ");
                break;
            case "println":
                System.out.println();
                break;
            default:
                node.arguments().accept(this);
                valueStack.push(0);
                break;
        }
    }
    
    // Statement execution
    @Override
    public void visit(Assignment node) {
        Object value;
        
        // Handle increment/decrement operators (source is null)
        if (node.getSource() == null) {
            if (node.getDestination() instanceof Designator) {
                String name = ((Designator) node.getDestination()).name().lexeme();
                try { symbolTable.lookup(name); } catch (mocha.SymbolNotFoundError e) { throw new RuntimeException("Unknown variable: " + name); }
                Object currentValue = memory.get(name);
                
                if (node.getOperator().kind() == Token.Kind.UNI_INC) {
                    if (currentValue instanceof Integer) {
                        value = (Integer) currentValue + 1;
                    } else if (currentValue instanceof Float) {
                        value = (Float) currentValue + 1.0f;
                    } else {
                        throw new RuntimeException("Cannot increment non-numeric type");
                    }
                } else if (node.getOperator().kind() == Token.Kind.UNI_DEC) {
                    if (currentValue instanceof Integer) {
                        value = (Integer) currentValue - 1;
                    } else if (currentValue instanceof Float) {
                        value = (Float) currentValue - 1.0f;
                    } else {
                        throw new RuntimeException("Cannot decrement non-numeric type");
                    }
                } else {
                    throw new RuntimeException("Unknown unary operator");
                }
                
                memory.put(name, value);
                return;
            } else {
                throw new RuntimeException("Increment/decrement only supported for simple variables");
            }
        }
        
        // Handle compound assignment operators
        if (node.getOperator().kind() != Token.Kind.ASSIGN) {
            // Get current value
            Object currentValue;
            if (node.getDestination() instanceof Designator) {
                String name = ((Designator) node.getDestination()).name().lexeme();
                try { symbolTable.lookup(name); } catch (mocha.SymbolNotFoundError e) { throw new RuntimeException("Unknown variable: " + name); }
                currentValue = memory.get(name);
            } else {
                throw new RuntimeException("Compound assignment only supported for simple variables");
            }
            
            // Evaluate the right-hand side
            node.getSource().accept(this);
            Object rightValue = getStoredValue();
            
            // Apply the compound operation
            switch (node.getOperator().kind()) {
                case ADD_ASSIGN:
                    if (currentValue instanceof Integer && rightValue instanceof Integer) {
                        value = (Integer) currentValue + (Integer) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Float) {
                        value = (Float) currentValue + (Float) rightValue;
                    } else if (currentValue instanceof Integer && rightValue instanceof Float) {
                        value = (Float) currentValue + (Float) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Integer) {
                        value = (Float) currentValue + (Float) rightValue;
                    } else {
                        throw new RuntimeException("Cannot add " + currentValue.getClass().getSimpleName() + " and " + rightValue.getClass().getSimpleName());
                    }
                    break;
                case SUB_ASSIGN:
                    if (currentValue instanceof Integer && rightValue instanceof Integer) {
                        value = (Integer) currentValue - (Integer) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Float) {
                        value = (Float) currentValue - (Float) rightValue;
                    } else if (currentValue instanceof Integer && rightValue instanceof Float) {
                        value = (Float) currentValue - (Float) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Integer) {
                        value = (Float) currentValue - (Float) rightValue;
                    } else {
                        throw new RuntimeException("Cannot subtract " + currentValue.getClass().getSimpleName() + " and " + rightValue.getClass().getSimpleName());
                    }
                    break;
                case MUL_ASSIGN:
                    if (currentValue instanceof Integer && rightValue instanceof Integer) {
                        value = (Integer) currentValue * (Integer) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Float) {
                        value = (Float) currentValue * (Float) rightValue;
                    } else if (currentValue instanceof Integer && rightValue instanceof Float) {
                        value = (Float) currentValue * (Float) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Integer) {
                        value = (Float) currentValue * (Float) rightValue;
                    } else {
                        throw new RuntimeException("Cannot multiply " + currentValue.getClass().getSimpleName() + " and " + rightValue.getClass().getSimpleName());
                    }
                    break;
                case DIV_ASSIGN:
                    if (currentValue instanceof Integer && rightValue instanceof Integer) {
                        if ((Integer) rightValue == 0) throw new RuntimeException("Division by zero");
                        value = (Integer) currentValue / (Integer) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Float) {
                        if ((Float) rightValue == 0.0f) throw new RuntimeException("Division by zero");
                        value = (Float) currentValue / (Float) rightValue;
                    } else if (currentValue instanceof Integer && rightValue instanceof Float) {
                        if ((Float) rightValue == 0.0f) throw new RuntimeException("Division by zero");
                        value = (Float) currentValue / (Float) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Integer) {
                        if ((Integer) rightValue == 0) throw new RuntimeException("Division by zero");
                        value = (Float) currentValue / (Float) rightValue;
                    } else {
                        throw new RuntimeException("Cannot divide " + currentValue.getClass().getSimpleName() + " and " + rightValue.getClass().getSimpleName());
                    }
                    break;
                case MOD_ASSIGN:
                    if (currentValue instanceof Integer && rightValue instanceof Integer) {
                        if ((Integer) rightValue == 0) throw new RuntimeException("Modulo by zero");
                        value = (Integer) currentValue % (Integer) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Float) {
                        if ((Float) rightValue == 0.0f) throw new RuntimeException("Modulo by zero");
                        value = (Float) currentValue % (Float) rightValue;
                    } else if (currentValue instanceof Integer && rightValue instanceof Float) {
                        if ((Float) rightValue == 0.0f) throw new RuntimeException("Modulo by zero");
                        value = (Float) currentValue % (Float) rightValue;
                    } else if (currentValue instanceof Float && rightValue instanceof Integer) {
                        if ((Integer) rightValue == 0) throw new RuntimeException("Modulo by zero");
                        value = (Float) currentValue % (Float) rightValue;
                    } else {
                        throw new RuntimeException("Cannot modulo " + currentValue.getClass().getSimpleName() + " and " + rightValue.getClass().getSimpleName());
                    }
                    break;
                case POW_ASSIGN:
                    if (currentValue instanceof Integer && rightValue instanceof Integer) {
                        value = (int) Math.pow((Integer) currentValue, (Integer) rightValue);
                    } else if (currentValue instanceof Float && rightValue instanceof Float) {
                        value = (float) Math.pow((Float) currentValue, (Float) rightValue);
                    } else if (currentValue instanceof Integer && rightValue instanceof Float) {
                        value = (float) Math.pow((Integer) currentValue, (Float) rightValue);
                    } else if (currentValue instanceof Float && rightValue instanceof Integer) {
                        value = (float) Math.pow((Float) currentValue, (Integer) rightValue);
                    } else {
                        throw new RuntimeException("Cannot raise " + currentValue.getClass().getSimpleName() + " to power of " + rightValue.getClass().getSimpleName());
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown compound assignment operator");
            }
            
            // Store the result
            String name = ((Designator) node.getDestination()).name().lexeme();
            memory.put(name, value);
            return;
        }
        
        // Regular assignment
        node.getSource().accept(this);
        value = getStoredValue();
        
        // Store in the appropriate variable
        if (node.getDestination() instanceof Designator) {
            String name = ((Designator) node.getDestination()).name().lexeme();
            try { symbolTable.lookup(name); } catch (mocha.SymbolNotFoundError e) { throw new RuntimeException("Unknown variable: " + name); }
            memory.put(name, value);
        } else if (node.getDestination() instanceof ArrayIndex) {
            ArrayList<Expression> indexExprs = new ArrayList<>();
            Symbol sym = unwindArrayIndexChain(node.getDestination(), indexExprs);

            ArrayList<Integer> indices = new ArrayList<>();
            for (Expression idxExpr : indexExprs) {
                idxExpr.accept(this);
                Object v = getStoredValue();
                if (!(v instanceof Integer)) throw new RuntimeException("Array index must be integer");
                indices.add((Integer) v);
            }

            if (!(sym.type() instanceof ArrayType)) throw new RuntimeException("Variable is not an array: " + sym.name());
            ArrayType at = (ArrayType) sym.type();
            List<Integer> dims = getArrayDimensions(at);
            Object[] data = (Object[]) memory.get(sym.name());
            int offset = computeFlatOffset(dims, indices);
            data[offset] = value;
        }
    }
    
    @Override
    public void visit(IfStatement node) {
        node.condition().accept(this);
        boolean condition = (Boolean) getStoredValue();
        
        if (condition) {
            node.thenBlock().accept(this);
        } else if (node.elseBlock() != null) {
            node.elseBlock().accept(this);
        }
    }
    
    @Override
    public void visit(ReturnStatement node) {
        if (node.value() != null) {
            node.value().accept(this);
            // Return value is on the stack for function calls
        }
        // Early return - throw exception to break out of execution
        throw new RuntimeException("EARLY_RETURN");
    }
    
    @Override
    public void visit(Computation node) {
        // Initialize globals via standard visitation
        node.variables().accept(this);
        // Ignore functions in interpreter mode
        // Execute main sequence
        try {
            node.mainStatementSequence().accept(this);
        } catch (RuntimeException e) {
            if (e.getMessage().equals("EARLY_RETURN")) {
                // Early return encountered, stop execution
                return;
            } else {
                // Re-throw other exceptions
                throw e;
            }
        }
    }
    
    @Override
    public void visit(VariableDeclaration node) {
        // Initialize declared variables with default values
        Type varType = node.type();
        for (Token nameTok : node.names()) {
            Symbol sym = symbolTable.lookup(nameTok.lexeme());
            Object value;
            if (varType instanceof ArrayType) {
                ArrayType at = (ArrayType) varType;
                List<Integer> dims = getArrayDimensions(at);
                int total = 1;
                for (int d : dims) total *= d;
                Object elementDefault = defaultFor(getArrayBaseType(at));

                // IMPORTANT: Allocate Object array, NOT ArrayList
                Object[] data = new Object[total];
                Arrays.fill(data, elementDefault);
                value = data;
            } else {
                value = defaultFor(varType);
            }
            memory.put(nameTok.lexeme(), value);
        }
    }
    
    @Override
    public void visit(FunctionDeclaration node) {
        // Not supported in interpreter mode
    }
    
    @Override
    public void visit(StatementSequence node) {
        for (Statement stmt : node.getStatements()) {
            stmt.accept(this);
        }
    }
    
    @Override
    public void visit(ArgumentList node) {
        for (Expression arg : node.args()) {
            arg.accept(this);
        }
    }
    
    @Override
    public void visit(DeclarationList node) {
        for (Node decl : node.declarations()) {
            decl.accept(this);
        }
    }
    
    @Override
    public void visit(FunctionBody node) {
        // Not supported in interpreter mode
    }
    
    // Unused methods
    @Override
    public void visit(WhileStatement node) {
        // Simple while loop implementation
        while (true) {
            // Evaluate condition
            node.condition().accept(this);
            Object conditionValue = getStoredValue();
            
            // Check if condition is true
            if (!(conditionValue instanceof Boolean) || !(Boolean) conditionValue) {
                break; // Exit loop if condition is false
            }
            
            // Execute body
            node.body().accept(this);
        }
    }
    
    @Override
    public void visit(RepeatStatement node) {
        throw new RuntimeException("Repeat loops not supported in interpreter");
    }
}
