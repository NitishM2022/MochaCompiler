package ir.ssa;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;
import mocha.Symbol;

public class SSAConverter {
    private CFG cfg;
    private DominatorAnalysis domAnalysis;
    private Map<String, Set<BasicBlock>> variableDefs;
    private Map<String, Stack<Variable>> variableStacks;
    private Map<String, Integer> variableVersionCounters;
    private int nextInstructionId = 1;

    public SSAConverter(CFG cfg) {
        this.cfg = cfg;
        this.variableDefs = new HashMap<>();
        this.variableStacks = new HashMap<>();
        this.variableVersionCounters = new HashMap<>();

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

    public void convertToSSA() {
        domAnalysis = new DominatorAnalysis(cfg);
        domAnalysis.analyze();
        cfg.setDominatorAnalysis(domAnalysis);

        Set<String> allVarNames = findVariableDefinitionsAndCollectNames();
        insertPhiNodes(allVarNames);
        renameVariables(allVarNames);
    }

    private Set<String> findVariableDefinitionsAndCollectNames() {
        Set<String> allNames = new HashSet<>();
        
        for (BasicBlock block : cfg.getAllBlocks()) {
            for (TAC instruction : block.getInstructions()) {
                // Collect variable names from destination
                Value dest = instruction.getDest();
                if (dest instanceof Variable && !((Variable)dest).isTemp()) {
                    String varName = ((Variable) dest).getSymbol().name();
                    allNames.add(varName);
                    
                    // Track definition site
                    variableDefs.putIfAbsent(varName, new HashSet<>());
                    variableDefs.get(varName).add(block);
                }
                
                // Collect variable names from operands
                List<Value> operands = instruction.getOperands();
                if (operands != null) {
                    for (Value op : operands) {
                        if (op instanceof Variable && !((Variable)op).isTemp()) {
                            allNames.add(((Variable) op).getSymbol().name());
                        }
                    }
                }
            }
        }
        
        // Add virtual definitions for parameters/globals at entry block
        for (String varName : allNames) {
            if (!variableDefs.containsKey(varName)) {
                variableDefs.put(varName, new HashSet<>());
                variableDefs.get(varName).add(cfg.getEntryBlock());
            }
        }
        
        return allNames;
    }

    private void insertPhiNodes(Set<String> allVarNames) {
        Map<BasicBlock, Set<String>> phiPlacedMap = new HashMap<>();

        for (String varName : allVarNames) {
            if (!variableDefs.containsKey(varName)) {
                 continue;
            }

            Queue<BasicBlock> worklist = new LinkedList<>();
            Set<BasicBlock> inWorklist = new HashSet<>();

            // Initialize worklist with all blocks that define this variable
            for (BasicBlock defBlock : variableDefs.get(varName)) {
                worklist.add(defBlock);
                inWorklist.add(defBlock);
            }

            // Iterate through dominance frontiers
            while (!worklist.isEmpty()) {
                BasicBlock currentBlock = worklist.poll();
                inWorklist.remove(currentBlock);

                Set<BasicBlock> dominanceFrontier = domAnalysis.getDominanceFrontier(currentBlock);
                if (dominanceFrontier == null) continue;

                for (BasicBlock frontierBlock : dominanceFrontier) {
                    phiPlacedMap.putIfAbsent(frontierBlock, new HashSet<>());

                    if (!phiPlacedMap.get(frontierBlock).contains(varName)) {
                        phiPlacedMap.get(frontierBlock).add(varName);

                        // Create and insert phi node
                        Symbol varSymbol = new Symbol(varName);
                        Variable dest = new Variable(varSymbol, -1);
                        Phi phi = new Phi(getNextInstructionId(), dest);
                        frontierBlock.addPhi(phi);

                        // Phi is a new definition, so add to worklist
                        if (!inWorklist.contains(frontierBlock)) {
                            worklist.add(frontierBlock);
                            inWorklist.add(frontierBlock);
                        }
                    }
                }
            }
        }
    }

    private void renameVariables(Set<String> allVarNames) {
        // Initialize stacks and counters for all variables
        for (String varName : allVarNames) {
            variableStacks.put(varName, new Stack<>());
            variableVersionCounters.put(varName, 0);
            // Push v_0 for all variables (represents parameters/initial values)
            variableStacks.get(varName).push(new Variable(new Symbol(varName), 0));
        }

        // Start recursive renaming from entry block
        renameBlock(cfg.getEntryBlock());
    }

    private void renameBlock(BasicBlock block) {
        List<Variable> defsPushedThisBlock = new ArrayList<>();

        // 1. Process PHI nodes - assign new versions to phi destinations
        for (Phi phi : block.getPhis()) {
            Variable phiDest = (Variable) phi.getDest();
            String baseName = phiDest.getSymbol().name();

            // Create new version
            int newVersion = variableVersionCounters.get(baseName) + 1;
            variableVersionCounters.put(baseName, newVersion);

            Variable newSsaVar = new Variable(phiDest.getSymbol(), newVersion);
            phi.setDest(newSsaVar);
            
            // Push onto stack
            variableStacks.get(baseName).push(newSsaVar);
            defsPushedThisBlock.add(newSsaVar);
        }

        // 2. Process regular instructions
        for (TAC instruction : block.getInstructions()) {
            // Rename uses (operands)
            renameUses(instruction);

            // Rename definition (destination) - only for instructions that support it
            Value dest = instruction.getDest();
            if (dest instanceof Variable && !((Variable)dest).isTemp()) {
                Variable destVar = (Variable) dest;
                String varName = destVar.getSymbol().name();

                // Create new version
                int newVersion = variableVersionCounters.get(varName) + 1;
                variableVersionCounters.put(varName, newVersion);

                Variable newSsaVar = new Variable(destVar.getSymbol(), newVersion);
                
                // Set destination based on instruction type
                if (instruction instanceof Mov) {
                    ((Mov) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Assign) {
                    ((Assign) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Load) {
                    ((Load) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Call) {
                    ((Call) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Cmp) {
                    ((Cmp) instruction).setDest(newSsaVar);
                }

                // Push onto stack
                variableStacks.get(varName).push(newSsaVar);
                defsPushedThisBlock.add(newSsaVar);
            }
        }

        // 3. Fill phi arguments in successor blocks
        for (BasicBlock succ : block.getSuccessors()) {
            for (Phi phi : succ.getPhis()) {
                Variable phiDest = (Variable) phi.getDest();
                String baseName = phiDest.getSymbol().name();

                // Get current version from stack
                Variable currentVersion = variableStacks.get(baseName).peek();
                phi.addArgument(block, currentVersion);
            }
        }

        // 4. Recurse on dominator tree children
        for (BasicBlock child : domAnalysis.getDomTreeChildren(block)) {
            renameBlock(child);
        }

        // 5. Backtrack: pop all versions pushed in this block
        for (Variable def : defsPushedThisBlock) {
            String varName = def.getSymbol().name();
            Stack<Variable> stack = variableStacks.get(varName);
            if (!stack.isEmpty() && stack.peek().getVersion() == def.getVersion()) {
                stack.pop();
            }
        }
    }

    private void renameUses(TAC instruction) {
        List<Value> operands = instruction.getOperands();
        if (operands == null || operands.isEmpty()) return;

        List<Value> newOperands = new ArrayList<>(operands.size());
        boolean changed = false;

        for (Value operand : operands) {
            if (operand instanceof Variable && !((Variable)operand).isTemp()) {
                Variable var = (Variable) operand;
                String varName = var.getSymbol().name();
                
                // Replace with current version from stack
                Variable currentVersion = variableStacks.get(varName).peek();
                newOperands.add(currentVersion);
                changed = true;
            } else {
                newOperands.add(operand);
            }
        }

        if (changed) {
            instruction.setOperands(newOperands);
        }
    }

    private int getNextInstructionId() {
        return nextInstructionId++;
    }
}
