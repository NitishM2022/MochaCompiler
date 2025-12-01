package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import ast.*;
import mocha.Symbol;
import mocha.SymbolTable;
import mocha.Token;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;
import ir.tac.Value;
import types.Type;
import types.ArrayType;
import types.BoolType;
import types.IntType;
import types.FloatType;

public class IRGenerator implements NodeVisitor {
    private int instructionCounter;
    private int tempCounter;
    private int blockCounter;
    private List<CFG> cfgs;
    private CFG currentCFG;
    private BasicBlock currentBlock;
    private SymbolTable symbolTable;
    private Stack<Value> valueStack;
    private Stack<Integer> tempStack;
    private Set<Symbol> initializedSymbols;

    // Global Offset Tracking (Relative to R30)
    private int globalOffset = 0;

    // Stack frame layout tracking
    private Map<Integer, Symbol> tempSymbols = new HashMap<>(); 
    private int maxTempIndex = -1; 
    private Set<String> loadedParams = new HashSet<>(); 
    private List<Symbol> localSymbols = new ArrayList<>(); 

    public IRGenerator(SymbolTable symTable) {
        this.instructionCounter = 0;
        this.tempCounter = -1;
        this.blockCounter = 1;
        this.cfgs = new ArrayList<>();
        this.symbolTable = symTable;
        this.valueStack = new Stack<>();
        this.tempStack = new Stack<>();
        this.initializedSymbols = new HashSet<>();
    }

    public List<CFG> generate(AST ast) {
        ast.getComputation().accept(this);
        return cfgs;
    }

    private int nextInstructionId() {
        return ++instructionCounter;
    }

    private Variable getTemp() {
        int index = tempStack.isEmpty() ? ++tempCounter : tempStack.pop();
        maxTempIndex = Math.max(maxTempIndex, index);

        // Cache temp Symbols so they can be reused and have FP offsets assigned
        Symbol sym = tempSymbols.computeIfAbsent(index, i -> {
            Symbol s = new Symbol("$t" + i);
            s.setHasStackSlot(true);
            return s;
        });

        return new Variable(sym, true, index);
    }

    private void freeTemp(Value val) {
        if (val instanceof Variable) {
            Variable var = (Variable) val;
            if (var.isTemp() && var.getTempIndex() >= 0) {
                tempStack.push(var.getTempIndex());
            }
        }
    }

    private Value loadIfNeeded(Value val) {
        if (val instanceof Variable && !((Variable) val).isTemp()) {
            Variable var = (Variable) val;
            Symbol sym = var.getSymbol();

            if (!sym.isParameter() && !initializedSymbols.contains(sym) && !sym.isGlobal()) {
                System.err.println("WARNING: Variable '" + sym.name() + "' used before initialization.");
                initializeVariableToDefault(var);
                return var;
            }
            if(sym.isGlobal()){
                Variable temp = getTemp();
                addInstruction(new LoadGP(nextInstructionId(), temp, sym.getGlobalOffset()));
                return temp;
            }
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
        }else{
            defaultValue = new Immediate(0);
        }

        addInstruction(new Mov(nextInstructionId(), var, defaultValue));
        initializedSymbols.add(sym);
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
        valueStack.push(new Variable(sym));
    }

    @Override
    public void visit(ArrayIndex node) {
        // Delegate to helper
        Variable addrTemp = computeArrayAddress(node);

        // Calculate result type
        Type baseType = getBaseType(node.base());
        Type resultType = null;
        if (baseType instanceof ArrayType) {
            resultType = ((ArrayType) baseType).getElementType();
        }

        // Only load if we're accessing a scalar element
        if (resultType instanceof ArrayType) {
            // Still have more dimensions to index - push the address
            valueStack.push(addrTemp);
        } else {
            // Final dimension - load the actual value
            Variable loadTemp = getTemp();
            addInstruction(new Load(nextInstructionId(), loadTemp, addrTemp));
            valueStack.push(loadTemp);
        }
    }

    private Type getBaseType(Expression base) {
        if (base instanceof Designator) {
            Symbol sym = symbolTable.lookup(((Designator) base).name().lexeme());
            return sym.type();
        } else if (base instanceof ArrayIndex) {
            Type outerType = getBaseType(((ArrayIndex) base).base());
            if (outerType instanceof ArrayType) {
                return ((ArrayType) outerType).getElementType();
            }
        }
        return null;
    }

    // --- FIX: Array Indexing Logic ---
    private Variable computeArrayAddress(ArrayIndex arrIdx) {
        arrIdx.base().accept(this);
        arrIdx.index().accept(this);

        Value indexVal = loadIfNeeded(valueStack.pop());
        Value baseVal = valueStack.pop();

        Type baseType = getBaseType(arrIdx.base());
        int elementSize = 4; 
        if (baseType instanceof ArrayType) {
            Type elemType = ((ArrayType) baseType).getElementType();
            elementSize = getTypeSize(elemType);
        }

        // Use fresh temporaries for math to prevent clobbering other registers
        Variable scaledIndex = getTemp();
        addInstruction(new Mul(nextInstructionId(), scaledIndex, indexVal, new Immediate(elementSize)));

        Variable addrTemp = getTemp();
        addInstruction(new Adda(nextInstructionId(), addrTemp, baseVal, scaledIndex));
        freeTemp(scaledIndex);

        return addrTemp;
    }

    @Override
    public void visit(Dereference node) {
        throw new RuntimeException("Dereference not supported in IR");
    }

    // Arithmetic operations
    @Override
    public void visit(Addition node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        Variable temp = getTemp();
        addInstruction(new Add(nextInstructionId(), temp, leftVal, rightVal));
        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(Subtraction node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        Variable temp = getTemp();
        addInstruction(new Sub(nextInstructionId(), temp, leftVal, rightVal));
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
        Variable temp = getTemp();
        addInstruction(new Mul(nextInstructionId(), temp, leftVal, rightVal));
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
        Variable temp = getTemp();
        addInstruction(new Div(nextInstructionId(), temp, leftVal, rightVal));
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
        Variable temp = getTemp();
        addInstruction(new Mod(nextInstructionId(), temp, leftVal, rightVal));
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
        Variable temp = getTemp();
        addInstruction(new Pow(nextInstructionId(), temp, leftVal, rightVal));
        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(LogicalAnd node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        Variable temp = getTemp();
        addInstruction(new And(nextInstructionId(), temp, leftVal, rightVal));
        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(LogicalOr node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);
        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());
        Variable temp = getTemp();
        addInstruction(new Or(nextInstructionId(), temp, leftVal, rightVal));
        freeTemp(leftVal);
        freeTemp(rightVal);
        valueStack.push(temp);
    }

    @Override
    public void visit(LogicalNot node) {
        node.operand().accept(this);
        Value operandVal = loadIfNeeded(valueStack.pop());
        Variable resultTemp = getTemp();
        addInstruction(new Not(nextInstructionId(), resultTemp, operandVal));
        freeTemp(operandVal);
        valueStack.push(resultTemp);
    }

    @Override
    public void visit(Relation node) {
        node.getLeft().accept(this);
        node.getRight().accept(this);

        Value rightVal = loadIfNeeded(valueStack.pop());
        Value leftVal = loadIfNeeded(valueStack.pop());

        Variable temp = getTemp();
        String cmpOp = getComparisonOp(node.getOperator().kind());
        addInstruction(new Cmp(nextInstructionId(), temp, leftVal, rightVal, cmpOp));

        freeTemp(leftVal);
        freeTemp(rightVal);

        valueStack.push(temp);
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
                if (funcName.equals("readBool")) {
                    addInstruction(new ReadB(nextInstructionId(), temp));
                } else {
                    addInstruction(new Read(nextInstructionId(), temp, funcName.equals("readFloat")));
                }
                valueStack.push(temp);
            }
            case "printInt", "printFloat", "printBool" -> {
                node.arguments().accept(this);
                Value arg = loadIfNeeded(valueStack.pop());
                if (funcName.equals("printBool")) {
                    addInstruction(new WriteB(nextInstructionId(), arg));
                } else {
                    addInstruction(new Write(nextInstructionId(), arg, funcName.equals("printFloat")));
                }
            }
            case "println" -> addInstruction(new WriteNL(nextInstructionId()));
            default -> {
                List<Value> args = new ArrayList<>();
                if (node.arguments() != null && node.arguments().args() != null) {
                    for (Expression argExpr : node.arguments().args()) {
                        argExpr.accept(this);
                        args.add(loadIfNeeded(valueStack.pop()));
                    }
                }

                Variable returnTemp = getTemp();
                addInstruction(new Call(nextInstructionId(), returnTemp,
                        new Symbol(funcName), args));
                valueStack.push(returnTemp);
            }
        }
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
        if (node.getSource() != null) {
            node.getSource().accept(this);
            Value srcValue = loadIfNeeded(valueStack.pop());

            if (node.getDestination() instanceof Designator) {
                String varName = ((Designator) node.getDestination()).name().lexeme();
                Symbol sym = symbolTable.lookup(varName);
                Variable destVar = new Variable(sym);

                Token.Kind opKind = node.getOperator().kind();
                if (opKind != Token.Kind.ASSIGN) {
                    // Compound Assignment (e.g., x += 1)
                    
                    // Determine Source for op (dest = SOURCE + src)
                    Value destValue;
                    if (sym.isGlobal()) {
                        Variable t = getTemp();
                        addInstruction(new LoadGP(nextInstructionId(), t, sym.getGlobalOffset()));
                        destValue = t;
                    } else {
                        destValue = loadIfNeeded(destVar);
                    }

                    Variable result = getTemp();
                    switch (opKind) {
                        case ADD_ASSIGN -> addInstruction(new Add(nextInstructionId(), result, destValue, srcValue));
                        case SUB_ASSIGN -> addInstruction(new Sub(nextInstructionId(), result, destValue, srcValue));
                        case MUL_ASSIGN -> addInstruction(new Mul(nextInstructionId(), result, destValue, srcValue));
                        case DIV_ASSIGN -> addInstruction(new Div(nextInstructionId(), result, destValue, srcValue));
                        case MOD_ASSIGN -> addInstruction(new Mod(nextInstructionId(), result, destValue, srcValue));
                        case POW_ASSIGN -> addInstruction(new Pow(nextInstructionId(), result, destValue, srcValue));
                        default -> throw new RuntimeException("Unknown compound assignment operator: " + opKind);
                    }

                    // Store result back
                    if (sym.isGlobal()) {
                        addInstruction(new StoreGP(nextInstructionId(), result, sym.getGlobalOffset()));
                    } else {
                        addInstruction(new Mov(nextInstructionId(), destVar, result));
                    }
                    freeTemp(result);
                } else {
                    // Simple assignment
                    if (sym.isGlobal()) {
                        addInstruction(new StoreGP(nextInstructionId(), srcValue, sym.getGlobalOffset()));
                    } else {
                        addInstruction(new Mov(nextInstructionId(), destVar, srcValue));
                    }
                }
                
                initializedSymbols.add(sym);
            } else if (node.getDestination() instanceof ArrayIndex) {
                ArrayIndex arrIdx = (ArrayIndex) node.getDestination();

                Variable addrTemp = computeArrayAddress(arrIdx);

                Token.Kind opKind = node.getOperator().kind();
                if (opKind != Token.Kind.ASSIGN) {
                    Variable currentVal = getTemp();
                    addInstruction(new Load(nextInstructionId(), currentVal, addrTemp));

                    Variable result = getTemp();
                    switch (opKind) {
                        case ADD_ASSIGN -> addInstruction(new Add(nextInstructionId(), result, currentVal, srcValue));
                        case SUB_ASSIGN -> addInstruction(new Sub(nextInstructionId(), result, currentVal, srcValue));
                        case MUL_ASSIGN -> addInstruction(new Mul(nextInstructionId(), result, currentVal, srcValue));
                        case DIV_ASSIGN -> addInstruction(new Div(nextInstructionId(), result, currentVal, srcValue));
                        case MOD_ASSIGN -> addInstruction(new Mod(nextInstructionId(), result, currentVal, srcValue));
                        case POW_ASSIGN -> addInstruction(new Pow(nextInstructionId(), result, currentVal, srcValue));
                    }

                    addInstruction(new Store(nextInstructionId(), result, addrTemp));
                    freeTemp(currentVal);
                    freeTemp(result);
                } else {
                    addInstruction(new Store(nextInstructionId(), srcValue, addrTemp));
                }

                freeTemp(addrTemp);
                // Can Arrays really be initialized?
                // We can't initialize arrays, so we don't need to add them to initializedSymbols
            }
        } else {
            handleIncrementDecrement(node.getDestination(), node.getOperator());
        }
    }

    private void handleIncrementDecrement(Expression dest, Token op) {
        boolean isIncrement = op.kind() == Token.Kind.UNI_INC;
        Immediate one = new Immediate(1);

        if (dest instanceof Designator) {
            Symbol sym = symbolTable.lookup(((Designator) dest).name().lexeme());
            Variable destVar = new Variable(sym);

            // Fetch current value
            Value destVal;
            if (sym.isGlobal()) {
                Variable t = getTemp();
                addInstruction(new LoadGP(nextInstructionId(), t, sym.getGlobalOffset()));
                destVal = t;
            } else {
                destVal = destVar;
            }

            Variable result = getTemp();
            addInstruction(isIncrement
                    ? new Add(nextInstructionId(), result, destVal, one)
                    : new Sub(nextInstructionId(), result, destVal, one));

            // Store back
            if (sym.isGlobal()) {
                addInstruction(new StoreGP(nextInstructionId(), result, sym.getGlobalOffset()));
            } else {
                addInstruction(new Mov(nextInstructionId(), destVar, result));
            }

            freeTemp(result);
        } else if (dest instanceof ArrayIndex) {
            ArrayIndex arrIdx = (ArrayIndex) dest;
            Variable addrTemp = computeArrayAddress(arrIdx);

            Variable temp = getTemp();
            addInstruction(new Load(nextInstructionId(), temp, addrTemp));

            Variable result = getTemp();
            addInstruction(isIncrement
                    ? new Add(nextInstructionId(), result, temp, one)
                    : new Sub(nextInstructionId(), result, temp, one));

            addInstruction(new Store(nextInstructionId(), result, addrTemp));

            freeTemp(addrTemp);
            freeTemp(temp);
            freeTemp(result);
        }
    }

    @Override
    public void visit(Computation node) {
        // --- FIX: Reset temp tracking for main ---
        tempSymbols.clear();
        tempCounter = -1;
        maxTempIndex = -1;
        
        node.variables().declarations().forEach(decl -> decl.accept(this));
        node.functions().declarations().forEach(func -> func.accept(this));

        blockCounter = 0;
        currentCFG = new CFG("main");
        BasicBlock entry = new BasicBlock(++blockCounter);
        currentCFG.setEntryBlock(entry);
        currentCFG.addBlock(entry);
        currentBlock = entry;

        node.mainStatementSequence().accept(this);
        addInstruction(new End(nextInstructionId()));
        
        // --- FIX: Calculate Frame Size for MAIN ---
        // Main needs stack space for temporaries to avoid corrupting globals
        int offset = 0;
        for (int i = 0; i <= maxTempIndex; i++) {
            Symbol tempSym = tempSymbols.get(i);
            if (tempSym != null) {
                offset -= 4;
                tempSym.setFpOffset(offset);
            }
        }
        currentCFG.setFrameSize(-offset);
        
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
        BasicBlock falseTarget = elseBlock != null ? elseBlock : joinBlock;
        addInstruction(new Beq(nextInstructionId(), conditionValue, falseTarget));

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

        addInstruction(new Beq(nextInstructionId(), conditionValue, loopExit));
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

        addInstruction(new Beq(nextInstructionId(), conditionValue, bodyBlock));
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
            addInstruction(new Return(nextInstructionId(), returnVal));
        } else {
            addInstruction(new Return(nextInstructionId(), null));
        }

        BasicBlock unreachable = new BasicBlock(++blockCounter);
        currentCFG.addBlock(unreachable);
        currentBlock = unreachable;
    }

    @Override
    public void visit(VariableDeclaration node) {
        for (Token name : node.names()) {
            Symbol sym;
            try {
                sym = symbolTable.insert(name.lexeme(), node.type());
            } catch (Error e) {
                sym = symbolTable.lookup(name.lexeme());
            }
    
            if (sym.isGlobal()) {
                // Calculate Global Offset (Negative per DLX spec)
                int size = getTypeSize(sym.type());
                globalOffset -= size;
                sym.setGlobalOffset(globalOffset);
            } else {
                sym.setHasStackSlot(true);
                // Avoid adding the same symbol to localSymbols twice if it was already there
                if (!localSymbols.contains(sym)) {
                    localSymbols.add(sym);
                }
                
                Variable var = new Variable(sym);
            }
        }
    }
    
    @Override
    public void visit(FunctionDeclaration node) {
        Set<Symbol> savedInitSymbols = initializedSymbols;
        initializedSymbols = new HashSet<>();

        loadedParams.clear();
        tempSymbols.clear();
        tempCounter = -1;
        maxTempIndex = -1;
        localSymbols.clear();

        symbolTable.enterScope();

        List<Symbol> insertedParams = new ArrayList<>();
        int paramIndex = 0;
        
        for (Symbol param : node.formals()) {
            try {
                Symbol insertedSym = symbolTable.insert(param.name(), param.type());
                
                // --- FIX: Standard Parameter Offsets ---
                // Arg 0 at FP+8, Arg 1 at FP+12, etc.
                int paramOffset = 8 + (paramIndex * 4);
                
                insertedSym.setFpOffset(paramOffset);
                insertedSym.setParameter(true);
                insertedSym.setHasStackSlot(true);
                insertedParams.add(insertedSym);
                initializedSymbols.add(insertedSym);
                paramIndex++;
            } catch (Error e) {
                // Ignore
            }
        }

        currentCFG = new CFG(node.name().lexeme());

        for (Symbol param : insertedParams) {
            currentCFG.addParameter(new Variable(param));
        }

        BasicBlock entry = new BasicBlock(++blockCounter);
        currentCFG.setEntryBlock(entry);
        currentCFG.addBlock(entry);
        currentBlock = entry;

        for (Symbol param : insertedParams) {
            Variable var = new Variable(param);
            addInstruction(new LoadFP(nextInstructionId(), var, param.getFpOffset()));
            loadedParams.add(param.name());
        }

        if (node.body() != null) {
            node.body().accept(this);
        }

        addInstruction(new End(nextInstructionId()));

        int offset = 0;
        for (Symbol local : localSymbols) {
            int size = getTypeSize(local.type());
            offset -= size;
            local.setFpOffset(offset);
        }
        for (int i = 0; i <= maxTempIndex; i++) {
            Symbol tempSym = tempSymbols.get(i);
            if (tempSym != null) {
                offset -= 4;
                tempSym.setFpOffset(offset);
            }
        }
        currentCFG.setFrameSize(-offset);

        cfgs.add(currentCFG);

        symbolTable.exitScope();
        initializedSymbols = savedInitSymbols;
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

    private int getTypeSize(Type type) {
        if (type instanceof ArrayType) {
            return ((ArrayType) type).getAllocationSize();
        }
        return 4;
    }
}