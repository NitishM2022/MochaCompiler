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
import ir.tac.Value;
import types.Type;
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
    private Set<String> initializedVariables; // Track initialized variables

    public IRGenerator(SymbolTable symTable) {
        this.instructionCounter = 0;
        this.tempCounter = -1;
        this.blockCounter = 1;
        this.cfgs = new ArrayList<>();
        this.symbolTable = symTable;
        this.valueStack = new Stack<>();
        this.tempStack = new Stack<>();
        this.initializedVariables = new HashSet<>();
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
        return new Variable(new Symbol("$t" + index), true, index);
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
            String varName = var.getSymbol().name();

            if (!initializedVariables.contains(varName)) {
                System.err.println("WARNING: Variable '" + varName + "' may be used before initialization");

                initializeVariableToDefault(var);
                initializedVariables.add(varName);
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
        node.base().accept(this);
        node.index().accept(this);

        Value indexVal = loadIfNeeded(valueStack.pop());
        Value baseVal = valueStack.pop();

        Variable addrTemp = getTemp();
        addInstruction(new Adda(nextInstructionId(), addrTemp, baseVal, indexVal));

        Variable loadTemp = getTemp();
        addInstruction(new Load(nextInstructionId(), loadTemp, addrTemp));

        valueStack.push(loadTemp);
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

        // Handle built-in functions
        switch (funcName) {
            case "readInt", "readFloat", "readBool" -> {
                Variable temp = getTemp();
                addInstruction(funcName.equals("readBool")
                        ? new ReadB(nextInstructionId(), temp)
                        : new Read(nextInstructionId(), temp));
                valueStack.push(temp);
            }
            case "printInt", "printFloat", "printBool" -> {
                node.arguments().accept(this);
                Value arg = loadIfNeeded(valueStack.pop());
                addInstruction(funcName.equals("printBool")
                        ? new WriteB(nextInstructionId(), arg)
                        : new Write(nextInstructionId(), arg));
            }
            case "println" -> addInstruction(new WriteNL(nextInstructionId()));
            default -> {
                // User-defined function call
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

                addInstruction(new Mov(nextInstructionId(), new Variable(sym), srcValue));

                // Mark variable as initialized
                initializedVariables.add(varName);
            } else if (node.getDestination() instanceof ArrayIndex) {
                ArrayIndex arrIdx = (ArrayIndex) node.getDestination();
                arrIdx.base().accept(this);
                arrIdx.index().accept(this);

                Value indexVal = loadIfNeeded(valueStack.pop());
                Value baseVal = valueStack.pop();

                Variable addrTemp = getTemp();
                addInstruction(new Adda(nextInstructionId(), addrTemp, baseVal, indexVal));
                addInstruction(new Store(nextInstructionId(), srcValue, addrTemp));

                // Mark array base as initialized if it's a designator
                if (arrIdx.base() instanceof Designator) {
                    String baseName = ((Designator) arrIdx.base()).name().lexeme();
                    initializedVariables.add(baseName);
                }
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

            Variable result = getTemp();
            addInstruction(isIncrement
                    ? new Add(nextInstructionId(), result, destVar, one)
                    : new Sub(nextInstructionId(), result, destVar, one));

            addInstruction(new Mov(nextInstructionId(), destVar, result));

            freeTemp(result);
        } else if (dest instanceof ArrayIndex) {
            ArrayIndex arrIdx = (ArrayIndex) dest;
            arrIdx.base().accept(this);
            arrIdx.index().accept(this);

            Value indexVal = loadIfNeeded(valueStack.pop());
            Value baseVal = valueStack.pop();

            Variable addrTemp = getTemp();
            addInstruction(new Adda(nextInstructionId(), addrTemp, baseVal, indexVal));

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
        bodyBlock.addSuccessor(bodyBlock);
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

        // Create unreachable block for any code after return
        BasicBlock unreachable = new BasicBlock(++blockCounter);
        currentCFG.addBlock(unreachable);
        currentBlock = unreachable;
    }

    @Override
    public void visit(VariableDeclaration node) {
        for (Token name : node.names()) {
            try {
                symbolTable.insert(name.lexeme(), node.type());
            } catch (Error e) {
                // Ignore
            }
        }
    }

    @Override
    public void visit(FunctionDeclaration node) {
        // Save the current initialized variables set and create a new one for this
        // function
        Set<String> savedInitializedVars = initializedVariables;
        initializedVariables = new HashSet<>();

        symbolTable.enterScope();

        // Add function parameters to initialized variables (they're always initialized
        // by caller)
        for (Symbol param : node.formals()) {
            try {
                symbolTable.insert(param.name(), param.type());
                initializedVariables.add(param.name()); // Parameters are initialized
            } catch (Error e) {
                // Ignore
            }
        }

        currentCFG = new CFG(node.name().lexeme());
        BasicBlock entry = new BasicBlock(++blockCounter);
        currentCFG.setEntryBlock(entry);
        currentCFG.addBlock(entry);
        currentBlock = entry;

        if (node.body() != null) {
            node.body().accept(this);
        }

        addInstruction(new End(nextInstructionId()));
        cfgs.add(currentCFG);

        symbolTable.exitScope();

        // Restore the previous initialized variables set
        initializedVariables = savedInitializedVars;
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