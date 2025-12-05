package ir.codegen;

import ir.cfg.BasicBlock;
import ir.cfg.CFG;
import ir.tac.*;
import mocha.DLX;
import mocha.Symbol;

import java.util.*;

public class CodeGenerator {
    private static final int R0 = 0;
    private static final int FP = 28;
    private static final int SP = 29;
    private static final int GP = 30;
    private static final int RA = 31;

    // R26, R27 are reserved for register allocator spilling - DO NOT USE
    // Return values are passed via stack slot at FP+8 (above RA and saved FP)

    private List<Integer> instructions;
    private int pc;

    private Map<Integer, Integer> blockPCMap;
    private Map<Symbol, Integer> functionPCMap;
    private List<BranchFixup> branchFixups;
    private List<CallFixup> callFixups;

    // Track live registers per function for caller-save optimization
    private Map<Symbol, Set<Integer>> functionLiveRegs;

    private static class BranchFixup {
        int instrPC;
        int targetBlockID;
        int branchOp;
        int condReg;

        BranchFixup(int instrPC, int targetBlockID, int branchOp, int condReg) {
            this.instrPC = instrPC;
            this.targetBlockID = targetBlockID;
            this.branchOp = branchOp;
            this.condReg = condReg;
        }
    }

    private static class CallFixup {
        int instrPC;
        Symbol targetFunction;

        CallFixup(int instrPC, Symbol targetFunction) {
            this.instrPC = instrPC;
            this.targetFunction = targetFunction;
        }
    }

    public CodeGenerator() {
        this.instructions = new ArrayList<>();
        this.pc = 0;
        this.blockPCMap = new HashMap<>();
        this.functionPCMap = new HashMap<>();
        this.branchFixups = new ArrayList<>();
        this.callFixups = new ArrayList<>();
        this.functionLiveRegs = new HashMap<>();
    }

    // DLX Codes
    static final int ADD = 0;
    static final int SUB = 1;
    static final int MUL = 2;
    static final int DIV = 3;
    static final int MOD = 4;
    static final int POW = 5;
    static final int CMP = 6;

    static final int fADD = 7;
    static final int fSUB = 8;
    static final int fMUL = 9;
    static final int fDIV = 10;
    static final int fMOD = 11;
    static final int fCMP = 12;

    static final int OR = 13;
    static final int AND = 14;
    static final int BIC = 15;
    static final int XOR = 16;
    static final int LSH = 17;
    static final int ASH = 18;

    static final int CHK = 19;

    static final int ADDI = 20;
    static final int SUBI = 21;
    static final int MULI = 22;
    static final int DIVI = 23;
    static final int MODI = 24;
    static final int POWI = 25;
    static final int CMPI = 26;

    static final int fADDI = 27;
    static final int fSUBI = 28;
    static final int fMULI = 29;
    static final int fDIVI = 30;
    static final int fMODI = 31;
    static final int fCMPI = 32;

    static final int ORI = 33;
    static final int ANDI = 34;
    static final int BICI = 35;
    static final int XORI = 36;
    static final int LSHI = 37;
    static final int ASHI = 38;

    static final int CHKI = 39;

    static final int LDW = 40;
    static final int LDX = 41;
    static final int POP = 42;

    static final int STW = 43;
    static final int STX = 44;
    static final int PSH = 45;

    static final int ARRCPY = 46;

    static final int BEQ = 47;
    static final int BNE = 48;
    static final int BLT = 49;
    static final int BGE = 50;
    static final int BLE = 51;
    static final int BGT = 52;

    static final int BSR = 53;
    static final int JSR = 54;
    static final int RET = 55;

    static final int RDI = 56;
    static final int RDF = 57;
    static final int RDB = 58;
    static final int WRI = 59;
    static final int WRF = 60;
    static final int WRB = 61;
    static final int WRL = 62;

    static final int ERR = 63;

    public int[] generate(List<CFG> cfgs) {
        for (CFG cfg : cfgs) {
            analyzeLiveRegisters(cfg);
        }

        // GP is initialized by DLX.execute to MEM_SIZE - 1 (top of memory)
        // Globals use negative offsets from GP, so we don't zero it out
        emit(ADDI, SP, GP, -4000);

        Symbol mainSymbol = null;
        for (CFG cfg : cfgs) {
            if (cfg.getFunctionSymbol().name().equals("main")) {
                mainSymbol = cfg.getFunctionSymbol();
                break;
            }
        }
        if (mainSymbol == null) {
            throw new RuntimeException("Main function not found");
        }
        int mainJumpPC = pc;
        emit(JSR, 0);
        callFixups.add(new CallFixup(mainJumpPC, mainSymbol));

        emit(RET, R0);

        for (CFG cfg : cfgs) {
            generateFunction(cfg);
        }

        applyFixups();

        return instructions.stream().mapToInt(i -> i).toArray();
    }

    private void analyzeLiveRegisters(CFG cfg) {
        Set<Integer> liveRegs = new HashSet<>();

        for (BasicBlock bb : cfg.getAllBlocks()) {
            for (TAC tac : bb.getInstructions()) {
                if (tac.getDest() instanceof Variable) {
                    int reg = getRegisterNumber((Variable) tac.getDest());
                    if (reg > 0 && reg < 26)
                        liveRegs.add(reg);
                }

                for (Value op : tac.getOperands()) {
                    if (op instanceof Variable) {
                        int reg = getRegisterNumber((Variable) op);
                        if (reg > 0 && reg < 26)
                            liveRegs.add(reg);
                    }
                }

                if (tac instanceof StoreGP) {
                    Value src = ((StoreGP) tac).getSrc();
                    if (src instanceof Variable) {
                        int reg = getRegisterNumber((Variable) src);
                        if (reg > 0 && reg < 26)
                            liveRegs.add(reg);
                    }
                }
            }
        }

        functionLiveRegs.put(cfg.getFunctionSymbol(), liveRegs);
    }

    private void generateFunction(CFG cfg) {
        boolean isMain = cfg.getFunctionSymbol().name().equals("main");
        functionPCMap.put(cfg.getFunctionSymbol(), pc);

        // Note: Do NOT clear blockPCMap - block IDs are globally unique across all CFGs
        // and branch fixups need to resolve blocks from all functions

        if (!isMain) {
            emit(PSH, RA, SP, -4);
            emit(PSH, FP, SP, -4);
            emit(ADD, FP, R0, SP);

            int frameSize = cfg.getFrameSize();
            if (frameSize > 0) {
                emit(SUBI, SP, SP, frameSize);
            }
        }

        // For conditional branches, the fallthrough block must immediately follow
        Set<BasicBlock> visited = new HashSet<>();
        Deque<BasicBlock> worklist = new ArrayDeque<>();
        worklist.add(cfg.getEntryBlock());

        while (!worklist.isEmpty()) {
            BasicBlock bb = worklist.pollFirst();
            if (visited.contains(bb)) continue;
            visited.add(bb);
            
            blockPCMap.put(bb.getNum(), pc);
            for (TAC tac : bb.getInstructions()) {
                generateInstruction(tac, isMain);
            }

            BasicBlock fallthrough = getFallthroughSuccessor(bb);
            BasicBlock branchTarget = getBranchTarget(bb);
            
            if (branchTarget != null && !visited.contains(branchTarget)) {
                worklist.addLast(branchTarget);
            }
            
            if (fallthrough != null && !visited.contains(fallthrough)) {
                worklist.addFirst(fallthrough);
            }
            
            if (fallthrough == null && branchTarget == null) {
                for (BasicBlock succ : bb.getSuccessors()) {
                    if (!visited.contains(succ)) {
                        worklist.addLast(succ);
                    }
                }
            }
        }

        for (BasicBlock bb : cfg.getAllBlocks()) {
            if (!visited.contains(bb)) {
                blockPCMap.put(bb.getNum(), pc);
                for (TAC tac : bb.getInstructions()) {
                    generateInstruction(tac, isMain);
                }
            }
        }
    }
    
    /**
     * Get the fallthrough successor (the block that should immediately follow in the instruction stream).
     * For conditional branches, this is the block we go to when the branch is NOT taken.
     */
    private BasicBlock getFallthroughSuccessor(BasicBlock bb) {
        List<TAC> insts = bb.getInstructions();
        if (insts.isEmpty()) return null;
        
        TAC branchInst = null;
        for (int i = insts.size() - 1; i >= 0; i--) {
            TAC inst = insts.get(i);
            if (inst instanceof Beq || inst instanceof Bne || inst instanceof Blt ||
                inst instanceof Ble || inst instanceof Bgt || inst instanceof Bge) {
                branchInst = inst;
                break;
            } else if (inst instanceof Bra || inst instanceof Return) {
                return null;
            }
        }
        
        if (branchInst == null) {
            for (TAC inst : insts) {
                if (inst instanceof Bra) {
                    return null;
                }
            }
            return bb.getSuccessors().isEmpty() ? null : bb.getSuccessors().get(0);
        }
        
        BasicBlock branchTarget = getBranchTargetFromInst(branchInst);
        for (BasicBlock succ : bb.getSuccessors()) {
            if (succ != branchTarget) {
                return succ;
            }
        }
        return null;
    }
    private BasicBlock getBranchTarget(BasicBlock bb) {
        List<TAC> insts = bb.getInstructions();
        for (int i = insts.size() - 1; i >= 0; i--) {
            TAC inst = insts.get(i);
            BasicBlock target = getBranchTargetFromInst(inst);
            if (target != null) return target;
        }
        return null;
    }
    
    private BasicBlock getBranchTargetFromInst(TAC inst) {
        if (inst instanceof Beq) return ((Beq) inst).getTarget();
        if (inst instanceof Bne) return ((Bne) inst).getTarget();
        if (inst instanceof Blt) return ((Blt) inst).getTarget();
        if (inst instanceof Ble) return ((Ble) inst).getTarget();
        if (inst instanceof Bgt) return ((Bgt) inst).getTarget();
        if (inst instanceof Bge) return ((Bge) inst).getTarget();
        if (inst instanceof Bra) return ((Bra) inst).getTarget();
        return null;
    }

    private void generateInstruction(TAC tac, boolean isMain) {
        if (tac instanceof Add) {
            generateBinaryOp((Add) tac, ADD, ADDI);
        } else if (tac instanceof Sub) {
            generateBinaryOp((Sub) tac, SUB, SUBI);
        } else if (tac instanceof Mul) {
            generateBinaryOp((Mul) tac, MUL, MULI);
        } else if (tac instanceof Div) {
            generateBinaryOp((Div) tac, DIV, DIVI);
        } else if (tac instanceof Mod) {
            generateBinaryOp((Mod) tac, MOD, MODI);
        } else if (tac instanceof Pow) {
            generateBinaryOp((Pow) tac, POW, POWI);
        }
        else if (tac instanceof And) {
            generateBinaryOp((And) tac, AND, ANDI);
        } else if (tac instanceof Or) {
            generateBinaryOp((Or) tac, OR, ORI);
        }
        else if (tac instanceof Not) {
            Not not = (Not) tac;
            int dest = getReg((Variable) not.getDest());
            int src = getReg((Variable) not.getOperands().get(0));
            emit(XORI, dest, src, 1);
        }
        else if (tac instanceof Mov) {
            generateMov((Mov) tac);
        } else if (tac instanceof Load) {
            Load load = (Load) tac;
            int dest = getReg((Variable) load.getDest());
            Value addrVal = load.getOperands().get(0);
            int addr = (addrVal instanceof Variable) ? getReg((Variable) addrVal) : R0;

            if (!(addrVal instanceof Variable)) {
                addr = 25;
                int val = getImmediateValue(addrVal);
                emit(ADDI, addr, R0, val);
            }

            emit(LDW, dest, addr, 0);
        } else if (tac instanceof Store) {
            Store store = (Store) tac;
            Value srcVal = store.getOperands().get(0);
            Value addrVal = store.getOperands().get(1);

            int src;
            int addr;

            if (addrVal instanceof Variable) {
                addr = getReg((Variable) addrVal);
            } else {
                addr = 27;
                emit(ADDI, addr, R0, getImmediateValue(addrVal));
            }

            if (srcVal instanceof Variable) {
                src = getReg((Variable) srcVal);
            } else {
                src = 25;
                if (isFloatValue(srcVal)) {
                    emit(fADDI, src, R0, getFloatImmediateValue(srcVal));
                } else {
                    emit(ADDI, src, R0, getImmediateValue(srcVal));
                }
            }

            emit(STW, src, addr, 0);
        } else if (tac instanceof LoadGP) {
            LoadGP loadGP = (LoadGP) tac;
            int dest = getReg(loadGP.getDest());
            int offset = loadGP.getGpOffset();
            emit(LDW, dest, GP, offset);
        } else if (tac instanceof StoreGP) {
            StoreGP storeGP = (StoreGP) tac;
            Value srcVal = storeGP.getSrc();
            int src;

            if (srcVal instanceof Variable) {
                src = getReg((Variable) srcVal);
            } else {
                src = 25;
                if (isFloatValue(srcVal)) {
                    emit(fADDI, src, R0, getFloatImmediateValue(srcVal));
                } else {
                    emit(ADDI, src, R0, getImmediateValue(srcVal));
                }
            }

            int offset = storeGP.getGpOffset();
            emit(STW, src, GP, offset);
        } else if (tac instanceof LoadFP) {
            LoadFP loadFP = (LoadFP) tac;
            int dest = getReg((Variable) loadFP.getDest());
            int offset = loadFP.getFpOffset();
            emit(LDW, dest, FP, offset);
        }
        else if (tac instanceof Adda) {
            Adda adda = (Adda) tac;
            int dest = getReg((Variable) adda.getDest());
            Value base = adda.getOperands().get(0);
            Value offset = adda.getOperands().get(1);

            if (isImmediate(offset)) {
                int baseReg;
                if (base instanceof Variable) {
                    baseReg = getReg((Variable) base);
                } else {
                    baseReg = 25; // Use R25
                    int val = getImmediateValue(base);
                    emit(ADDI, baseReg, R0, val);
                }
                emit(ADDI, dest, baseReg, getImmediateValue(offset));
            } else {
                int baseReg, offsetReg;

                if (base instanceof Variable) {
                    baseReg = getReg((Variable) base);
                } else {
                    baseReg = 25;
                    int val = getImmediateValue(base);
                    emit(ADDI, baseReg, R0, val);
                }

                if (offset instanceof Variable) {
                    offsetReg = getReg((Variable) offset);
                } else {
                    offsetReg = 27;
                    int val = getImmediateValue(offset);
                    emit(ADDI, offsetReg, R0, val);
                }

                emit(ADD, dest, baseReg, offsetReg);
            }
        } else if (tac instanceof AddaGP) {
            AddaGP addaGP = (AddaGP) tac;
            int dest = getReg(addaGP.getDest());
            int gpOffset = addaGP.getGpOffset();
            Value indexOffset = addaGP.getIndex();

            emit(ADDI, dest, GP, gpOffset);

            if (!isZero(indexOffset)) {
                int indexReg;
                if (indexOffset instanceof Variable) {
                    indexReg = getReg((Variable) indexOffset);
                } else {
                    indexReg = 25;
                    int val = getImmediateValue(indexOffset);
                    emit(ADDI, indexReg, R0, val);
                }
                emit(ADD, dest, dest, indexReg);
            }
        } else if (tac instanceof AddaFP) {
            AddaFP addaFP = (AddaFP) tac;
            int dest = getReg(addaFP.getDest());
            int fpOffset = addaFP.getFpOffset();
            Value indexOffset = addaFP.getIndex();

            emit(ADDI, dest, FP, fpOffset);

            if (!isZero(indexOffset)) {
                int indexReg;
                if (indexOffset instanceof Variable) {
                    indexReg = getReg((Variable) indexOffset);
                } else {
                    indexReg = 25;
                    int val = getImmediateValue(indexOffset);
                    emit(ADDI, indexReg, R0, val);
                }
                emit(ADD, dest, dest, indexReg);
            }
        }
        else if (tac instanceof Cmp) {
            generateCmp((Cmp) tac);
        }
        else if (tac instanceof Bra) {
            Bra bra = (Bra) tac;
            int targetID = bra.getTarget().getNum();
            branchFixups.add(new BranchFixup(pc, targetID, BEQ, R0));
            emit(BEQ, R0, 0);
        } else if (tac instanceof Beq) {
            Beq beq = (Beq) tac;
            Value condVal = beq.getOperands().get(0);
            int cond;
            
            if (condVal instanceof Variable) {
                cond = getReg((Variable) condVal);
            } else {
                cond = 25;
                int val = getImmediateValue(condVal);
                emit(ADDI, cond, R0, val);
            }

            int targetID = beq.getTarget().getNum();
            branchFixups.add(new BranchFixup(pc, targetID, BEQ, cond));
            emit(BEQ, cond, 0);
        } else if (tac instanceof Bne) {
            Bne bne = (Bne) tac;
            Value condVal = bne.getOperands().get(0);
            int cond = (condVal instanceof Variable) ? getReg((Variable) condVal) : R0;

            if (!(condVal instanceof Variable)) {
                cond = 25;
                int val = getImmediateValue(condVal);
                emit(ADDI, cond, R0, val);
            }

            int targetID = bne.getTarget().getNum();
            branchFixups.add(new BranchFixup(pc, targetID, BNE, cond));
            emit(BNE, cond, 0);
        } else if (tac instanceof Blt) {
            Blt blt = (Blt) tac;
            Value condVal = blt.getOperands().get(0);
            int cond = (condVal instanceof Variable) ? getReg((Variable) condVal) : R0;

            if (!(condVal instanceof Variable)) {
                cond = 25;
                int val = getImmediateValue(condVal);
                emit(ADDI, cond, R0, val);
            }

            int targetID = blt.getTarget().getNum();
            branchFixups.add(new BranchFixup(pc, targetID, BLT, cond));
            emit(BLT, cond, 0);
        } else if (tac instanceof Ble) {
            Ble ble = (Ble) tac;
            Value condVal = ble.getOperands().get(0);
            int cond = (condVal instanceof Variable) ? getReg((Variable) condVal) : R0;

            if (!(condVal instanceof Variable)) {
                cond = 25;
                int val = getImmediateValue(condVal);
                emit(ADDI, cond, R0, val);
            }

            int targetID = ble.getTarget().getNum();
            branchFixups.add(new BranchFixup(pc, targetID, BLE, cond));
            emit(BLE, cond, 0);
        } else if (tac instanceof Bgt) {
            Bgt bgt = (Bgt) tac;
            Value condVal = bgt.getOperands().get(0);
            int cond = (condVal instanceof Variable) ? getReg((Variable) condVal) : R0;

            if (!(condVal instanceof Variable)) {
                cond = 25;
                int val = getImmediateValue(condVal);
                emit(ADDI, cond, R0, val);
            }

            int targetID = bgt.getTarget().getNum();
            branchFixups.add(new BranchFixup(pc, targetID, BGT, cond));
            emit(BGT, cond, 0);
        } else if (tac instanceof Bge) {
            Bge bge = (Bge) tac;
            Value condVal = bge.getOperands().get(0);
            int cond = (condVal instanceof Variable) ? getReg((Variable) condVal) : R0;

            if (!(condVal instanceof Variable)) {
                cond = 25;
                int val = getImmediateValue(condVal);
                emit(ADDI, cond, R0, val);
            }

            int targetID = bge.getTarget().getNum();
            branchFixups.add(new BranchFixup(pc, targetID, BGE, cond));
            emit(BGE, cond, 0);
        }
        else if (tac instanceof Call) {
            generateCall((Call) tac);
        }
        else if (tac instanceof Return) {
            generateReturn((Return) tac, isMain);
        }
        else if (tac instanceof Swap) {
            Swap swap = (Swap) tac;
            Variable var1 = (Variable) swap.getOperands().get(0);
            Variable var2 = (Variable) swap.getOperands().get(1);
            int reg1 = getReg(var1);
            int reg2 = getReg(var2);
            // XOR swap trick: x = x ^ y; y = x ^ y; x = x ^ y
            emit(XOR, reg1, reg1, reg2);
            emit(XOR, reg2, reg1, reg2);
            emit(XOR, reg1, reg1, reg2);
        }
        else if (tac instanceof End) {
            generateReturn(null, isMain);
        }
        else if (tac instanceof Read) {
            Read read = (Read) tac;
            int dest = getReg(read.getDest());

            if (read.isFloat()) {
                emit(RDF, dest);
            } else {
                emit(RDI, dest);
            }
        } else if (tac instanceof ReadB) {
            ReadB readB = (ReadB) tac;
            int dest = getReg(readB.getDest());
            emit(RDB, dest);
        } else if (tac instanceof Write) {
            Write write = (Write) tac;
            Value srcVal = write.getOperands().get(0);
            int src;

            if (srcVal instanceof Variable) {
                src = getReg((Variable) srcVal);
            } else {
                src = 25;
                if (write.isFloat()) {
                    emit(fADDI, src, R0, getFloatImmediateValue(srcVal));
                } else {
                    emit(ADDI, src, R0, getImmediateValue(srcVal));
                }
            }

            if (write.isFloat()) {
                emit(WRF, src);
            } else {
                emit(WRI, src);
            }
        } else if (tac instanceof WriteB) {
            WriteB writeB = (WriteB) tac;
            Value srcVal = writeB.getOperands().get(0);
            int src;

            if (srcVal instanceof Variable) {
                src = getReg((Variable) srcVal);
            } else {
                src = 25;
                int val = getImmediateValue(srcVal);
                emit(ADDI, src, R0, val);
            }

            emit(WRB, src);
        } else if (tac instanceof WriteNL) {
            emit(WRL);
        }

        else {
            throw new RuntimeException("Unsupported TAC instruction: " + tac.toString() + " (class: " + tac.getClass().getSimpleName() + ")");
        }
    }

    private void generateBinaryOp(TAC tac, int regOp, int immOp) {
        int dest = getReg((Variable) tac.getDest());
        
        Value left = tac.getOperands().get(0);
        Value right = tac.getOperands().get(1);

        int leftReg;
        if (left instanceof Variable) {
            leftReg = getReg((Variable) left);
        } else {
            leftReg = 25;
            if (isFloatValue(left)) {
                emit(fADDI, leftReg, R0, getFloatImmediateValue(left));
            } else {
                emit(ADDI, leftReg, R0, getImmediateValue(left));
            }
        }

        boolean isFloat = false;
        if (tac instanceof Add)
            isFloat = ((Add) tac).isFloat();
        else if (tac instanceof Sub)
            isFloat = ((Sub) tac).isFloat();
        else if (tac instanceof Mul)
            isFloat = ((Mul) tac).isFloat();
        else if (tac instanceof Div)
            isFloat = ((Div) tac).isFloat();
        else if (tac instanceof Mod)
            isFloat = ((Mod) tac).isFloat();

        if (isImmediate(right)) {
            if (isFloat) {
                int floatImmOp = getFloatOpcode(immOp);
                emit(floatImmOp, dest, leftReg, getFloatImmediateValue(right));
            } else {
                emit(immOp, dest, leftReg, getImmediateValue(right));
            }
        } else {
            if (isFloat) {
                int floatRegOp = getFloatOpcode(regOp);
                emit(floatRegOp, dest, leftReg, getReg((Variable) right));
            } else {
                emit(regOp, dest, leftReg, getReg((Variable) right));
            }
        }
    }

    /**
     * Map integer opcode to float opcode.
     * e.g., ADDI -> fADDI, ADD -> fADD
     */
    private int getFloatOpcode(int intOp) {
        switch (intOp) {
            case ADDI:
                return fADDI;
            case SUBI:
                return fSUBI;
            case MULI:
                return fMULI;
            case DIVI:
                return fDIVI;
            case MODI:
                return fMODI;
            case CMPI:
                return fCMPI;

            case ADD:
                return fADD;
            case SUB:
                return fSUB;
            case MUL:
                return fMUL;
            case DIV:
                return fDIV;
            case MOD:
                return fMOD;
            case CMP:
                return fCMP;

            case ANDI:
            case ORI:
            case AND:
            case OR:
            case POWI: // POW might not have float version
            case POW:
                throw new RuntimeException("No float version of opcode: " + intOp);
            default:
                throw new RuntimeException("Unknown opcode for float mapping: " + intOp);
        }
    }

    private void generateMov(Mov mov) {
        int dest = getReg((Variable) mov.getDest());
        Value src = mov.getOperands().get(0);

        if (isImmediate(src)) {
            // Infer type from the immediate value itself (optimizations create immediates without isFloat flag)
            if (isFloatImmediate(src)) {
                emit(fADDI, dest, R0, getFloatImmediateValue(src));
            } else {
                emit(ADDI, dest, R0, getImmediateValue(src));
            }
        } else {
            emit(ADD, dest, getReg((Variable) src), R0);
        }
    }
    
    /**
     * Check if an immediate value is a float type (Float or Double).
     */
    private boolean isFloatImmediate(Value v) {
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            return val instanceof Float || val instanceof Double;
        }
        if (v instanceof Literal) {
            Object val = ((Literal) v).getValue();
            return val instanceof ast.FloatLiteral;
        }
        return false;
    }

    private void generateCmp(Cmp cmp) {
        int dest = getReg(cmp.getDest());

        Value left = cmp.getOperands().get(0);
        Value right = cmp.getOperands().get(1);

        int leftReg;
        if (left instanceof Variable) {
            leftReg = getReg((Variable) left);
        } else {
            leftReg = 25;
            if (isFloatValue(left)) {
                emit(fADDI, leftReg, R0, getFloatImmediateValue(left));
            } else {
                emit(ADDI, leftReg, R0, getImmediateValue(left));
            }
        }

        if (isImmediate(right)) {
            if (cmp.isFloat()) {
                emit(fCMPI, dest, leftReg, getFloatImmediateValue(right));
            } else {
                emit(CMPI, dest, leftReg, getImmediateValue(right));
            }
        } else {
            if (cmp.isFloat()) {
                emit(fCMP, dest, leftReg, getReg((Variable) right));
            } else {
                emit(CMP, dest, leftReg, getReg((Variable) right));
            }
        }

        // Convert CMP result (-1, 0, 1) to boolean (0 or 1) based on operator
        switch (cmp.getOp()) {
            case "eq":
                emit(ANDI, dest, dest, 1);
                emit(XORI, dest, dest, 1);
                break;
            case "ne":
                emit(ANDI, dest, dest, 1);
                break;
            case "lt":
                emit(LSHI, dest, dest, -31);
                break;
            case "le":
                emit(SUBI, dest, dest, 1);
                emit(LSHI, dest, dest, -31);
                break;
            case "gt":
                emit(ADDI, dest, dest, 1);
                emit(LSHI, dest, dest, -1);
                break;
            case "ge":
                emit(ADDI, dest, dest, 2);
                emit(LSHI, dest, dest, -1);
                break;
        }
    }

    private void generateCall(Call call) {
        // Use Symbol directly for call target to support overloading
        Symbol funcSymbol = call.getFunction();
        Set<Integer> liveRegs = functionLiveRegs.getOrDefault(funcSymbol, new HashSet<>());

        List<Integer> savedRegs = new ArrayList<>();
        for (int r = 1; r <= 25; r++) {
            if (liveRegs.contains(r)) {
                emit(PSH, r, SP, -4);
                savedRegs.add(r);
            }
        }

        List<Value> args = call.getArguments();
        for (int i = args.size() - 1; i >= 0; i--) {
            Value arg = args.get(i);
            int argReg;
            if (arg instanceof Variable) {
                argReg = getReg((Variable) arg);
            } else {
                argReg = 25;
                if (isFloatValue(arg)) {
                    emit(fADDI, argReg, R0, getFloatImmediateValue(arg));
                } else {
                    emit(ADDI, argReg, R0, getImmediateValue(arg));
                }
            }
            emit(PSH, argReg, SP, -4);
        }

        emit(PSH, R0, SP, -4);

        callFixups.add(new CallFixup(pc, funcSymbol));
        emit(JSR, 0);

        int destReg = -1;
        if (call.getDest() != null) {
            destReg = getReg((Variable) call.getDest());
            emit(LDW, destReg, SP, 0);
        }

        int popSize = 4 + (args.size() * 4);
        emit(ADDI, SP, SP, popSize);

        for (int i = savedRegs.size() - 1; i >= 0; i--) {
            int reg = savedRegs.get(i);

            // FIX: If this register is holding our Return Value,
            // do NOT overwrite it with the old saved value.
            if (reg == destReg) {
                emit(ADDI, SP, SP, 4);
            } else {
                emit(POP, reg, SP, 4);
            }
        }
    }

    private void generateReturn(Return ret, boolean isMain) {
        if (isMain) {
            emit(RET, R0);
            return;
        }

        // Store return value in stack slot at FP+8 (above saved FP and RA)
        if (ret != null && !ret.getOperands().isEmpty()) {
            Value retVal = ret.getOperands().get(0);
            int srcReg;

            if (retVal instanceof Variable) {
                srcReg = getReg((Variable) retVal);
            } else {
                srcReg = 25;
                if (isFloatValue(retVal)) {
                    emit(fADDI, srcReg, R0, getFloatImmediateValue(retVal));
                } else {
                    emit(ADDI, srcReg, R0, getImmediateValue(retVal));
                }
            }

            emit(STW, srcReg, FP, 8);
        }

        emit(ADD, SP, FP, R0);
        emit(POP, FP, SP, 4);
        emit(POP, RA, SP, 4);
        emit(RET, RA);
    }

    private int getReg(Variable var) {
        return getRegisterNumber(var);
    }

    private int getRegisterNumber(Variable var) {
        String name = var.getSymbol().name();
        if (name.startsWith("R")) {
            try {
                return Integer.parseInt(name.substring(1));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid register name: " + name);
            }
        }
        throw new RuntimeException("Variable not mapped to register: " + var);
    }

    private boolean isImmediate(Value v) {
        if (v instanceof Immediate)
            return true;
        if (v instanceof Literal) {
            Object val = ((Literal) v).getValue();
            return val instanceof ast.IntegerLiteral
                    || val instanceof ast.BoolLiteral
                    || val instanceof ast.FloatLiteral;
        }
        return false;
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
            Object val = ((Literal) v).getValue();
            if (val instanceof ast.IntegerLiteral) {
                return ((ast.IntegerLiteral) val).getValue();
            }
            if (val instanceof ast.BoolLiteral) {
                return ((ast.BoolLiteral) val).getValue() ? 1 : 0;
            }
        }
        throw new RuntimeException("Not an integer immediate: " + v);
    }

    private boolean isFloatVariable(Variable var) {
        return var.getSymbol().type() instanceof types.FloatType;
    }

    private boolean isFloatValue(Value v) {
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            return val instanceof Float;
        }
        if (v instanceof Literal) {
            Object val = ((Literal) v).getValue();
            return val instanceof ast.FloatLiteral;
        }
        return false;
    }

    private float getFloatImmediateValue(Value v) {
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            if (val instanceof Float)
                return (Float) val;
            if (val instanceof Double)
                return ((Double) val).floatValue();
            if (val instanceof Integer)
                return ((Integer) val).floatValue();
        }
        if (v instanceof Literal) {
            Object val = ((Literal) v).getValue();
            if (val instanceof ast.FloatLiteral) {
                return ((ast.FloatLiteral) val).getValue();
            }
        }
        throw new RuntimeException("Not a float immediate: " + v);
    }

    private boolean isZero(Value v) {
        if (v instanceof Immediate) {
            Object val = ((Immediate) v).getValue();
            if (val instanceof Integer) {
                return ((Integer) val).intValue() == 0;
            }
            return val.equals(0);
        }
        return false;
    }

    private void applyFixups() {
        for (BranchFixup fixup : branchFixups) {
            if (!blockPCMap.containsKey(fixup.targetBlockID)) {
                throw new RuntimeException("Branch to unknown block: " + fixup.targetBlockID);
            }

            int targetPC = blockPCMap.get(fixup.targetBlockID);
            int offset = targetPC - fixup.instrPC;

            instructions.set(fixup.instrPC, DLX.assemble(fixup.branchOp, fixup.condReg, offset));
        }

        for (CallFixup fixup : callFixups) {
            if (!functionPCMap.containsKey(fixup.targetFunction)) {
                throw new RuntimeException("Call to undefined function: " + fixup.targetFunction.name());
            }

            int targetPC = functionPCMap.get(fixup.targetFunction);
            instructions.set(fixup.instrPC, DLX.assemble(JSR, targetPC * 4));
        }
    }

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

    private void emit(int op, int a, int b, float c) {
        instructions.add(DLX.assemble(op, a, b, c));
        pc++;
    }

}