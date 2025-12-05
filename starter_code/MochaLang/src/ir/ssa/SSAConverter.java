package ir.ssa;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;
import mocha.Symbol;

public class SSAConverter {
    private CFG cfg;
    private DominatorAnalysis domAnalysis;
    private Map<Symbol, Set<BasicBlock>> variableDefs;
    private Map<Symbol, Stack<Variable>> variableStacks;
    private Map<Symbol, Integer> variableVersionCounters;
    private int nextInstructionId = 1;

    public SSAConverter(CFG cfg) {
        this.cfg = cfg;
        this.variableDefs = new HashMap<>();
        this.variableStacks = new HashMap<>();
        this.variableVersionCounters = new HashMap<>();

        int maxId = 0;
        for (BasicBlock block : cfg.getAllBlocks()) {
            for (Phi phi : block.getPhis()) {
                maxId = Math.max(maxId, phi.getId());
            }
            for (TAC instruction : block.getInstructions()) {
                maxId = Math.max(maxId, instruction.getId());
            }
        }
        this.nextInstructionId = maxId + 1;
    }

    public void convertToSSA() {
        domAnalysis = new DominatorAnalysis(cfg);
        domAnalysis.analyze();
        cfg.setDominatorAnalysis(domAnalysis);

        Set<Symbol> allVars = findVariableDefinitionsAndCollectSymbols();
        insertPhiNodes(allVars);
        renameVariables(allVars);
    }

    private Set<Symbol> findVariableDefinitionsAndCollectSymbols() {
        Set<Symbol> allVars = new HashSet<>();

        for (BasicBlock block : cfg.getAllBlocks()) {
            for (TAC instruction : block.getInstructions()) {
                // Collect variable symbols from destination
                Value dest = instruction.getDest();
                if (dest instanceof Variable) {
                    Symbol sym = ((Variable) dest).getSymbol();
                    allVars.add(sym);

                    // Track definition site
                    variableDefs.putIfAbsent(sym, new HashSet<>());
                    variableDefs.get(sym).add(block);
                }

                // Collect variable symbols from operands
                List<Value> operands = instruction.getOperands();
                if (operands != null) {
                    for (Value op : operands) {
                        if (op instanceof Variable) {
                            allVars.add(((Variable) op).getSymbol());
                        }
                    }
                }
            }
        }

        // Add virtual definitions for parameters/globals at entry block
        for (Symbol sym : allVars) {
            if (!variableDefs.containsKey(sym)) {
                variableDefs.put(sym, new HashSet<>());
                variableDefs.get(sym).add(cfg.getEntryBlock());
            }
        }

        return allVars;
    }

    private void insertPhiNodes(Set<Symbol> allVars) {
        Map<BasicBlock, Set<Symbol>> phiPlacedMap = new HashMap<>();

        for (Symbol sym : allVars) {
            if (!variableDefs.containsKey(sym)) {
                continue;
            }

            Queue<BasicBlock> worklist = new LinkedList<>();
            Set<BasicBlock> inWorklist = new HashSet<>();

            // Initialize worklist with all blocks that define this variable
            for (BasicBlock defBlock : variableDefs.get(sym)) {
                worklist.add(defBlock);
                inWorklist.add(defBlock);
            }

            // Iterate through dominance frontiers
            while (!worklist.isEmpty()) {
                BasicBlock currentBlock = worklist.poll();
                inWorklist.remove(currentBlock);

                Set<BasicBlock> dominanceFrontier = domAnalysis.getDominanceFrontier(currentBlock);
                if (dominanceFrontier == null)
                    continue;

                for (BasicBlock frontierBlock : dominanceFrontier) {
                    phiPlacedMap.putIfAbsent(frontierBlock, new HashSet<>());

                    if (!phiPlacedMap.get(frontierBlock).contains(sym)) {
                        phiPlacedMap.get(frontierBlock).add(sym);

                        // Create and insert phi node
                        Variable dest = new Variable(sym, -1);
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

    private void renameVariables(Set<Symbol> allVars) {
        // Initialize stacks and counters for all variables
        for (Symbol sym : allVars) {
            variableStacks.put(sym, new Stack<>());
            variableVersionCounters.put(sym, 0);
            // Push v_0 for all variables (represents parameters/initial values)
            variableStacks.get(sym).push(new Variable(sym, 0));
        }

        // Start recursive renaming from entry block
        renameBlock(cfg.getEntryBlock());
    }

    private void renameBlock(BasicBlock block) {
        List<Variable> defsPushedThisBlock = new ArrayList<>();

        // 1. Process PHI nodes - assign new versions to phi destinations
        for (Phi phi : block.getPhis()) {
            Variable phiDest = (Variable) phi.getDest();
            Symbol sym = phiDest.getSymbol();

            // Create new version
            int newVersion = variableVersionCounters.get(sym) + 1;
            variableVersionCounters.put(sym, newVersion);

            Variable newSsaVar = new Variable(sym, newVersion);
            phi.setDest(newSsaVar);

            // Push onto stack
            variableStacks.get(sym).push(newSsaVar);
            defsPushedThisBlock.add(newSsaVar);
        }

        // 2. Process regular instructions
        for (TAC instruction : block.getInstructions()) {
            // Rename uses (operands)
            renameUses(instruction);

            // Rename definition (destination) - only for instructions that support it
            Value dest = instruction.getDest();
            if (dest instanceof Variable) {
                Variable destVar = (Variable) dest;
                Symbol sym = destVar.getSymbol();

                // Create new version
                int newVersion = variableVersionCounters.get(sym) + 1;
                variableVersionCounters.put(sym, newVersion);

                Variable newSsaVar = new Variable(sym, newVersion);

                // Set destination based on instruction type
                if (instruction instanceof Mov) {
                    ((Mov) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Assign) {
                    ((Assign) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Load) {
                    ((Load) instruction).setDest(newSsaVar);
                } else if (instruction instanceof LoadGP) {
                    ((LoadGP) instruction).setDest(newSsaVar);
                } else if (instruction instanceof LoadFP) {
                    ((LoadFP) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Call) {
                    ((Call) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Cmp) {
                    ((Cmp) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Read) {
                    ((Read) instruction).setDest(newSsaVar);
                } else if (instruction instanceof ReadB) {
                    ((ReadB) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Add) {
                    ((Add) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Sub) {
                    ((Sub) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Mul) {
                    ((Mul) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Div) {
                    ((Div) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Mod) {
                    ((Mod) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Pow) {
                    ((Pow) instruction).setDest(newSsaVar);
                } else if (instruction instanceof And) {
                    ((And) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Or) {
                    ((Or) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Not) {
                    ((Not) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Neg) {
                    ((Neg) instruction).setDest(newSsaVar);
                } else if (instruction instanceof Adda) {
                    ((Adda) instruction).setDest(newSsaVar);
                } else if (instruction instanceof AddaGP) {
                    ((AddaGP) instruction).setDest(newSsaVar);
                } else if (instruction instanceof AddaFP) {
                    ((AddaFP) instruction).setDest(newSsaVar);
                }

                // Push onto stack
                variableStacks.get(sym).push(newSsaVar);
                defsPushedThisBlock.add(newSsaVar);
            }
        }

        // 3. Fill phi arguments in successor blocks
        for (BasicBlock succ : block.getSuccessors()) {
            for (Phi phi : succ.getPhis()) {
                Variable phiDest = (Variable) phi.getDest();
                Symbol sym = phiDest.getSymbol();

                // Get current version from stack
                Variable currentVersion = variableStacks.get(sym).peek();
                phi.addArgument(block, currentVersion);
            }
        }

        // 4. Recurse on dominator tree children
        for (BasicBlock child : domAnalysis.getDomTreeChildren(block)) {
            renameBlock(child);
        }

        // 5. Backtrack: pop all versions pushed in this block
        // CRITICAL: Pop in REVERSE order (LIFO - last pushed first)
        for (int i = defsPushedThisBlock.size() - 1; i >= 0; i--) {
            Variable def = defsPushedThisBlock.get(i);
            Symbol sym = def.getSymbol();
            Stack<Variable> stack = variableStacks.get(sym);
            if (!stack.isEmpty() && stack.peek().getVersion() == def.getVersion()) {
                stack.pop();
            }
        }
    }

    private void renameUses(TAC instruction) {
        List<Value> operands = instruction.getOperands();
        if (operands == null || operands.isEmpty())
            return;

        List<Value> newOperands = new ArrayList<>(operands.size());
        boolean changed = false;

        for (Value operand : operands) {
            if (operand instanceof Variable) {
                Variable var = (Variable) operand;
                Symbol sym = var.getSymbol();

                // Replace with current version from stack
                Variable currentVersion = variableStacks.get(sym).peek();
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