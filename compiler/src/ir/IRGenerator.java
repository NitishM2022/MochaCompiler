package ir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import ast.*;
import mocha.Symbol;
import mocha.SymbolTable;
import mocha.Token;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;
import types.Type;
import types.BoolType;
import types.IntType;
import types.FloatType;
import types.ArrayType;
import types.FuncType;

public class IRGenerator implements NodeVisitor {
    private int instructionCounter;
    private int blockCounter;
    private int nextTempNumber;

    private int fpOffset;
    private int gpOffset;
    private int paramOffset;

    private Stack<Variable> freeTemps;

    private Set<Symbol> initializedGlobals;
    private Set<Symbol> initializedLocals;
    private Set<Symbol> needsDefaultInitGlobals;
    private Set<Symbol> needsDefaultInitLocals;
    private Set<Symbol> usedGlobalsInFunction;  // Track globals actually used in this function

    private List<Symbol> globalVariables;
    private List<Symbol> globalNonArrayVars;

    private List<CFG> cfgs;
    private CFG currentCFG;
    private BasicBlock currentBlock;
    private SymbolTable symbolTable;
    private Stack<Value> valueStack;

    public IRGenerator(SymbolTable symTable) {
        this.instructionCounter = 0;
        this.blockCounter = 1;
        this.nextTempNumber = 0;
        this.cfgs = new ArrayList<>();
        this.symbolTable = symTable;
        this.valueStack = new Stack<>();
        this.freeTemps = new Stack<>();
        this.initializedGlobals = new HashSet<>();
        this.needsDefaultInitGlobals = new HashSet<>();
        this.needsDefaultInitLocals = new HashSet<>();
        this.globalVariables = new ArrayList<>();
        this.globalNonArrayVars = new ArrayList<>();
    }

    public List<CFG> generate(AST ast) {
        assignGlobalOffsets(ast.getComputation().variables());
        ast.getComputation().accept(this);
        return cfgs;
    }

    private void assignGlobalOffsets(DeclarationList globals) {
        for (Node node : globals.declarations()) {
            if (node instanceof VariableDeclaration) {
                VariableDeclaration decl = (VariableDeclaration) node;
                for (Token name : decl.names()) {
                    Symbol sym = symbolTable.lookup(name.lexeme());
                    sym.setGlobal(true);
                    int size = calculateSize(decl.type());
                    gpOffset -= size;
                    sym.setGlobalOffset(gpOffset);

                    globalVariables.add(sym);
                    if (!(sym.type() instanceof ArrayType)) {
                        globalNonArrayVars.add(sym);
                    }
                }
            }
        }
    }

    private int calculateSize(Type type) {
        if (type instanceof ArrayType) {
            // Arrays need: 4 bytes for address slot + array data size
            return 4 + ((ArrayType) type).getAllocationSize();
        }
        return 4;
    }

    private void loadAllGlobals() {
        for (Symbol global : globalNonArrayVars) {
            Variable var = new Variable(global);
            addInstruction(new LoadGP(nextInstructionId(), var, global.getGlobalOffset()));
            // Conservative: assume loaded implies potentially used/read, preventing warnings
            initializedGlobals.add(global);
        }
    }

    private void insertEntryLoads(BasicBlock entryBlock, int insertionIndex) {
        // Optimization: Only load globals that were actually used in the function/main
        // We insert these in reverse order at the specific index so they appear in correct order
        // (Though order of loads doesn't strictly matter for correctness)
        List<TAC> loads = new ArrayList<>();
        for (Symbol global : globalNonArrayVars) {
            if (usedGlobalsInFunction.contains(global)) {
                Variable var = new Variable(global);
                 // Note: Instruction ID ordering might look slightly out of sequence, but that's fine for IR
                loads.add(new LoadGP(nextInstructionId(), var, global.getGlobalOffset()));
                initializedGlobals.add(global);
            }
        }
        
        // Insert into block
        entryBlock.getInstructions().addAll(insertionIndex, loads);
    }

    private void storeUsedGlobals() {
        for (Symbol global : globalNonArrayVars) {
            // Optimization: Only store globals that were USED and MODIFIED (Initialized)
            if (usedGlobalsInFunction.contains(global) && initializedGlobals.contains(global)) {
                Variable var = new Variable(global);
                addInstruction(new StoreGP(nextInstructionId(), var, global.getGlobalOffset()));
            }
        }
    }

    private void loadAllParams(List<Symbol> params) {
        for (Symbol param : params) {
            // ALL parameters (including array addresses) must be loaded from stack
            Variable paramVar = new Variable(param);
            addInstruction(new LoadFP(nextInstructionId(), paramVar, param.getFpOffset()));
        }
    }

    private int nextInstructionId() {
        return ++instructionCounter;
    }

    private Variable getTemp() {
        if (!freeTemps.isEmpty()) {
            Variable temp = freeTemps.pop();
            return temp;
        } else {
            int tempNum = nextTempNumber++;
            fpOffset -= 4;
            int offset = fpOffset;

            Symbol tempSym = new Symbol("$t" + tempNum);
            tempSym.setFpOffset(offset);

            return new Variable(tempSym, true, tempNum);
        }
    }

    private Variable getTemp(boolean isFloat) {
        Variable temp = getTemp();
        if (isFloat) {
            temp.getSymbol().setType(new FloatType());
        } else {
            temp.getSymbol().setType(new IntType());
        }
        return temp;
    }

    private boolean isFloat(Value v) {
        if (v instanceof Literal) {
            if (((Literal) v).getValue() instanceof FloatLiteral) {
                return true;
            }
        }

        if (v instanceof FloatLiteral) {
            return true;
        }
        if (v instanceof Variable) {
            Variable var = (Variable) v;
            return var.getSymbol().type() instanceof FloatType;
        }
        return false;
    }

    private void freeTemp(Value val) {
        if (val instanceof Variable) {
            Variable var = (Variable) val;
            if (var.isTemp()) {
                // Clear the type before recycling to avoid stale type info
                var.getSymbol().setType(null);
                freeTemps.push(var);
            }
        }
    }

    private Value loadIfNeeded(Value val) {
        if (val instanceof Variable && !((Variable) val).isTemp()) {
            Variable var = (Variable) val;
            Symbol sym = var.getSymbol();

            // Check if user explicitly initialized this variable
            boolean isUserInit = sym.isGlobal()
                    ? initializedGlobals.contains(sym)
                    : initializedLocals.contains(sym);

            if (!isUserInit) {
                // Warn on first use
                System.err.println("WARNING: Variable '" + sym.name() + "' may be used before initialization");
                
                // Mark this variable as needing default initialization
                // (will be initialized at entry block)
                if (sym.isGlobal()) {
                    needsDefaultInitGlobals.add(sym);
                } else {
                    needsDefaultInitLocals.add(sym);
                }
                
                // Mark as "seen" so we don't warn multiple times
                if (sym.isGlobal()) {
                    initializedGlobals.add(sym);
                } else {
                    initializedLocals.add(sym);
                }
            }

            return var;
        }
        return val;
    }

    private void initializeVariableToDefault(Variable var) {
        Symbol sym = var.getSymbol();
        Type type = sym.type();
        Value defaultValue;

        if (type instanceof IntType) {
            defaultValue = new Immediate(0);
        } else if (type instanceof FloatType) {
            defaultValue = new Immediate(0.0);
        } else if (type instanceof BoolType) {
            defaultValue = new Immediate(0);
        } else {
            defaultValue = new Immediate(0);
        }

        addInstruction(new Mov(nextInstructionId(), var, defaultValue));
    }

    private boolean isImmediate(Value v) {
        return v instanceof Immediate || v instanceof Literal;
    }

    private int getImmediateValue(Value v) {
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            if (val instanceof Integer)
                return (Integer) val;
            if (val instanceof Boolean)
                return (Boolean) val ? 1 : 0;

        }
        if (v instanceof Literal) {
            Literal lit = (Literal) v;
            if (lit.getValue() instanceof ast.IntegerLiteral) {
                return ((ast.IntegerLiteral) lit.getValue()).getValue();
            } else if (lit.getValue() instanceof ast.BoolLiteral) {
                return ((ast.BoolLiteral) lit.getValue()).getValue() ? 1 : 0;
            }
        }
        throw new RuntimeException("Cannot extract immediate value from: " + v);
    }

    private boolean isZero(Value v) {
        if (isImmediate(v)) {
            return getImmediateValue(v) == 0;
        }
        return false;
    }

    /**
     * Calculate the size of elements at this array dimension.
     * For multi-dimensional arrays, returns the size of the sub-array.
     * For final dimensions, returns 4 (size of scalar).
     */
    private int calculateElementSize(Expression base) {
        Type baseType = getExpressionType(base);

        if (baseType instanceof ArrayType) {
            ArrayType arrType = (ArrayType) baseType;
            Type elemType = arrType.getElementType();

            if (elemType instanceof ArrayType) {
                return ((ArrayType) elemType).getAllocationSize();
            } else {
                return 4;
            }
        }

        return 4;
    }

    /**
     * Check if this is an intermediate array access (multi-dimensional).
     * Returns true if the base expression's type is an array whose elements are
     * also arrays.
     */
    private boolean isIntermediateArrayAccess(Expression base) {
        Type baseType = getExpressionType(base);

        if (baseType instanceof ArrayType) {
            ArrayType arrType = (ArrayType) baseType;
            Type elemType = arrType.getElementType();
            return elemType instanceof ArrayType;
        }

        return false;
    }

    /**
     * Get the type of an expression.
     * This is used to determine array dimensions and element sizes.
     */
    private Type getExpressionType(Expression expr) {
        if (expr instanceof Designator) {
            String name = ((Designator) expr).name().lexeme();
            Symbol sym = symbolTable.lookup(name);
            return sym != null ? sym.type() : null;
        } else if (expr instanceof ArrayIndex) {
            ArrayIndex ai = (ArrayIndex) expr;
            Type baseType = getExpressionType(ai.base());
            if (baseType instanceof ArrayType) {
                return ((ArrayType) baseType).getElementType();
            }
        }
        return null;
    }

    @Override
    public void visit(IntegerLiteral node) {
        valueStack.push(new Literal(node));
    }

    @Override
    public void visit(FloatLiteral node) {
        valueStack.push(new Literal(node));
    }

    @Override
    public void visit(BoolLiteral node) {
        valueStack.push(new Literal(node));
    }

    @Override
    public void visit(Designator node) {
        Symbol sym = symbolTable.lookup(node.name().lexeme());

        // Track global usage
        if (sym.isGlobal() && !(sym.type() instanceof ArrayType)) {
            usedGlobalsInFunction.add(sym);
        }

        if (sym.type() instanceof ArrayType) {
            // We must push the BASE ADDRESS of the array onto the stack.
            if (sym.isParameter()) {
                // Array Parameters: address was loaded in loadAllParams()
                valueStack.push(new Variable(sym));
            } else {
                Variable baseAddr = getTemp();
                baseAddr.getSymbol().setType(sym.type());
                
                if (sym.isGlobal()) {
                    // Global Array: The array address slot is at GP + GlobalOffset.
                    // The array data starts at GlobalOffset + 4.
                    addInstruction(new AddaGP(nextInstructionId(), baseAddr, sym.getGlobalOffset(), new Immediate(4)));
                } else {
                    // Local Array: The array address slot is at FP + FpOffset.
                    // The array data starts at FpOffset + 4.
                    addInstruction(new AddaFP(nextInstructionId(), baseAddr, sym.getFpOffset(), new Immediate(4)));
                }
                
                valueStack.push(baseAddr);
            }
        } else {
            valueStack.push(new Variable(sym));
        }
    }

    @Override
    public void visit(ArrayIndex node) {
        node.base().accept(this);
        node.index().accept(this);

        Value indexVal = loadIfNeeded(valueStack.pop());
        Value baseAddr = valueStack.pop();

        // For multi-dim arrays, this is critical!
        int elementSize = calculateElementSize(node.base());

        Value scaledIndex;
        if (isImmediate(indexVal) && isZero(indexVal)) {
            scaledIndex = new Immediate(0);
        } else if (isImmediate(indexVal)) {
            int idx = getImmediateValue(indexVal);
            scaledIndex = new Immediate(idx * elementSize);
        } else {
            Variable tempScaled = getTemp();
            addInstruction(new Mul(nextInstructionId(), tempScaled, indexVal, new Immediate(elementSize), false));
            scaledIndex = tempScaled;
        }

        Variable elementAddr = getTemp();
        addInstruction(new Adda(nextInstructionId(), elementAddr, baseAddr, scaledIndex));

        // Intermediate: arr[i] in arr[i][j] -> return ADDRESS
        // Final: arr[i][j] or arr[i] (1D) -> LOAD and return VALUE
        boolean isIntermediateAccess = isIntermediateArrayAccess(node.base());

        if (isIntermediateAccess) {
            valueStack.push(elementAddr);

            if (indexVal != scaledIndex && indexVal instanceof Variable)
                freeTemp(indexVal);
            if (scaledIndex instanceof Variable)
                freeTemp(scaledIndex);
            freeTemp(baseAddr);
        } else {
            Variable loadedValue = getTemp();
            // FIX: Set type of loaded value so isFloat() works correctly!
            loadedValue.getSymbol().setType(getExpressionType(node));
            
            addInstruction(new Load(nextInstructionId(), loadedValue, elementAddr));
            valueStack.push(loadedValue);

            if (indexVal != scaledIndex && indexVal instanceof Variable)
                freeTemp(indexVal);
            if (scaledIndex instanceof Variable)
                freeTemp(scaledIndex);
            freeTemp(baseAddr);
            freeTemp(elementAddr);
        }
    }

    @Override
    public void visit(Dereference node) {
        throw new RuntimeException("Dereference not supported in IR");
    }

    // ==========================================
    // Commutative Operations (Add, Mul, And, Or)
    // Strategy: Swap operands if Left is Immediate
    // ==========================================

    @Override
    public void visit(Addition node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        boolean isFloat = isFloat(leftVal) || isFloat(rightVal);
        Variable temp = getTemp(isFloat);

        if (isImmediate(leftVal) && !isImmediate(rightVal)) {
            addInstruction(new Add(nextInstructionId(), temp, rightVal, leftVal, isFloat));
        } else if (isImmediate(leftVal)) {
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal, isFloat));
            addInstruction(new Add(nextInstructionId(), temp, immTemp, rightVal, isFloat));
            freeTemp(immTemp);
        } else {
            addInstruction(new Add(nextInstructionId(), temp, leftVal, rightVal, isFloat));
        }

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(Multiplication node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        boolean isFloat = isFloat(leftVal) || isFloat(rightVal);
        Variable temp = getTemp(isFloat);

        if (isImmediate(leftVal) && !isImmediate(rightVal)) {
            addInstruction(new Mul(nextInstructionId(), temp, rightVal, leftVal, isFloat));
        } else if (isImmediate(leftVal)) {
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal, isFloat));
            addInstruction(new Mul(nextInstructionId(), temp, immTemp, rightVal, isFloat));
            freeTemp(immTemp);
        } else {
            addInstruction(new Mul(nextInstructionId(), temp, leftVal, rightVal, isFloat));
        }

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    /* diasabled: Short-circuiting
    @Override
    public void visit(LogicalAnd node) {
        node.getLeft().accept(this);
        Value leftVal = loadIfNeeded(valueStack.pop());
        
        // If leftVal is a Literal, move it to a temp to avoid register conflicts
        if (leftVal instanceof Literal) {
            Variable leftTemp = getTemp();
            addInstruction(new Mov(nextInstructionId(), leftTemp, leftVal));
            leftVal = leftTemp;
        }

        BasicBlock evalRightBlock = new BasicBlock(++blockCounter);
        BasicBlock endBlock = new BasicBlock(++blockCounter);
        BasicBlock current = currentBlock;

        Variable result = getTemp();
        addInstruction(new Mov(nextInstructionId(), result, new Immediate(0)));

        currentCFG.addBlock(evalRightBlock);
        currentCFG.addBlock(endBlock);

        // Logic: IF (Left == False) THEN Done ELSE CheckRight.
        addInstruction(new Beq(nextInstructionId(), leftVal, endBlock));
        freeTemp(leftVal);

        current.addSuccessor(endBlock);
        current.addSuccessor(evalRightBlock);
        evalRightBlock.addPredecessor(current);
        endBlock.addPredecessor(current);

        currentBlock = evalRightBlock;
        node.getRight().accept(this);
        
        // Use currentBlock (NOT evalRightBlock) because the right child might have added blocks
        Value rightVal = loadIfNeeded(valueStack.pop());

        addInstruction(new Mov(nextInstructionId(), result, rightVal));
        freeTemp(rightVal);

        addInstruction(new Bra(nextInstructionId(), endBlock));
        
        currentBlock.addSuccessor(endBlock);
        endBlock.addPredecessor(currentBlock);

        currentBlock = endBlock;
        valueStack.push(result);
    }

    @Override
    public void visit(LogicalOr node) {
        node.getLeft().accept(this);
        Value leftVal = loadIfNeeded(valueStack.pop());
        
        // If leftVal is a Literal, move it to a temp to avoid register conflicts
        if (leftVal instanceof Literal) {
            Variable leftTemp = getTemp();
            addInstruction(new Mov(nextInstructionId(), leftTemp, leftVal));
            leftVal = leftTemp;
        }

        BasicBlock evalRightBlock = new BasicBlock(++blockCounter);
        BasicBlock endBlock = new BasicBlock(++blockCounter);
        BasicBlock current = currentBlock;

        Variable result = getTemp();
        addInstruction(new Mov(nextInstructionId(), result, new Immediate(1)));

        currentCFG.addBlock(evalRightBlock);
        currentCFG.addBlock(endBlock);

        // Logic: IF (Left == True) THEN Done ELSE CheckRight.
        // Bne branches when value != 0 (TRUE), Beq branches when value == 0 (FALSE)
        addInstruction(new Bne(nextInstructionId(), leftVal, endBlock));
        freeTemp(leftVal);

        current.addSuccessor(endBlock);
        current.addSuccessor(evalRightBlock);
        endBlock.addPredecessor(current);
        evalRightBlock.addPredecessor(current);

        currentBlock = evalRightBlock;
        node.getRight().accept(this);

        Value rightVal = loadIfNeeded(valueStack.pop());

        addInstruction(new Mov(nextInstructionId(), result, rightVal));
        freeTemp(rightVal);

        addInstruction(new Bra(nextInstructionId(), endBlock));
        
        currentBlock.addSuccessor(endBlock);
        endBlock.addPredecessor(currentBlock);

        currentBlock = endBlock;
        valueStack.push(result);
    }
    */

    @Override
    public void visit(LogicalAnd node) {
        node.getLeft().accept(this);
        Value leftVal = loadIfNeeded(valueStack.pop());

        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());

        Variable result = getTemp();
        addInstruction(new And(nextInstructionId(), result, leftVal, rightVal));

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(result);
    }

    @Override
    public void visit(LogicalOr node) {
        node.getLeft().accept(this);
        Value leftVal = loadIfNeeded(valueStack.pop());

        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());

        Variable result = getTemp();
        addInstruction(new Or(nextInstructionId(), result, leftVal, rightVal));

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(result);
    }
    
    // ==========================================
    // Non-Commutative Operations (Sub, Div, Mod, Pow, Cmp)
    // Strategy: MUST Materialize Temp if Left is Immediate
    // ==========================================

    @Override
    public void visit(Subtraction node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        boolean isFloat = isFloat(leftVal) || isFloat(rightVal);
        Variable temp = getTemp(isFloat);

        if (isImmediate(leftVal)) {
            // Cannot swap. Must Load 12 into Reg.
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal, isFloat));
            addInstruction(new Sub(nextInstructionId(), temp, immTemp, rightVal, isFloat));
            freeTemp(immTemp);
        } else {
            addInstruction(new Sub(nextInstructionId(), temp, leftVal, rightVal, isFloat));
        }

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(Division node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        boolean isFloat = isFloat(leftVal) || isFloat(rightVal);
        Variable temp = getTemp(isFloat);

        if (isImmediate(leftVal)) {
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal, isFloat));
            addInstruction(new Div(nextInstructionId(), temp, immTemp, rightVal, isFloat));
            freeTemp(immTemp);
        } else {
            addInstruction(new Div(nextInstructionId(), temp, leftVal, rightVal, isFloat));
        }

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(Modulo node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        // Modulo is typically integer-only
        boolean isFloat = isFloat(leftVal) || isFloat(rightVal);
        Variable temp = getTemp(isFloat);

        if (isImmediate(leftVal)) {
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal, isFloat));
            addInstruction(new Mod(nextInstructionId(), temp, immTemp, rightVal, isFloat));
            freeTemp(immTemp);
        } else {
            addInstruction(new Mod(nextInstructionId(), temp, leftVal, rightVal, isFloat));
        }

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(Power node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        boolean isFloat = isFloat(leftVal) || isFloat(rightVal);
        Variable temp = getTemp(isFloat);

        if (isImmediate(leftVal)) {
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal, isFloat));
            addInstruction(new Pow(nextInstructionId(), temp, immTemp, rightVal));
            freeTemp(immTemp);
        } else {
            addInstruction(new Pow(nextInstructionId(), temp, leftVal, rightVal));
        }

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(Relation node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        boolean isFloat = isFloat(leftVal) || isFloat(rightVal);
        // Result of CMP is always boolean (int 0/1)
        Variable temp = getTemp();
        String cmpOp = getComparisonOp(node.getOperator().kind());

        if (isImmediate(leftVal)) {
            // We could flip to a > 5, but we'd have to flip the string 'cmpOp'.
            // Safer to just materialize 5 into a register.
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal, isFloat));
            addInstruction(new Cmp(nextInstructionId(), temp, immTemp, rightVal, cmpOp, isFloat));
            freeTemp(immTemp);
        } else {
            addInstruction(new Cmp(nextInstructionId(), temp, leftVal, rightVal, cmpOp, isFloat));
        }

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    // ==========================================
    // Unary Operations
    // ==========================================

    @Override
    public void visit(LogicalNot node) {
        node.operand().accept(this);
        Value operandVal = loadIfNeeded(valueStack.pop());
        Variable resultTemp = getTemp();

        if (isImmediate(operandVal)) {
            // Ensure CodeGen doesn't fail on "Not Immediate"
            Variable immTemp = getTemp();
            addInstruction(new Mov(nextInstructionId(), immTemp, operandVal));
            addInstruction(new Not(nextInstructionId(), resultTemp, immTemp));
            freeTemp(immTemp);
        } else {
            addInstruction(new Not(nextInstructionId(), resultTemp, operandVal));
        }

        freeTemp(operandVal);
        valueStack.push(resultTemp);
    }

    private String getComparisonOp(Token.Kind kind) {
        return switch (kind) {
            case EQUAL_TO -> "eq";
            case NOT_EQUAL -> "ne";
            case LESS_THAN -> "lt";
            case LESS_EQUAL -> "le";
            case GREATER_THAN -> "gt";
            case GREATER_EQUAL -> "ge";
            default -> "eq";
        };
    }

    @Override
    public void visit(FunctionCallExpression node) {
        String funcName = node.name().lexeme();

        switch (funcName) {
            case "readInt", "readFloat", "readBool" -> {
                Variable temp = getTemp();
                boolean isFloat = funcName.equals("readFloat");
                if (funcName.equals("readBool")) {
                    addInstruction(new ReadB(nextInstructionId(), temp));
                } else {
                    addInstruction(new Read(nextInstructionId(), temp, isFloat));
                }
                valueStack.push(temp);
            }
            case "printInt", "printFloat", "printBool" -> {
                node.arguments().accept(this);
                Value arg = loadIfNeeded(valueStack.pop());
                boolean isFloat = funcName.equals("printFloat");
                if (funcName.equals("printBool")) {
                    addInstruction(new WriteB(nextInstructionId(), arg));
                } else {
                    addInstruction(new Write(nextInstructionId(), arg, isFloat));
                }
            }
            case "println" -> addInstruction(new WriteNL(nextInstructionId()));
            default -> {
                List<Value> args = new ArrayList<>();
                List<Type> argTypes = new ArrayList<>();

                if (node.arguments() != null && node.arguments().args() != null) {
                    for (Expression argExpr : node.arguments().args()) {
                        argExpr.accept(this);
                        Value argVal = valueStack.pop();
                        
                        // CRITICAL FIX: Don't load arrays - we want the ADDRESS!
                        Type argType = getType(argVal);
                        if (argType instanceof ArrayType) {
                            args.add(argVal);
                        } else {
                            args.add(loadIfNeeded(argVal));
                        }
                        argTypes.add(argType);
                    }
                }

                storeUsedGlobals();

                // Lookup specific function symbol based on name AND argument types
                Symbol funcSym = symbolTable.lookupFunction(funcName, argTypes);

                Variable returnTemp = getTemp();
                if (funcSym.type() instanceof FuncType) {
                    returnTemp.getSymbol().setType(((FuncType) funcSym.type()).getReturnType());
                }
                addInstruction(new Call(nextInstructionId(), returnTemp, funcSym, args));

                loadAllGlobals();

                for (Value arg : args) {
                    freeTemp(arg);
                }

                valueStack.push(returnTemp);
            }
        }
    }

    private Type getType(Value v) {
        if (v instanceof Variable) {
            return ((Variable) v).getSymbol().type();
        } else if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            if (val instanceof Integer)
                return new IntType();
            if (val instanceof Float)
                return new FloatType();
            if (val instanceof Boolean)
                return new BoolType();
        } else if (v instanceof Literal) {
            ast.Expression expr = ((Literal) v).getValue();
            if (expr instanceof ast.IntegerLiteral)
                return new IntType();
            if (expr instanceof ast.FloatLiteral)
                return new FloatType();
            if (expr instanceof ast.BoolLiteral)
                return new BoolType();
        }
        throw new RuntimeException("Unknown type for value: " + v);
    }

    @Override
    public void visit(FunctionCallStatement node) {
        node.getFunctionCall().accept(this);
        if (!valueStack.isEmpty()) {
            freeTemp(valueStack.pop());
        }
    }

    @Override
    public void visit(ArgumentList node) {
        for (Expression arg : node.args()) {
            arg.accept(this);
        }
    }

    @Override
    public void visit(Assignment node) {
        Token op = node.getOperator();
        Expression dest = node.getDestination();
        Expression source = node.getSource();

        Value rhsValue;
        if (source != null) {
            source.accept(this);
            rhsValue = loadIfNeeded(valueStack.pop());
        } else {
            // Unary assignment (a++, a--) implies RHS is 1
            rhsValue = new Immediate(1);
        }

        if (dest instanceof Designator) {
            handleVariableAssignment((Designator) dest, op, rhsValue);
        } else if (dest instanceof ArrayIndex) {
            handleArrayAssignment((ArrayIndex) dest, op, rhsValue);
        } else {
            throw new RuntimeException("Unsupported assignment destination: " + dest.getClass().getSimpleName());
        }

        freeTemp(rhsValue);
    }

    private void handleVariableAssignment(Designator dest, Token op, Value rhs) {
        String varName = dest.name().lexeme();
        Symbol sym = symbolTable.lookup(varName);
        Variable targetVar = new Variable(sym);

        if (sym.isGlobal()) {
            usedGlobalsInFunction.add(sym);
            // initializedGlobals.add(sym); // Moved down
        } else {
            // initializedLocals.add(sym); // Moved down
        }

        if (op.kind() == Token.Kind.ASSIGN) {
            if (sym.isGlobal()) initializedGlobals.add(sym);
            else initializedLocals.add(sym);

            boolean isFloat = isFloat(targetVar) || isFloat(rhs);
            addInstruction(new Mov(nextInstructionId(), targetVar, rhs, isFloat));
        } else {
            // Compound assignment reads the variable first, so check initialization
            targetVar = (Variable) loadIfNeeded(targetVar);

            if (sym.isGlobal()) initializedGlobals.add(sym);
            else initializedLocals.add(sym);
            
            boolean isFloat = isFloat(targetVar) || isFloat(rhs);
            Variable resultTemp = getTemp(isFloat);

            switch (op.kind()) {
                case ADD_ASSIGN:
                case UNI_INC:
                    addInstruction(new Add(nextInstructionId(), resultTemp, targetVar, rhs, isFloat));
                    break;
                case SUB_ASSIGN:
                case UNI_DEC:
                    addInstruction(new Sub(nextInstructionId(), resultTemp, targetVar, rhs, isFloat));
                    break;
                case MUL_ASSIGN:
                    addInstruction(new Mul(nextInstructionId(), resultTemp, targetVar, rhs, isFloat));
                    break;
                case DIV_ASSIGN:
                    addInstruction(new Div(nextInstructionId(), resultTemp, targetVar, rhs, isFloat));
                    break;
                case MOD_ASSIGN:
                    addInstruction(new Mod(nextInstructionId(), resultTemp, targetVar, rhs, isFloat));
                    break;
                case POW_ASSIGN:
                    addInstruction(new Pow(nextInstructionId(), resultTemp, targetVar, rhs));
                    break;
                default:
                    throw new RuntimeException("Unknown variable assignment operator: " + op);
            }

            addInstruction(new Mov(nextInstructionId(), targetVar, resultTemp, isFloat));
            freeTemp(resultTemp);
        }

        if (sym.isGlobal()) {
            initializedGlobals.add(sym);
        } else {
            initializedLocals.add(sym);
        }
    }

    private void handleArrayAssignment(ArrayIndex dest, Token op, Value rhs) {
        dest.base().accept(this);
        dest.index().accept(this);
        Value indexVal = loadIfNeeded(valueStack.pop());
        Value baseVal = valueStack.pop();

        // CRITICAL: must match visit(ArrayIndex) scaling
        int elementSize = calculateElementSize(dest.base());

        Value scaledIndex;
        if (isImmediate(indexVal) && isZero(indexVal)) {
            scaledIndex = new Immediate(0);
        } else if (isImmediate(indexVal)) {
            int val = getImmediateValue(indexVal);
            scaledIndex = new Immediate(val * elementSize);
        } else {
            Variable tempScaled = getTemp();
            addInstruction(new Mul(nextInstructionId(), tempScaled, indexVal, new Immediate(elementSize), false));
            scaledIndex = tempScaled;
        }

        Variable addrTemp = getTemp();
        addInstruction(new Adda(nextInstructionId(), addrTemp, baseVal, scaledIndex));

        if (op.kind() == Token.Kind.ASSIGN) {
            addInstruction(new Store(nextInstructionId(), rhs, addrTemp));
        } else {
            // Logic: val = load(addr); val = val + 5; store(val, addr);
            Variable currentVal = getTemp();
            addInstruction(new Load(nextInstructionId(), currentVal, addrTemp));

            boolean isFloat = isFloat(currentVal) || isFloat(rhs);
            Variable resultTemp = getTemp(isFloat);

            switch (op.kind()) {
                case ADD_ASSIGN:
                case UNI_INC:
                    addInstruction(new Add(nextInstructionId(), resultTemp, currentVal, rhs, isFloat));
                    break;
                case SUB_ASSIGN:
                case UNI_DEC:
                    addInstruction(new Sub(nextInstructionId(), resultTemp, currentVal, rhs, isFloat));
                    break;
                case MUL_ASSIGN:
                    addInstruction(new Mul(nextInstructionId(), resultTemp, currentVal, rhs, isFloat));
                    break;
                case DIV_ASSIGN:
                    addInstruction(new Div(nextInstructionId(), resultTemp, currentVal, rhs, isFloat));
                    break;
                case MOD_ASSIGN:
                    addInstruction(new Mod(nextInstructionId(), resultTemp, currentVal, rhs, isFloat));
                    break;
                case POW_ASSIGN:
                    addInstruction(new Pow(nextInstructionId(), resultTemp, currentVal, rhs));
                    break;
                default:
                    throw new RuntimeException("Unknown array assignment operator: " + op);
            }

            addInstruction(new Store(nextInstructionId(), resultTemp, addrTemp));

            freeTemp(currentVal);
            freeTemp(resultTemp);
        }

        freeTemp(addrTemp);
        freeTemp(indexVal);
        if (scaledIndex instanceof Variable)
            freeTemp(scaledIndex);
        freeTemp(baseVal);
    }

    @Override
    public void visit(Computation node) {
        node.variables().declarations().forEach(decl -> decl.accept(this));
        node.functions().declarations().forEach(func -> func.accept(this));

        // CRITICAL FIX: Do NOT reset blockCounter! IDs must be globally unique.
        Symbol mainSymbol = new Symbol("main");
        currentCFG = new CFG(mainSymbol);
        BasicBlock entry = new BasicBlock(++blockCounter);
        currentCFG.setEntryBlock(entry);
        currentCFG.addBlock(entry);
        currentBlock = entry;

        // Reset global tracking for main's CFG (each CFG tracks independently)
        initializedGlobals = new HashSet<>();
        needsDefaultInitGlobals = new HashSet<>();
        usedGlobalsInFunction = new HashSet<>();  // Track which globals are used in main

        // loadUsedGlobals(); // Deferred to insertEntryLoads at end
        
        // Remember the entry block and insertion point for default initializations
        BasicBlock entryBlock = currentBlock;
        int insertionPoint = entryBlock.getInstructions().size();

        node.mainStatementSequence().accept(this);
        
        // Insert them at the beginning of the ENTRY block (after loadAllGlobals)
        List<TAC> defaultInits = new ArrayList<>();
        for (Symbol global : needsDefaultInitGlobals) {
            Variable var = new Variable(global);
            initializeVariableToDefault(var);
            defaultInits.add(currentBlock.getInstructions().remove(currentBlock.getInstructions().size() - 1));
        }
        
        for (int i = defaultInits.size() - 1; i >= 0; i--) {
            entryBlock.getInstructions().add(insertionPoint, defaultInits.get(i));
        }
        
        // Optimization: For MAIN, globals are fresh and 0-initialized by runtime.
        // Instead of loading from memory (which returns unknown), explicitly initialize to 0.
        // This allows Constant Propagation to assume they are 0.
        List<TAC> globalInits = new ArrayList<>();
        for (Symbol global : globalNonArrayVars) {
            if (usedGlobalsInFunction.contains(global)) {
                Variable var = new Variable(global);
                // Same logic as initializeVariableToDefault but for globals at start
                Type type = global.type();
                Value defaultValue;
                if (type instanceof IntType) defaultValue = new Immediate(0);
                else if (type instanceof FloatType) defaultValue = new Immediate(0.0);
                else if (type instanceof BoolType) defaultValue = new Immediate(0);
                else defaultValue = new Immediate(0);
                
                globalInits.add(new Mov(nextInstructionId(), var, defaultValue));
                
                // Mark as initialized so we don't warn about them or re-init
                initializedGlobals.add(global);
            }
        }
        entryBlock.getInstructions().addAll(0, globalInits);

        addInstruction(new End(nextInstructionId()));
        cfgs.add(currentCFG);
    }

    @Override
    public void visit(StatementSequence node) {
        node.getStatements().forEach(stmt -> stmt.accept(this));
    }

    @Override
    public void visit(IfStatement node) {
        node.condition().accept(this);
        Value conditionValue = loadIfNeeded(valueStack.pop());

        BasicBlock thenBlock = new BasicBlock(++blockCounter);
        BasicBlock joinBlock = new BasicBlock(++blockCounter);
        BasicBlock elseBlock = node.elseBlock() != null ? new BasicBlock(++blockCounter) : null;

        currentCFG.addBlock(thenBlock);
        currentCFG.addBlock(joinBlock);
        if (elseBlock != null)
            currentCFG.addBlock(elseBlock);

        BasicBlock branchBlock = currentBlock;

        // CRITICAL: beq branches when condition == 0 (FALSE)
        // Fall-through happens when condition != 0 (TRUE)
        BasicBlock falseTarget = elseBlock != null ? elseBlock : joinBlock;

        addInstruction(new Beq(nextInstructionId(), conditionValue, falseTarget));
        // Explicit jump to THEN to handle block reordering
        addInstruction(new Bra(nextInstructionId(), thenBlock));
        freeTemp(conditionValue);

        branchBlock.addSuccessor(thenBlock);
        thenBlock.addPredecessor(branchBlock);
        branchBlock.addSuccessor(falseTarget);
        falseTarget.addPredecessor(branchBlock);

        currentBlock = thenBlock;
        node.thenBlock().accept(this);
        addInstruction(new Bra(nextInstructionId(), joinBlock));
        currentBlock.addSuccessor(joinBlock);
        joinBlock.addPredecessor(currentBlock);

        if (elseBlock != null) {
            currentBlock = elseBlock;
            node.elseBlock().accept(this);
            addInstruction(new Bra(nextInstructionId(), joinBlock));
            currentBlock.addSuccessor(joinBlock);
            joinBlock.addPredecessor(currentBlock);
        }

        currentBlock = joinBlock;
    }

    @Override
    public void visit(WhileStatement node) {
        BasicBlock loopHeader = new BasicBlock(++blockCounter);
        BasicBlock loopBody = new BasicBlock(++blockCounter);
        BasicBlock loopExit = new BasicBlock(++blockCounter);

        currentCFG.addBlock(loopHeader);
        currentCFG.addBlock(loopBody);
        currentCFG.addBlock(loopExit);

        addInstruction(new Bra(nextInstructionId(), loopHeader));
        currentBlock.addSuccessor(loopHeader);
        loopHeader.addPredecessor(currentBlock);

        currentBlock = loopHeader;
        node.condition().accept(this);
        Value conditionValue = loadIfNeeded(valueStack.pop());

        // Branch to exit if condition is FALSE (beq means "branch if zero")
        addInstruction(new Beq(nextInstructionId(), conditionValue, loopExit));
        addInstruction(new Bra(nextInstructionId(), loopBody));
        freeTemp(conditionValue);
        loopHeader.addSuccessor(loopExit);
        loopHeader.addSuccessor(loopBody);
        loopExit.addPredecessor(loopHeader);
        loopBody.addPredecessor(loopHeader);

        currentBlock = loopBody;
        node.body().accept(this);
        addInstruction(new Bra(nextInstructionId(), loopHeader));
        currentBlock.addSuccessor(loopHeader);
        loopHeader.addPredecessor(currentBlock);

        currentBlock = loopExit;
    }

    @Override
    public void visit(RepeatStatement node) {
        BasicBlock bodyBlock = new BasicBlock(++blockCounter);
        BasicBlock exitBlock = new BasicBlock(++blockCounter);

        currentCFG.addBlock(bodyBlock);
        currentCFG.addBlock(exitBlock);

        currentBlock.addSuccessor(bodyBlock);
        bodyBlock.addPredecessor(currentBlock);

        currentBlock = bodyBlock;
        node.body().accept(this);
        node.condition().accept(this);
        Value conditionValue = loadIfNeeded(valueStack.pop());

        // The issue is the Beq should be: if TRUE, go to exit; if FALSE, go to body
        // So: Bne conditionValue exitBlock (if TRUE, exit)
        addInstruction(new Bne(nextInstructionId(), conditionValue, exitBlock));
        addInstruction(new Bra(nextInstructionId(), bodyBlock));
        freeTemp(conditionValue);
        // CRITICAL FIX: Add edges from currentBlock (where the branch actually is), not bodyBlock
        // This is important for nested loops where currentBlock != bodyBlock after processing body
        currentBlock.addSuccessor(bodyBlock);
        bodyBlock.addPredecessor(currentBlock);
        currentBlock.addSuccessor(exitBlock);
        exitBlock.addPredecessor(currentBlock);

        currentBlock = exitBlock;
    }

    @Override
    public void visit(ReturnStatement node) {
        if (node.value() != null) {
            node.value().accept(this);
            Value returnVal = loadIfNeeded(valueStack.pop());
            storeUsedGlobals();
            addInstruction(new Return(nextInstructionId(), returnVal));
        } else {
            storeUsedGlobals();
            addInstruction(new Return(nextInstructionId(), null));
        }
    }


    @Override
    public void visit(VariableDeclaration node) {
        for (Token name : node.names()) {
            try {
                Symbol sym = symbolTable.insert(name.lexeme(), node.type());

                // Globals are already handled in assignGlobalOffsets
                if (!sym.isGlobal()) {
                    int size = calculateSize(node.type());
                    fpOffset -= size;
                    sym.setFpOffset(fpOffset);
                }
            } catch (Error e) {
                // Ignore re-declaration errors in IR gen (already caught in semantic analysis)
            }
        }
    }

    @Override
    public void visit(FunctionDeclaration node) {
        fpOffset = 0;
        // Skip FP, RA, and return value slot at FP+8
        paramOffset = 12;
        initializedLocals = new HashSet<>();
        needsDefaultInitLocals = new HashSet<>();
        
        // Reset global tracking for this function's CFG (each CFG tracks independently)
        initializedGlobals = new HashSet<>();
        needsDefaultInitGlobals = new HashSet<>();
        usedGlobalsInFunction = new HashSet<>();  // Track which globals are used in this function
        
        freeTemps = new Stack<>();

        symbolTable.enterScope();

        List<Symbol> params = new ArrayList<>();
        for (Symbol param : node.formals()) {
            try {
                Symbol sym = symbolTable.insert(param.name(), param.type());
                sym.setParameter(true);
                sym.setFpOffset(paramOffset);
                // All params are 4 bytes (pointers or scalars)
                paramOffset += 4;

                initializedLocals.add(sym);
                params.add(sym);
            } catch (Error e) {
            }
        }

        // Lookup function symbol for CFG (handles overloading)
        List<Type> paramTypes = new ArrayList<>();
        for (Symbol param : node.formals()) {
            paramTypes.add(param.type());
        }

        Symbol currentFunctionSymbol;
        try {
            currentFunctionSymbol = symbolTable.lookupFunction(node.name().lexeme(), paramTypes);
        } catch (Error e) {
            throw new RuntimeException("Function symbol not found during IR generation: " + node.name().lexeme());
        }

        currentCFG = new CFG(currentFunctionSymbol);
        BasicBlock entry = new BasicBlock(++blockCounter);
        currentCFG.setEntryBlock(entry);
        currentCFG.addBlock(entry);
        currentBlock = entry;

        // loadUsedGlobals(); // Deferred to insertEntryLoads at end
        loadAllParams(params);
        
        // Remember the entry block and insertion point for default initializations
        BasicBlock entryBlock = currentBlock;
        int insertionPoint = entryBlock.getInstructions().size();

        if (node.body() != null) {
            node.body().accept(this);
        }
        
        // Insert them at the beginning of the ENTRY block (after loadAllGlobals and loadAllParams)
        List<TAC> defaultInits = new ArrayList<>();
        for (Symbol local : needsDefaultInitLocals) {
            Variable var = new Variable(local);
            initializeVariableToDefault(var);
            defaultInits.add(currentBlock.getInstructions().remove(currentBlock.getInstructions().size() - 1));
        }
        
        for (int i = defaultInits.size() - 1; i >= 0; i--) {
            entryBlock.getInstructions().add(insertionPoint, defaultInits.get(i));
        }

        // Optimization: Insert Loads for used globals
        insertEntryLoads(entryBlock, 0);

        // Ensure globals are stored before implicit return/end
        // Ensure globals are stored before implicit return/end
        storeUsedGlobals();
        addInstruction(new End(nextInstructionId()));
        
        // CRITICAL FIX: Set frame size so CodeGenerator allocates stack references
        currentCFG.setFrameSize(Math.abs(fpOffset));
        cfgs.add(currentCFG);

        symbolTable.exitScope();
    }

    @Override
    public void visit(FunctionBody node) {
        node.locals().forEach(decl -> decl.accept(this));
        node.statements().accept(this);
    }

    @Override
    public void visit(DeclarationList node) {
    }

    private void addInstruction(TAC instruction) {
        currentBlock.addInstruction(instruction);
    }
}
