package ir.codegen;

import ir.cfg.BasicBlock;
import ir.cfg.CFG;
import ir.tac.*;
import mocha.DLX;
import types.FloatType;

import java.util.*;

public class CodeGenerator {

    private List<Integer> instructions;
    private int pc;
    private Map<Integer, Integer> blockLabels;
    private Map<String, Integer> functionStartPCs;
    private Map<Integer, Integer> branchFixups;
    private Map<Integer, String> functionFixups;

    public CodeGenerator() {
        this.instructions = new ArrayList<>();
        this.pc = 0;
        this.blockLabels = new HashMap<>();
        this.functionStartPCs = new HashMap<>();
        this.branchFixups = new HashMap<>();
        this.functionFixups = new HashMap<>();
    }

    public int[] generate(List<CFG> cfgs) {
        // Entry point: initialize SP and jump to main
        emit(DLX.ADDI, 29, 30, -2000); // SP = FP - 2000 (globals space)
        int entryJumpPC = pc;
        emit(DLX.JSR, 0); // Placeholder for main
        emit(DLX.RET, 0); // Halt

        // Generate all functions
        for (CFG cfg : cfgs) {
            generateFunction(cfg);
        }
        
        // Fix up main entry jump
        if (!functionStartPCs.containsKey("main")) {
            throw new RuntimeException("No main function found!");
        }
        instructions.set(entryJumpPC, DLX.assemble(DLX.JSR, functionStartPCs.get("main") * 4));

        applyFixups();
        return instructions.stream().mapToInt(i -> i).toArray();
    }

    private void generateFunction(CFG cfg) {
        functionStartPCs.put(cfg.getFunctionName(), pc);
        
        // Prologue
        int frameSize = Math.max(cfg.getFrameSize() + 16, 16);
        emit(DLX.PSH, 31, 29, -4); // Push RA
        emit(DLX.PSH, 28, 29, -4); // Push old FP
        emit(DLX.ADD, 28, 29, 0);  // FP = SP
        emit(DLX.ADDI, 29, 29, -frameSize); // Allocate frame

        // Generate basic blocks
        for (BasicBlock bb : cfg.getAllBlocks()) {
            blockLabels.put(bb.getNum(), pc);
            for (TAC tac : bb.getInstructions()) {
                generateInstruction(tac);
            }
        }
    }

    private void generateInstruction(TAC tac) {
        // Arithmetic operations
        if (tac instanceof Add) generateBinaryOp((Add) tac, DLX.ADD, DLX.ADDI, DLX.fADD, DLX.fADDI);
        else if (tac instanceof Sub) generateBinaryOp((Sub) tac, DLX.SUB, DLX.SUBI, DLX.fSUB, DLX.fSUBI);
        else if (tac instanceof Mul) generateBinaryOp((Mul) tac, DLX.MUL, DLX.MULI, DLX.fMUL, DLX.fMULI);
        else if (tac instanceof Div) generateBinaryOp((Div) tac, DLX.DIV, DLX.DIVI, DLX.fDIV, DLX.fDIVI);
        else if (tac instanceof Mod) generateBinaryOp((Mod) tac, DLX.MOD, DLX.MODI, -1, -1);
        else if (tac instanceof Pow) generateBinaryOp((Pow) tac, DLX.POW, DLX.POWI, -1, -1);
        
        // Address operations
        else if (tac instanceof Adda) generateAdda((Adda) tac);
        else if (tac instanceof LoadFP) {
            LoadFP load = (LoadFP) tac;
            emit(DLX.LDW, getReg(load.getDest()), 28, load.getFpOffset());
        }
        else if (tac instanceof Load) {
            Load load = (Load) tac;
            emit(DLX.LDW, getReg(load.getDest()), getRegOrLoad(load.getOperands().get(0), 27), 0);
        }
        else if (tac instanceof Store) {
            Store store = (Store) tac;
            int srcReg = getRegOrLoad(store.getOperands().get(0), 27);
            int addrReg = getRegOrLoad(store.getOperands().get(1), 26);
            emit(DLX.STW, srcReg, addrReg, 0);
        }
        
        // Move and compare
        else if (tac instanceof Mov) generateMov((Mov) tac);
        else if (tac instanceof Cmp) generateCmp((Cmp) tac);
        
        // Control flow
        else if (tac instanceof Bra) generateBranch(((Bra) tac).getTarget().getNum(), DLX.BEQ, 0);
        else if (tac instanceof Beq) generateBranch(((Beq) tac).getTarget().getNum(), DLX.BEQ, getRegOrLoad(tac.getOperands().get(0), 27));
        else if (tac instanceof Bne) generateBranch(((Bne) tac).getTarget().getNum(), DLX.BNE, getRegOrLoad(tac.getOperands().get(0), 27));
        else if (tac instanceof Blt) generateBranch(((Blt) tac).getTarget().getNum(), DLX.BLT, getRegOrLoad(tac.getOperands().get(0), 27));
        else if (tac instanceof Ble) generateBranch(((Ble) tac).getTarget().getNum(), DLX.BLE, getRegOrLoad(tac.getOperands().get(0), 27));
        else if (tac instanceof Bgt) generateBranch(((Bgt) tac).getTarget().getNum(), DLX.BGT, getRegOrLoad(tac.getOperands().get(0), 27));
        else if (tac instanceof Bge) generateBranch(((Bge) tac).getTarget().getNum(), DLX.BGE, getRegOrLoad(tac.getOperands().get(0), 27));
        
        // Function calls and returns
        else if (tac instanceof Call) generateCall((Call) tac);
        else if (tac instanceof Return) generateReturn((Return) tac);
        else if (tac instanceof End) emitEpilogue();
        
        // I/O operations
        else if (tac instanceof Read) {
            Read read = (Read) tac;
            emit(read.isFloat() ? DLX.RDF : DLX.RDI, getReg(read.getDest()));
        }
        else if (tac instanceof ReadB) emit(DLX.RDB, getReg(((ReadB) tac).getDest()));
        else if (tac instanceof Write) {
            Write write = (Write) tac;
            int reg = getRegOrLoad(write.getOperands().get(0), 27);
            emit(write.isFloat() ? DLX.WRF : DLX.WRI, reg);
        }
        else if (tac instanceof WriteB) {
            int reg = getRegOrLoad(((WriteB) tac).getOperands().get(0), 27);
            emit(DLX.WRB, reg);
        }
        else if (tac instanceof WriteNL) emit(DLX.WRL);
    }

    private void generateBinaryOp(TAC tac, int regOp, int immOp, int fregOp, int fimmOp) {
        int dest = getReg(tac.getDest());
        Value left = tac.getOperands().get(0);
        Value right = tac.getOperands().get(1);
        boolean isFloat = isFloatOperation(tac);

        if (isFloat && fregOp != -1) {
            if (isFloatImmediate(right) && fimmOp != -1) {
                emitFloat(fimmOp, dest, getRegOrLoad(left, 27), getFloatImmediateValue(right));
            } else {
                emit(fregOp, dest, getRegOrLoad(left, 27), getRegOrLoad(right, 26));
            }
        } else {
            if (isImmediate(right) && immOp != -1) {
                emit(immOp, dest, getRegOrLoad(left, 27), getImmediateValue(right));
            } else {
                emit(regOp, dest, getRegOrLoad(left, 27), getRegOrLoad(right, 26));
            }
        }
    }

    private void generateAdda(Adda adda) {
        int dest = getReg(adda.getDest());
        Value base = adda.getOperands().get(0);
        Value offset = adda.getOperands().get(1);
        
        if (offset instanceof Immediate) {
            emit(DLX.ADDI, dest, getRegOrLoad(base, 27), (Integer) ((Immediate) offset).getValue());
        } else {
            emit(DLX.ADD, dest, getRegOrLoad(base, 27), getRegOrLoad(offset, 26));
        }
    }

    private void generateMov(Mov mov) {
        int dest = getReg(mov.getDest());
        Value src = mov.getOperands().get(0);
        
        if (src instanceof Immediate) {
            emit(DLX.ADDI, dest, 0, (Integer) ((Immediate) src).getValue());
        } else {
            emit(DLX.ADD, dest, getRegOrLoad(src, 27), 0);
        }
    }

    private void generateCmp(Cmp cmp) {
        int dest = getReg(cmp.getDest());
        Value left = cmp.getOperands().get(0);
        Value right = cmp.getOperands().get(1);

        // Emit CMP instruction
        if (isImmediate(right)) {
            emit(DLX.CMPI, dest, getRegOrLoad(left, 27), getImmediateValue(right));
        } else {
            emit(DLX.CMP, dest, getRegOrLoad(left, 27), getRegOrLoad(right, 26));
        }

        // Convert CMP result to boolean based on operator
        switch (cmp.getOp()) {
            case "eq": // 0 -> 1, non-zero -> 0
                emit(DLX.ANDI, dest, dest, 1);
                emit(DLX.XORI, dest, dest, 1);
                break;
            case "ne": // non-zero -> 1, 0 -> 0
                emit(DLX.ANDI, dest, dest, 1);
                break;
            case "lt": // -1 -> 1, else -> 0
                emit(DLX.LSHI, dest, dest, -31);
                break;
            case "le": // -1 or 0 -> 1, 1 -> 0
                emit(DLX.SUBI, dest, dest, 1);
                emit(DLX.LSHI, dest, dest, -31);
                break;
            case "gt": // 1 -> 1, else -> 0
                emit(DLX.ADDI, dest, dest, 1);
                emit(DLX.LSHI, dest, dest, -1);
                break;
            case "ge": // 0 or 1 -> 1, -1 -> 0
                emit(DLX.ADDI, dest, dest, 2);
                emit(DLX.LSHI, dest, dest, -1);
                break;
        }
    }

    private void generateBranch(int targetBlockId, int branchOp, int reg) {
        if (blockLabels.containsKey(targetBlockId)) {
            int offset = blockLabels.get(targetBlockId) - pc;
            emit(branchOp, reg, offset);
        } else {
            emit(branchOp, reg, 0);
            branchFixups.put(pc - 1, targetBlockId);
        }
    }

    private void generateCall(Call call) {
        // Save caller-saved registers (R1-R25)
        for (int i = 1; i <= 25; i++) {
            emit(DLX.PSH, i, 29, -4);
        }
        
        // Push arguments
        for (Value arg : call.getArguments()) {
            emit(DLX.PSH, getRegOrLoad(arg, 27), 29, -4);
        }
        
        // Jump to function
        String funcName = call.getFunction().name();
        if (functionStartPCs.containsKey(funcName)) {
            emit(DLX.JSR, functionStartPCs.get(funcName) * 4);
        } else {
            emit(DLX.JSR, 0);
            functionFixups.put(pc - 1, funcName);
        }
        
        // Clean up arguments
        if (!call.getArguments().isEmpty()) {
            emit(DLX.ADDI, 29, 29, call.getArguments().size() * 4);
        }
        
        // Restore caller-saved registers
        for (int i = 25; i >= 1; i--) {
            emit(DLX.POP, i, 29, 4);
        }
        
        // Move return value to destination
        if (call.getDest() != null) {
            emit(DLX.ADD, getReg(call.getDest()), 27, 0);
        }
    }

    private void generateReturn(Return ret) {
        if (!ret.getOperands().isEmpty()) {
            int srcReg = getRegOrLoad(ret.getOperands().get(0), 27);
            if (srcReg != 27) {
                emit(DLX.ADD, 27, srcReg, 0);
            }
        }
        emitEpilogue();
    }

    private void emitEpilogue() {
        emit(DLX.ADD, 29, 28, 0); // SP = FP
        emit(DLX.POP, 28, 29, 4); // Restore FP
        emit(DLX.POP, 31, 29, 4); // Restore RA
        emit(DLX.RET, 31);
    }

    // Helper methods for value handling
    private int getRegOrLoad(Value v, int scratch) {
        if (v instanceof Variable) {
            return getReg(v);
        }
        
        // Load immediate/literal into scratch register
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            if (val instanceof Integer) {
                emit(DLX.ADDI, scratch, 0, (Integer) val);
            } else if (val instanceof Boolean) {
                emit(DLX.ADDI, scratch, 0, (Boolean) val ? 1 : 0);
            } else if (val instanceof Float) {
                emitFloat(DLX.fADDI, scratch, 0, (Float) val);
            } else if (val instanceof Double) {
                emitFloat(DLX.fADDI, scratch, 0, ((Double) val).floatValue());
            }
            return scratch;
        }
        
        if (v instanceof Literal) {
            Literal lit = (Literal) v;
            if (lit.getValue() instanceof ast.IntegerLiteral) {
                emit(DLX.ADDI, scratch, 0, ((ast.IntegerLiteral) lit.getValue()).getValue());
            } else if (lit.getValue() instanceof ast.BoolLiteral) {
                emit(DLX.ADDI, scratch, 0, ((ast.BoolLiteral) lit.getValue()).getValue() ? 1 : 0);
            } else if (lit.getValue() instanceof ast.FloatLiteral) {
                emitFloat(DLX.fADDI, scratch, 0, ((ast.FloatLiteral) lit.getValue()).getValue());
            }
            return scratch;
        }
        
        throw new RuntimeException("Unknown Value type: " + v);
    }

    private int getReg(Value v) {
        if (!(v instanceof Variable)) {
            throw new RuntimeException("Cannot get register for non-variable: " + v);
        }
        
        Variable var = (Variable) v;
        if (var.getSymbol() != null && var.getSymbol().name().startsWith("R")) {
            try {
                return Integer.parseInt(var.getSymbol().name().substring(1));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid register name: " + var.getSymbol().name());
            }
        }
        throw new RuntimeException("Variable not assigned to register: " + var);
    }

    private boolean isImmediate(Value v) {
        return v instanceof Immediate || 
               (v instanceof Literal && ((Literal) v).getValue() instanceof ast.IntegerLiteral);
    }

    private int getImmediateValue(Value v) {
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            return val instanceof Boolean ? ((Boolean) val ? 1 : 0) : (Integer) val;
        }
        if (v instanceof Literal) {
            Literal lit = (Literal) v;
            if (lit.getValue() instanceof ast.IntegerLiteral) {
                return ((ast.IntegerLiteral) lit.getValue()).getValue();
            } else if (lit.getValue() instanceof ast.BoolLiteral) {
                return ((ast.BoolLiteral) lit.getValue()).getValue() ? 1 : 0;
            }
        }
        throw new RuntimeException("Not an immediate: " + v);
    }

    private boolean isFloatOperation(TAC tac) {
        if (tac.getDest() instanceof Variable) {
            Variable dest = (Variable) tac.getDest();
            if (dest.getSymbol() != null && dest.getSymbol().type() instanceof FloatType) {
                return true;
            }
        }
        for (Value op : tac.getOperands()) {
            if (isFloatValue(op)) return true;
        }
        return false;
    }

    private boolean isFloatValue(Value v) {
        if (v instanceof Variable) {
            Variable var = (Variable) v;
            return var.getSymbol() != null && var.getSymbol().type() instanceof FloatType;
        }
        if (v instanceof Literal) {
            return ((Literal) v).getValue() instanceof ast.FloatLiteral;
        }
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            return val instanceof Float || val instanceof Double;
        }
        return false;
    }

    private boolean isFloatImmediate(Value v) {
        if (v instanceof Literal) {
            return ((Literal) v).getValue() instanceof ast.FloatLiteral;
        }
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            return val instanceof Float || val instanceof Double;
        }
        return false;
    }

    private float getFloatImmediateValue(Value v) {
        if (v instanceof Literal && ((Literal) v).getValue() instanceof ast.FloatLiteral) {
            return ((ast.FloatLiteral) ((Literal) v).getValue()).getValue();
        }
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            return val instanceof Float ? (Float) val : ((Double) val).floatValue();
        }
        throw new RuntimeException("Not a float immediate: " + v);
    }

    private void applyFixups() {
        // Fix branch offsets
        for (Map.Entry<Integer, Integer> entry : branchFixups.entrySet()) {
            int instrIdx = entry.getKey();
            int targetBlockId = entry.getValue();
            
            if (!blockLabels.containsKey(targetBlockId)) {
                throw new RuntimeException("Branch to unknown block: " + targetBlockId);
            }
            
            int offset = blockLabels.get(targetBlockId) - instrIdx;
            int oldInstr = instructions.get(instrIdx);
            int op = oldInstr >>> 26;
            int reg = (oldInstr >>> 21) & 0x1F;
            
            instructions.set(instrIdx, DLX.assemble(op, reg, offset));
        }
        
        // Fix function calls
        for (Map.Entry<Integer, String> entry : functionFixups.entrySet()) {
            if (!functionStartPCs.containsKey(entry.getValue())) {
                throw new RuntimeException("Call to unknown function: " + entry.getValue());
            }
            instructions.set(entry.getKey(), 
                DLX.assemble(DLX.JSR, functionStartPCs.get(entry.getValue()) * 4));
        }
    }

    // Emit instructions
    private void emit(int op) {
        instructions.add(DLX.assemble(op));
        pc++;
    }

    private void emit(int op, int a) {
        instructions.add(DLX.assemble(op, a));
        pc++;
    }

    private void emit(int op, int a, int b) {
        instructions.add(DLX.assemble(op, a, b));
        pc++;
    }

    private void emit(int op, int a, int b, int c) {
        instructions.add(DLX.assemble(op, a, b, c));
        pc++;
    }
    
    private void emitFloat(int op, int a, int b, float c) {
        instructions.add(DLX.assemble(op, a, b, c));
        pc++;
    }
}