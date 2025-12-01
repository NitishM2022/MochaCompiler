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

    // Offsets
    private int fpOffset; // locals/temps (grows negative from 0)
    private int gpOffset; // globals (grows positive from 0)
    private int paramOffset; // parameters (grows positive from 8)

    // Temp recycling
    private Stack<Variable> freeTemps;

    // Uninitialized tracking
    private Set<Symbol> initializedGlobals;
    private Set<Symbol> initializedLocals;

    // Global tracking
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
            return ((ArrayType) type).getAllocationSize();
        }
        return 4; // Default size for scalar types
    }

    private void loadAllGlobals() {
        for (Symbol global : globalNonArrayVars) {
            Variable var = new Variable(global);
            addInstruction(new LoadGP(nextInstructionId(), var, global.getGlobalOffset()));
            initializedGlobals.add(global);
        }
    }

    private void storeAllGlobals() {
        for (Symbol global : globalNonArrayVars) {
            if (initializedGlobals.contains(global)) {
                Variable var = new Variable(global);
                addInstruction(new StoreGP(nextInstructionId(), var, global.getGlobalOffset()));
            }
        }
    }

    private void loadAllParams(List<Symbol> params) {
        for (Symbol param : params) {
            if (param.type() instanceof ArrayType) {
                // Array parameter is just an address - already loaded
                continue;
            } else {
                // Load non-array parameter from stack
                Variable paramVar = new Variable(param);
                addInstruction(new LoadFP(nextInstructionId(), paramVar, param.getFpOffset()));
            }
        }
    }

    private int nextInstructionId() {
        return ++instructionCounter;
    }

    private Variable getTemp() {
        if (!freeTemps.isEmpty()) {
            Variable temp = freeTemps.pop();
            // Type will be set by caller if needed
            return temp;
        } else {
            // Create new temp with new number
            int tempNum = nextTempNumber++;
            fpOffset -= 4;
            int offset = fpOffset;

            Symbol tempSym = new Symbol("$t" + tempNum);
            tempSym.setFpOffset(offset);
            // No type set - caller will set if needed

            return new Variable(tempSym, true, tempNum);
        }
    }

    private Variable getTemp(boolean isFloat) {
        Variable temp = getTemp();
        // Set type based on request
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

            // Check if initialized
            boolean isInit = sym.isGlobal()
                    ? initializedGlobals.contains(sym)
                    : initializedLocals.contains(sym);

            if (!isInit) {
                System.err.println("WARNING: Variable '" + sym.name() + "' may be used before initialization");
                initializeVariableToDefault(var);

                // Mark as initialized
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
        // Adjust specific class names based on your AST/Value hierarchy
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
                // Multi-dim: element is a sub-array
                // Return total size of all remaining dimensions
                return ((ArrayType) elemType).getAllocationSize();
            } else {
                // Final dimension: element is scalar (4 bytes for int)
                return 4;
            }
        }

        return 4; // Default for scalars
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
                // Indexing reduces one dimension level
                return ((ArrayType) baseType).getElementType();
            }
        }
        return null;
    }

    // Literals
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

    // Designators
    @Override
    public void visit(Designator node) {
        Symbol sym = symbolTable.lookup(node.name().lexeme());

        if (sym.type() instanceof ArrayType) {
            // === HANDLING ARRAYS ===
            // We must push the BASE ADDRESS of the array onto the stack.

            Variable baseAddr = getTemp();

            if (sym.isParameter()) {
                // Array Parameters are pointers passed by reference.
                // The stack slot at FP+offset ALREADY contains the address.
                // Just load it.
                addInstruction(new LoadFP(nextInstructionId(), baseAddr, sym.getFpOffset()));
            } else if (sym.isGlobal()) {
                // Global Array: The array lives at GP + GlobalOffset.
                // We use AddaGP to calculate this address into a register.
                // (Index is 0 because we just want the start of the array)
                addInstruction(new AddaGP(nextInstructionId(), baseAddr, sym.getGlobalOffset(), new Immediate(0)));
            } else {
                // Local Array: The array lives at FP + FpOffset.
                // We use AddaFP to calculate this address.
                addInstruction(new AddaFP(nextInstructionId(), baseAddr, sym.getFpOffset(), new Immediate(0)));
            }

            valueStack.push(baseAddr);
        } else {
            // === HANDLING SCALARS ===
            // Standard variable usage
            valueStack.push(new Variable(sym));
        }
    }

    @Override
    public void visit(ArrayIndex node) {
        // 1. Get base address
        node.base().accept(this);
        node.index().accept(this);

        Value indexVal = loadIfNeeded(valueStack.pop());
        Value baseAddr = valueStack.pop();

        // 2. Calculate element size based on remaining dimensions
        // For multi-dim arrays, this is critical!
        int elementSize = calculateElementSize(node.base());

        // 3. Scale index by element size
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

        // 4. Calculate element address: base + scaled offset
        Variable elementAddr = getTemp();
        addInstruction(new Adda(nextInstructionId(), elementAddr, baseAddr, scaledIndex));

        // 5. Determine if this is an intermediate access (multi-dim) or final access
        // Intermediate: arr[i] in arr[i][j] -> return ADDRESS
        // Final: arr[i][j] or arr[i] (1D) -> LOAD and return VALUE
        boolean isIntermediateAccess = isIntermediateArrayAccess(node.base());

        if (isIntermediateAccess) {
            // Multi-dim intermediate access - push ADDRESS for next dimension
            valueStack.push(elementAddr);

            // Cleanup (but keep elementAddr on stack)
            if (indexVal != scaledIndex && indexVal instanceof Variable)
                freeTemp(indexVal);
            if (scaledIndex instanceof Variable)
                freeTemp(scaledIndex);
            freeTemp(baseAddr);
        } else {
            // Final dimension or 1D array - LOAD the value
            Variable loadedValue = getTemp();
            addInstruction(new Load(nextInstructionId(), loadedValue, elementAddr));
            valueStack.push(loadedValue);

            // Cleanup
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

    // Arithmetic operations
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
            // Case: 12 + a (Commutative -> a + 12)
            addInstruction(new Add(nextInstructionId(), temp, rightVal, leftVal, isFloat));
        } else if (isImmediate(leftVal)) {
            // Case: 12 + 13 (Should be folded, but if not: t0=12, t0+13)
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal));
            addInstruction(new Add(nextInstructionId(), temp, immTemp, rightVal, isFloat));
            freeTemp(immTemp);
        } else {
            // Case: a + 12 or a + b
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
            // Swap: 5 * a -> a * 5
            addInstruction(new Mul(nextInstructionId(), temp, rightVal, leftVal, isFloat));
        } else if (isImmediate(leftVal)) {
            // Materialize: 5 * 5
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal));
            addInstruction(new Mul(nextInstructionId(), temp, immTemp, rightVal, isFloat));
            freeTemp(immTemp);
        } else {
            addInstruction(new Mul(nextInstructionId(), temp, leftVal, rightVal, isFloat));
        }

        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(LogicalAnd node) {
        // 1. Evaluate Left
        node.getLeft().accept(this);
        Value leftVal = loadIfNeeded(valueStack.pop());

        BasicBlock evalRightBlock = new BasicBlock(++blockCounter);
        BasicBlock endBlock = new BasicBlock(++blockCounter);
        BasicBlock current = currentBlock;

        // STRATEGY: Initialize Result to FALSE (0)
        Variable result = getTemp();
        addInstruction(new Mov(nextInstructionId(), result, new Immediate(0)));

        currentCFG.addBlock(evalRightBlock);
        currentCFG.addBlock(endBlock);

        // --- THE FIX FOR "ONLY BEQ" ---
        // Logic: IF (Left == True) THEN CheckRight ELSE Done.
        
        // 1. Branch if True -> Go to evalRightBlock
        addInstruction(new Beq(nextInstructionId(), leftVal, evalRightBlock));
        freeTemp(leftVal);

        // 2. Fallthrough means Left was False.
        // We are done (result is already 0). Jump to End.
        addInstruction(new Bra(nextInstructionId(), endBlock));

        // Link CFG
        current.addSuccessor(evalRightBlock); // Branch taken (Left was true)
        current.addSuccessor(endBlock);       // Fallthrough (Left was false)
        evalRightBlock.addPredecessor(current);
        endBlock.addPredecessor(current);

        // 3. Evaluate Right (Only reachable if Left was True)
        currentBlock = evalRightBlock;
        node.getRight().accept(this);
        
        // Use currentBlock (NOT evalRightBlock) because the right child might have added blocks
        Value rightVal = loadIfNeeded(valueStack.pop());

        // If we are here, Left was True, so Result = RightVal
        addInstruction(new Mov(nextInstructionId(), result, rightVal));
        freeTemp(rightVal);

        // Jump to End
        addInstruction(new Bra(nextInstructionId(), endBlock));
        
        // Link CFG (Dynamic wiring)
        currentBlock.addSuccessor(endBlock);
        endBlock.addPredecessor(currentBlock);

        // 4. Continue
        currentBlock = endBlock;
        valueStack.push(result);
    }

    @Override
    public void visit(LogicalOr node) {
        // 1. Evaluate Left
        node.getLeft().accept(this);
        Value leftVal = loadIfNeeded(valueStack.pop());

        BasicBlock evalRightBlock = new BasicBlock(++blockCounter);
        BasicBlock endBlock = new BasicBlock(++blockCounter);
        BasicBlock current = currentBlock;

        // STRATEGY: Initialize Result to TRUE (1)
        Variable result = getTemp();
        addInstruction(new Mov(nextInstructionId(), result, new Immediate(1)));

        currentCFG.addBlock(evalRightBlock);
        currentCFG.addBlock(endBlock);

        // --- LOGIC FOR OR ---
        // Logic: IF (Left == True) THEN Done ELSE CheckRight.
        
        // 1. Branch if True -> Go to End (Short Circuit)
        addInstruction(new Beq(nextInstructionId(), leftVal, endBlock));
        freeTemp(leftVal);

        // 2. Fallthrough means Left was False.
        // We must evaluate the Right side.
        
        // Link CFG
        current.addSuccessor(endBlock);       // Branch taken (Left was true)
        current.addSuccessor(evalRightBlock); // Fallthrough (Left was false)
        endBlock.addPredecessor(current);
        evalRightBlock.addPredecessor(current);

        // 3. Evaluate Right
        currentBlock = evalRightBlock;
        node.getRight().accept(this);

        // Use currentBlock (NOT evalRightBlock)
        Value rightVal = loadIfNeeded(valueStack.pop());

        // If we are here, Left was False, so Result = RightVal
        addInstruction(new Mov(nextInstructionId(), result, rightVal));
        freeTemp(rightVal);

        // Jump to End
        addInstruction(new Bra(nextInstructionId(), endBlock));
        
        // Link CFG
        currentBlock.addSuccessor(endBlock);
        endBlock.addPredecessor(currentBlock);

        // 4. Continue
        currentBlock = endBlock;
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
            // Case: 12 - a. Cannot swap. Must Load 12 into Reg.
            // Generates: t0 = 12; t1 = t0 - a;
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal));
            addInstruction(new Sub(nextInstructionId(), temp, immTemp, rightVal, isFloat));
            freeTemp(immTemp);
        } else {
            // Case: a - 12 (Valid SUBI) or a - b
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
            // Case: 12 / a
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal));
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
        boolean isFloat = isFloat(leftVal) || isFloat(rightVal); // Modulo is typically integer-only
        Variable temp = getTemp(isFloat);

        if (isImmediate(leftVal)) {
            // Case: 12 % a
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal));
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
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal));
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
        Variable temp = getTemp(); // Result of CMP is always boolean (int 0/1)
        String cmpOp = getComparisonOp(node.getOperator().kind());

        if (isImmediate(leftVal)) {
            // Case: 5 < a.
            // We could flip to a > 5, but we'd have to flip the string 'cmpOp'.
            // Safer to just materialize 5 into a register.
            Variable immTemp = getTemp(isFloat);
            addInstruction(new Mov(nextInstructionId(), immTemp, leftVal));
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

        // Handle built-in functions
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
                // User-defined function call
                List<Value> args = new ArrayList<>();
                List<Type> argTypes = new ArrayList<>();

                if (node.arguments() != null && node.arguments().args() != null) {
                    for (Expression argExpr : node.arguments().args()) {
                        argExpr.accept(this);
                        Value argVal = loadIfNeeded(valueStack.pop());
                        args.add(argVal);
                        argTypes.add(getType(argVal));
                    }
                }

                storeAllGlobals();

                // Lookup specific function symbol based on name AND argument types
                Symbol funcSym = symbolTable.lookupFunction(funcName, argTypes);

                Variable returnTemp = getTemp();
                if (funcSym.type() instanceof FuncType) {
                    returnTemp.getSymbol().setType(((FuncType) funcSym.type()).getReturnType());
                }
                addInstruction(new Call(nextInstructionId(), returnTemp, funcSym, args));

                loadAllGlobals();

                // Free arguments
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

        // 1. Determine the RHS value
        Value rhsValue;
        if (source != null) {
            // Standard assignment (a = b, a += b)
            source.accept(this);
            rhsValue = loadIfNeeded(valueStack.pop());
        } else {
            // Unary assignment (a++, a--) implies RHS is 1
            rhsValue = new Immediate(1);
        }

        // 2. Dispatch based on destination type
        if (dest instanceof Designator) {
            handleVariableAssignment((Designator) dest, op, rhsValue);
        } else if (dest instanceof ArrayIndex) {
            handleArrayAssignment((ArrayIndex) dest, op, rhsValue);
        } else {
            throw new RuntimeException("Unsupported assignment destination: " + dest.getClass().getSimpleName());
        }

        // Cleanup
        freeTemp(rhsValue);
    }

    private void handleVariableAssignment(Designator dest, Token op, Value rhs) {
        String varName = dest.name().lexeme();
        Symbol sym = symbolTable.lookup(varName);
        Variable targetVar = new Variable(sym);

        if (op.kind() == Token.Kind.ASSIGN) {
            // Case: a = 5
            addInstruction(new Mov(nextInstructionId(), targetVar, rhs));
        } else {
            // Case: a += 5, a++, etc.
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

            // Store result back: targetVar = result
            addInstruction(new Mov(nextInstructionId(), targetVar, resultTemp));
            freeTemp(resultTemp);
        }

        // Mark initialized
        if (sym.isGlobal()) {
            initializedGlobals.add(sym);
        } else {
            initializedLocals.add(sym);
        }
    }

    private void handleArrayAssignment(ArrayIndex dest, Token op, Value rhs) {
        // 1. Calculate Array Address
        dest.base().accept(this);
        dest.index().accept(this);
        Value indexVal = loadIfNeeded(valueStack.pop());
        Value baseVal = valueStack.pop();

        // 2. Calculate element size (CRITICAL: must match visit(ArrayIndex) scaling)
        int elementSize = calculateElementSize(dest.base());

        // 3. Scale Index by element size
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

        // 4. Calculate Element Address
        Variable addrTemp = getTemp();
        addInstruction(new Adda(nextInstructionId(), addrTemp, baseVal, scaledIndex));

        if (op.kind() == Token.Kind.ASSIGN) {
            // Case: A[i] = 5
            addInstruction(new Store(nextInstructionId(), rhs, addrTemp));
        } else {
            // Case: A[i] += 5, A[i]++
            // Logic: val = load(addr); val = val + 5; store(val, addr);

            // 1. Load current value
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

            // 3. Store result back
            addInstruction(new Store(nextInstructionId(), resultTemp, addrTemp));

            freeTemp(currentVal);
            freeTemp(resultTemp);
        }

        // Cleanup
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

        blockCounter = 0;
        Symbol mainSymbol = new Symbol("main");
        currentCFG = new CFG(mainSymbol);
        BasicBlock entry = new BasicBlock(++blockCounter);
        currentCFG.setEntryBlock(entry);
        currentCFG.addBlock(entry);
        currentBlock = entry;

        node.mainStatementSequence().accept(this);
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

        // Determine where to branch when condition is FALSE
        BasicBlock falseTarget = elseBlock != null ? elseBlock : joinBlock;

        // Add the beq instruction (branches on FALSE/zero)
        addInstruction(new Beq(nextInstructionId(), conditionValue, falseTarget));
        freeTemp(conditionValue);

        // Set up CFG edges in the RIGHT ORDER:
        // 1. Fall-through edge (TRUE case) -> then block
        branchBlock.addSuccessor(thenBlock);
        thenBlock.addPredecessor(branchBlock);

        // 2. Branch edge (FALSE case) -> else or join
        branchBlock.addSuccessor(falseTarget);
        falseTarget.addPredecessor(branchBlock);

        // Generate then block
        currentBlock = thenBlock;
        node.thenBlock().accept(this);
        addInstruction(new Bra(nextInstructionId(), joinBlock));
        currentBlock.addSuccessor(joinBlock);
        joinBlock.addPredecessor(currentBlock);

        // Generate else block if it exists
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
        // Create a new loop header block
        BasicBlock loopHeader = new BasicBlock(++blockCounter);
        BasicBlock loopBody = new BasicBlock(++blockCounter);
        BasicBlock loopExit = new BasicBlock(++blockCounter);

        currentCFG.addBlock(loopHeader);
        currentCFG.addBlock(loopBody);
        currentCFG.addBlock(loopExit);

        // Jump from current block to loop header
        addInstruction(new Bra(nextInstructionId(), loopHeader));
        currentBlock.addSuccessor(loopHeader);
        loopHeader.addPredecessor(currentBlock);

        // Generate condition in loop header
        currentBlock = loopHeader;
        node.condition().accept(this);
        Value conditionValue = loadIfNeeded(valueStack.pop());

        // Branch to exit if condition is FALSE (beq means "branch if zero")
        addInstruction(new Beq(nextInstructionId(), conditionValue, loopExit));
        freeTemp(conditionValue);
        loopHeader.addSuccessor(loopExit);
        loopHeader.addSuccessor(loopBody);
        loopExit.addPredecessor(loopHeader);
        loopBody.addPredecessor(loopHeader);

        // Generate loop body
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

        addInstruction(new Beq(nextInstructionId(), conditionValue, bodyBlock));
        freeTemp(conditionValue);
        bodyBlock.addSuccessor(bodyBlock);
        bodyBlock.addPredecessor(bodyBlock);
        bodyBlock.addSuccessor(exitBlock);
        exitBlock.addPredecessor(bodyBlock);

        currentBlock = exitBlock;
    }

    @Override
    public void visit(ReturnStatement node) {
        if (node.value() != null) {
            node.value().accept(this);
            Value returnVal = loadIfNeeded(valueStack.pop());
            storeAllGlobals();
            addInstruction(new Return(nextInstructionId(), returnVal));
        } else {
            storeAllGlobals();
            addInstruction(new Return(nextInstructionId(), null));
        }
    }


    @Override
    public void visit(VariableDeclaration node) {
        for (Token name : node.names()) {
            try {
                Symbol sym = symbolTable.insert(name.lexeme(), node.type());

                // Assign offset for local variable
                // Globals are already handled in assignGlobalOffsets
                if (!sym.isGlobal()) {
                    int size = calculateSize(node.type());
                    fpOffset -= size;
                    sym.setFpOffset(fpOffset);

                    // Add to locals tracking
                    // initializedLocals.add(sym); // Locals are NOT initialized by default
                }
            } catch (Error e) {
                // Ignore re-declaration errors in IR gen (already caught in semantic analysis)
            }
        }
    }

    @Override
    public void visit(FunctionDeclaration node) {
        // Reset state for new function
        fpOffset = 0;
        paramOffset = 12; // Skip FP, RA, and return value slot at FP+8
        initializedLocals = new HashSet<>();
        freeTemps = new Stack<>();

        symbolTable.enterScope();

        // Handle parameters
        List<Symbol> params = new ArrayList<>();
        for (Symbol param : node.formals()) {
            try {
                Symbol sym = symbolTable.insert(param.name(), param.type());
                sym.setParameter(true);
                sym.setFpOffset(paramOffset);
                paramOffset += 4; // All params are 4 bytes (pointers or scalars)

                initializedLocals.add(sym); // Parameters are initialized
                params.add(sym);
            } catch (Error e) {
                // Ignore
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

        // Load globals and params at entry
        loadAllGlobals();
        loadAllParams(params);

        if (node.body() != null) {
            node.body().accept(this);
        }

        // Ensure globals are stored before implicit return/end
        storeAllGlobals();
        addInstruction(new End(nextInstructionId()));
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
