package ir.optimizations;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;

public class ConstantFolding extends BaseOptimization {
    private Set<BasicBlock> infiniteLoopsDetected = new HashSet<>();
    
    public ConstantFolding(Optimizer optimizer) { 
        super(optimizer); 
    }
    
    @Override
    protected String getName() { 
        return "CF"; 
    }
    
    @Override
    public boolean optimize(CFG cfg) {
        infiniteLoopsDetected.clear();
        boolean changed = foldArithmeticAndComparisons(cfg);
        changed |= optimizeBranches(cfg);

        // optimizeBranches invalidates the dominator analysis
        // We do so in eliminateUnreachableBlocks
        changed |= eliminateUnreachableBlocks(cfg);
        return changed;
    }
    
    private boolean foldArithmeticAndComparisons(CFG cfg) {
        boolean changed = false;
        
        for (BasicBlock block : cfg.getAllBlocks()) {
            if (block == null) continue;
            
            List<TAC> instructions = block.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                TAC instruction = instructions.get(i);
                
                if (instruction.isEliminated() || !(instruction.getDest() instanceof Variable)) {
                    continue;
                }
                
                TAC simplified = tryAlgebraicSimplification(instruction);
                if (simplified != null) {
                    instructions.set(i, simplified);
                    logInstruction(instruction, "Algebraic simplification: " + instruction.toString() + " -> " + simplified.toString());
                    changed = true;
                    continue;
                }
                
                Value folded = tryConstantFolding(instruction);
                if (folded != null) {
                    Variable dest = (Variable) instruction.getDest();
                    
                    // Preserve float flag from original instruction
                    boolean isFloat = false;
                    if (instruction instanceof Add) isFloat = ((Add) instruction).isFloat();
                    else if (instruction instanceof Sub) isFloat = ((Sub) instruction).isFloat();
                    else if (instruction instanceof Mul) isFloat = ((Mul) instruction).isFloat();
                    else if (instruction instanceof Div) isFloat = ((Div) instruction).isFloat();
                    else if (instruction instanceof Mod) isFloat = ((Mod) instruction).isFloat();
                    
                    Mov newMove = new Mov(instruction.getId(), dest, folded, isFloat);
                    instructions.set(i, newMove);
                    logInstruction(instruction, "Folded constant: " + instruction.toString() + " -> " + newMove.toString());
                    changed = true;
                }
                
                // Handle unary Not instruction
                if (instruction instanceof Not) {
                    Not notInst = (Not) instruction;
                    Value operand = notInst.getLeft();
                    Integer operandVal = getIntegerValue(operand);
                    if (operandVal != null) {
                        int result = (operandVal == 0) ? 1 : 0;
                        Variable dest = (Variable) notInst.getDest();
                        Mov newMove = new Mov(notInst.getId(), dest, new Immediate(result));
                        instructions.set(i, newMove);
                        logInstruction(notInst, "Folded constant: " + notInst.toString() + " -> " + newMove.toString());
                        changed = true;
                    }
                }
            }
        }
        
        return changed;
    }
    
    private boolean optimizeBranches(CFG cfg) {
        boolean changed = false;
        
        for (BasicBlock block : cfg.getAllBlocks()) {
            if (block == null) continue;
            
            List<TAC> instructions = block.getInstructions();
            if (instructions.isEmpty()) continue;
            
            TAC lastInst = instructions.get(instructions.size() - 1);
            
            if (lastInst instanceof Beq) {
                changed |= optimizeConditionalBranch((Beq) lastInst, block, instructions, true);
            } else if (lastInst instanceof Bne) {
                changed |= optimizeConditionalBranch((Bne) lastInst, block, instructions, false);
            } else if (lastInst instanceof Blt) {
                changed |= optimizeComparisonBranch((Blt) lastInst, block, instructions, "lt");
            } else if (lastInst instanceof Ble) {
                changed |= optimizeComparisonBranch((Ble) lastInst, block, instructions, "le");
            } else if (lastInst instanceof Bgt) {
                changed |= optimizeComparisonBranch((Bgt) lastInst, block, instructions, "gt");
            } else if (lastInst instanceof Bge) {
                changed |= optimizeComparisonBranch((Bge) lastInst, block, instructions, "ge");
            }
        }
        
        return changed;
    }

    private boolean eliminateUnreachableBlocks(CFG cfg) {
        boolean changed = false;
        Set<BasicBlock> reachable = new HashSet<>();
        Queue<BasicBlock> worklist = new LinkedList<>();
        
        BasicBlock entry = cfg.getEntryBlock();
        if (entry == null) {
            return false;
        }
        
        worklist.add(entry);
        reachable.add(entry);
        
        while (!worklist.isEmpty()) {
            BasicBlock block = worklist.poll();
            
            for (BasicBlock succ : block.getSuccessors()) {
                if (!reachable.contains(succ)) {
                    reachable.add(succ);
                    worklist.add(succ);
                }
            }
        }
        
        List<BasicBlock> allBlocks = new ArrayList<>(cfg.getAllBlocks());
        
        for (BasicBlock block : allBlocks) {
            if (!reachable.contains(block)) {
                cfg.removeBlock(block);
                changed = true;
                log("Eliminated unreachable block: BB" + block.getNum());
            }
        }
        
        return changed;
    }
    
    private TAC tryAlgebraicSimplification(TAC instruction) {
        if (!isBinaryArithmetic(instruction)) return null;
        
        List<Value> operands = instruction.getOperands();
        if (operands == null || operands.size() < 2) return null;
        
        Variable dest = (Variable) instruction.getDest();
        int id = instruction.getId();
        Value left = operands.get(0);
        Value right = operands.get(1);
        
        Integer leftVal = getIntegerValue(left);
        Integer rightVal = getIntegerValue(right);
        
        if (instruction instanceof Add) {
            if (rightVal != null && rightVal == 0) return new Mov(id, dest, left);
            if (leftVal != null && leftVal == 0) return new Mov(id, dest, right);
        } else if (instruction instanceof Sub) {
            if (rightVal != null && rightVal == 0) return new Mov(id, dest, left);
            if (left instanceof Variable && right instanceof Variable && left.equals(right)) {
                return new Mov(id, dest, new Immediate(0));
            }
        } else if (instruction instanceof Mul) {
            if ((leftVal != null && leftVal == 0) || (rightVal != null && rightVal == 0)) {
                return new Mov(id, dest, new Immediate(0));
            }
            if (rightVal != null && rightVal == 1) return new Mov(id, dest, left);
            if (leftVal != null && leftVal == 1) return new Mov(id, dest, right);
        } else if (instruction instanceof Div) {
            if (rightVal != null && rightVal == 1) return new Mov(id, dest, left);
            if (leftVal != null && leftVal == 0) return new Mov(id, dest, new Immediate(0));
        } else if (instruction instanceof Pow) {
            if (rightVal != null && rightVal == 0) return new Mov(id, dest, new Immediate(1));
            if (rightVal != null && rightVal == 1) return new Mov(id, dest, left);
            if (leftVal != null && leftVal == 0) return new Mov(id, dest, new Immediate(0));
            if (leftVal != null && leftVal == 1) return new Mov(id, dest, new Immediate(1));
        } else if (instruction instanceof Mod) {
            if (rightVal != null && rightVal == 1) return new Mov(id, dest, new Immediate(0));
        } else if (instruction instanceof And) {
            if (leftVal != null && leftVal == 0) return new Mov(id, dest, new Immediate(0));
            if (rightVal != null && rightVal == 0) return new Mov(id, dest, new Immediate(0));
            if (leftVal != null && leftVal == 1) return new Mov(id, dest, right);
            if (rightVal != null && rightVal == 1) return new Mov(id, dest, left);
            if (left instanceof Variable && right instanceof Variable && left.equals(right)) {
                return new Mov(id, dest, left);
            }
        } else if (instruction instanceof Or) {
            if (leftVal != null && leftVal == 1) return new Mov(id, dest, new Immediate(1));
            if (rightVal != null && rightVal == 1) return new Mov(id, dest, new Immediate(1));
            if (leftVal != null && leftVal == 0) return new Mov(id, dest, right);
            if (rightVal != null && rightVal == 0) return new Mov(id, dest, left);
            if (left instanceof Variable && right instanceof Variable && left.equals(right)) {
                return new Mov(id, dest, left);
            }
        }
        
        return null;
    }
    
    private Value tryConstantFolding(TAC instruction) {
        if (instruction instanceof Cmp) {
            return foldComparison((Cmp) instruction);
        }
        
        if (!isBinaryArithmetic(instruction)) return null;
        
        List<Value> operands = instruction.getOperands();
        if (operands == null || operands.size() < 2) return null;
        
        Value left = operands.get(0);
        Value right = operands.get(1);
        
        Number leftNum = getNumericValue(left);
        Number rightNum = getNumericValue(right);
        
        if (leftNum == null || rightNum == null) return null;
        
        try {
            // Check if this is a float operation using the instruction's isFloat flag
            boolean isFloat = false;
            if (instruction instanceof Add) isFloat = ((Add) instruction).isFloat();
            else if (instruction instanceof Sub) isFloat = ((Sub) instruction).isFloat();
            else if (instruction instanceof Mul) isFloat = ((Mul) instruction).isFloat();
            else if (instruction instanceof Div) isFloat = ((Div) instruction).isFloat();
            else if (instruction instanceof Mod) isFloat = ((Mod) instruction).isFloat();
            
            if (isFloat) {
                double leftVal = leftNum.doubleValue();
                double rightVal = rightNum.doubleValue();
                double result = 0.0;
                
                if (instruction instanceof Add) {
                    result = leftVal + rightVal;
                } else if (instruction instanceof Sub) {
                    result = leftVal - rightVal;
                } else if (instruction instanceof Mul) {
                    result = leftVal * rightVal;
                } else if (instruction instanceof Div) {
                    if (rightVal == 0.0) return null;
                    result = leftVal / rightVal;
                } else if (instruction instanceof Mod) {
                    if (rightVal == 0.0) return null;
                    result = leftVal % rightVal;
                } else if (instruction instanceof Pow) {
                    if (leftVal < 0 || rightVal < 0) return null;
                    result = Math.pow(leftVal, rightVal);
                } else {
                    return null;
                }
                
                return new Immediate(result);
            } else {
                int leftVal = leftNum.intValue();
                int rightVal = rightNum.intValue();
                Integer result = null;
                
                if (instruction instanceof Add) {
                    result = leftVal + rightVal;
                } else if (instruction instanceof Sub) {
                    result = leftVal - rightVal;
                } else if (instruction instanceof Mul) {
                    result = leftVal * rightVal;
                } else if (instruction instanceof Div) {
                    if (rightVal == 0) return null;
                    result = leftVal / rightVal;
                } else if (instruction instanceof Mod) {
                    if (rightVal == 0) return null;
                    result = leftVal % rightVal;
                } else if (instruction instanceof Pow) {
                    if (leftVal < 0 || rightVal < 0) return null;
                    result = (int) Math.pow(leftVal, rightVal);
                } else if (instruction instanceof And) {
                    result = leftVal & rightVal;
                } else if (instruction instanceof Or) {
                    result = leftVal | rightVal;
                }
                
                if (result != null) return new Immediate(result);
            }
        } catch (ArithmeticException e) {
            return null;
        }
        
        return null;
    }
    
    private Value foldComparison(Cmp cmp) {
        Value left = cmp.getLeft();
        Value right = cmp.getRight();
        String op = cmp.getOp();
        
        Number leftNum = getNumericValue(left);
        Number rightNum = getNumericValue(right);
        
        if (leftNum == null || rightNum == null) return null;
        
        boolean isFloat = (leftNum instanceof Double) || (rightNum instanceof Double);
        boolean result;
        
        if (isFloat) {
            double leftVal = leftNum.doubleValue();
            double rightVal = rightNum.doubleValue();
            
            switch (op) {
                case "eq": result = (leftVal == rightVal); break;
                case "ne": result = (leftVal != rightVal); break;
                case "lt": result = (leftVal < rightVal); break;
                case "le": result = (leftVal <= rightVal); break;
                case "gt": result = (leftVal > rightVal); break;
                case "ge": result = (leftVal >= rightVal); break;
                default: return null;
            }
        } else {
            int leftVal = leftNum.intValue();
            int rightVal = rightNum.intValue();
            
            switch (op) {
                case "eq": result = (leftVal == rightVal); break;
                case "ne": result = (leftVal != rightVal); break;
                case "lt": result = (leftVal < rightVal); break;
                case "le": result = (leftVal <= rightVal); break;
                case "gt": result = (leftVal > rightVal); break;
                case "ge": result = (leftVal >= rightVal); break;
                default: return null;
            }
        }
        
        return new Immediate(result ? 1 : 0);
    }
    
    private Number getNumericValue(Value value) {
        if (value instanceof Immediate) {
            Object val = ((Immediate) value).getValue();
            if (val instanceof Integer) return (Integer) val;
            if (val instanceof Double) return (Double) val;
            if (val instanceof Float) return ((Float) val).doubleValue();
            if (val instanceof Boolean) return ((Boolean) val) ? 1 : 0;
        } else if (value instanceof Literal) {
            ast.Expression expr = ((Literal) value).getValue();
            if (expr instanceof ast.IntegerLiteral) {
                return ((ast.IntegerLiteral) expr).getValue();
            } else if (expr instanceof ast.FloatLiteral) {
                return ((ast.FloatLiteral) expr).getValue();
            } else if (expr instanceof ast.BoolLiteral) {
                return ((ast.BoolLiteral) expr).getValue() ? 1 : 0;
            }
        }
        return null;
    }
    
    private boolean optimizeConditionalBranch(TAC branch, BasicBlock block, 
                                             List<TAC> instructions, boolean isBeq) {
        Value condition;
        BasicBlock target;
        
        if (branch instanceof Beq) {
            condition = ((Beq) branch).getCondition();
            target = ((Beq) branch).getTarget();
        } else if (branch instanceof Bne) {
            condition = ((Bne) branch).getCondition();
            target = ((Bne) branch).getTarget();
        } else {
            return false;
        }
        
        Integer condValue = getIntegerValue(condition);
        if (condValue == null) return false;
        
        boolean branchTaken = isBeq ? (condValue == 0) : (condValue != 0);
        
        if (branchTaken) {
            Bra unconditional = new Bra(branch.getId(), target);
            instructions.set(instructions.size() - 1, unconditional);
            logInstruction(branch, "Branch always taken: " + branch.toString() + " -> " + unconditional.toString());
            
            if (isInfiniteLoop(block, target)) {
                System.err.println("WARNING: Detected infinite loop in basic block BB" + block.getNum());
                infiniteLoopsDetected.add(block);
            }
            
            removeFallthroughSuccessor(block, target);
            return true;
        } else {
            instructions.remove(instructions.size() - 1);
            logInstruction(branch, "Branch never taken: " + branch.toString());
            removeBranchSuccessor(block, target);
            return true;
        }
    }
    
    private boolean optimizeComparisonBranch(TAC branch, BasicBlock block,
                                            List<TAC> instructions, String comparison) {
        Value condition;
        BasicBlock target;
        
        if (branch instanceof Blt) {
            condition = ((Blt) branch).getCondition();
            target = ((Blt) branch).getTarget();
        } else if (branch instanceof Ble) {
            condition = ((Ble) branch).getCondition();
            target = ((Ble) branch).getTarget();
        } else if (branch instanceof Bgt) {
            condition = ((Bgt) branch).getCondition();
            target = ((Bgt) branch).getTarget();
        } else if (branch instanceof Bge) {
            condition = ((Bge) branch).getCondition();
            target = ((Bge) branch).getTarget();
        } else {
            return false;
        }
        
        Integer condValue = getIntegerValue(condition);
        if (condValue == null) return false;
        
        boolean branchTaken = false;
        switch (comparison) {
            case "lt":  branchTaken = (condValue < 0); break;
            case "le":  branchTaken = (condValue <= 0); break;
            case "gt":  branchTaken = (condValue > 0); break;
            case "ge":  branchTaken = (condValue >= 0); break;
        }
        
        if (branchTaken) {
            Bra unconditional = new Bra(branch.getId(), target);
            instructions.set(instructions.size() - 1, unconditional);
            logInstruction(branch, "Branch always taken: " + branch.toString() + " -> " + unconditional.toString());
            
            if (isInfiniteLoop(block, target)) {
                System.err.println("WARNING: Detected infinite loop in basic block BB" + block.getNum());
                infiniteLoopsDetected.add(block);
            }
            
            removeFallthroughSuccessor(block, target);
            return true;
        } else {
            instructions.remove(instructions.size() - 1);
            logInstruction(branch, "Branch never taken: " + branch.toString());
            removeBranchSuccessor(block, target);
            return true;
        }
    }
    
    private boolean isInfiniteLoop(BasicBlock current, BasicBlock target) {
        return target.getNum() <= current.getNum();
    }
    
    private void removeFallthroughSuccessor(BasicBlock block, BasicBlock target) {
        List<BasicBlock> successors = block.getSuccessors();
        Iterator<BasicBlock> iter = successors.iterator();
        while (iter.hasNext()) {
            BasicBlock succ = iter.next();
            if (succ != target) {
                iter.remove();
                succ.getPredecessors().remove(block);
            }
        }
    }
    
    private void removeBranchSuccessor(BasicBlock block, BasicBlock target) {
        block.getSuccessors().remove(target);
        target.getPredecessors().remove(block);
    }
}
