package ir;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;
import mocha.Symbol;

/**
 * Mem2Reg: Changes memory operations (Load/Store) to register operations (direct variable use).
 * 
 * Converts:
 *   Store x, value
 *   Load temp, x
 * 
 * Into:
 *   x = value
 *   temp replaced with x everywhere
 * 
 * After this pass, the IR is ready for SSA conversion.
 * Used this article as a reference: https://longfangsong.github.io/en/mem2reg-made-simple/
 */
public class Mem2Reg {
    private CFG cfg;
    private int nextInstructionId;
    
    public Mem2Reg(CFG cfg) {
        this.cfg = cfg;
        
        int maxId = 0;
        for(BasicBlock block : cfg.getAllBlocks()) {
            for(Phi phi : block.getPhis()) {
                maxId = Math.max(maxId, phi.getId());
            }
            for(TAC instruction : block.getInstructions()) {
                maxId = Math.max(maxId, instruction.getId());
            }
        }
        this.nextInstructionId = maxId + 1;
    }
    
    public void run() {
        for (BasicBlock block : cfg.getAllBlocks()) {
            processBlock(block);
        }
    }
    
    private void processBlock(BasicBlock block) {
        // Track mappings from load destination temps to their source variables
        Map<Variable, Variable> loadTempReplacements = new HashMap<>();
        
        List<TAC> newInstructions = new ArrayList<>();
        TAC terminator = null;
        
        for (TAC instruction : block.getInstructions()) {
            // Handle Load: eliminate it and track the temp to variable mapping
            if (instruction instanceof Load) {
                Load load = (Load) instruction;
                Value addr = load.getAddr();
                
                if (addr instanceof Variable && !((Variable)addr).isTemp()) {
                    Variable srcVar = (Variable) addr;
                    Variable loadDestTemp = (Variable) load.getDest();
                    
                    if (loadDestTemp != null && loadDestTemp.isTemp()) {
                        loadTempReplacements.put(loadDestTemp, srcVar);
                    }
                }
                // Don't add Load to new instruction list (it's eliminated)
                continue;
            }
            
            // Handle Store: convert to Mov
            if (instruction instanceof Store) {
                Store store = (Store) instruction;
                Value addr = store.getAddr();
                
                if (addr instanceof Variable && !((Variable)addr).isTemp()) {
                    Variable destVar = (Variable) addr;
                    Value src = store.getSrc();
                    
                    // Replace any temps in the source with the actual variable
                    src = replaceValue(src, loadTempReplacements);
                    
                    // Create a Mov: dest = src
                    Mov mov = new Mov(nextInstructionId++, destVar, src);
                    newInstructions.add(mov);
                    
                    if (isTerminator(store)) {
                        terminator = mov;
                    }
                } else {
                    // Store to temp or array - keep it
                    replaceOperands(instruction, loadTempReplacements);
                    newInstructions.add(instruction);
                    if (isTerminator(instruction)) {
                        terminator = instruction;
                    }
                }
                continue;
            }
            
            // For all other instructions: replace operands
            replaceOperands(instruction, loadTempReplacements);
            newInstructions.add(instruction);
            
            if (isTerminator(instruction)) {
                terminator = instruction;
            }
        }
        
        // Update the block's instructions
        block.setInstructions(newInstructions);
        
        // Ensure terminator is last
        if (terminator != null && !newInstructions.isEmpty() && 
            newInstructions.get(newInstructions.size() - 1) != terminator) {
            newInstructions.remove(terminator);
            newInstructions.add(terminator);
        }
    }
    
    private void replaceOperands(TAC instruction, Map<Variable, Variable> replacements) {
        List<Value> operands = instruction.getOperands();
        if (operands == null || operands.isEmpty()) return;
        
        List<Value> newOperands = new ArrayList<>(operands.size());
        boolean changed = false;
        
        for (Value operand : operands) {
            Value newOperand = replaceValue(operand, replacements);
            newOperands.add(newOperand);
            if (newOperand != operand) {
                changed = true;
            }
        }
        
        if (changed) {
            instruction.setOperands(newOperands);
        }
    }
    
    private Value replaceValue(Value value, Map<Variable, Variable> replacements) {
        if (value instanceof Variable) {
            Variable var = (Variable) value;
            if (var.isTemp() && replacements.containsKey(var)) {
                return replacements.get(var);
            }
        }
        return value;
    }
    
    private boolean isTerminator(TAC instruction) {
        return instruction instanceof Bra || isConditionalBranch(instruction) ||
               instruction instanceof End || instruction instanceof Return;
    }
    
    private boolean isConditionalBranch(TAC instruction) {
        return instruction instanceof Beq || instruction instanceof Bne ||
               instruction instanceof Blt || instruction instanceof Ble ||
               instruction instanceof Bgt || instruction instanceof Bge;
    }
}

