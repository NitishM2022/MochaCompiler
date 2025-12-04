package ir.regalloc;

import ir.cfg.BasicBlock;
import ir.cfg.CFG;
import ir.tac.*;

import java.util.*;

public class SSAElimination {

    public SSAElimination() {
    }

    private static int nextBlockNum = 1000; // Start high to avoid conflicts - MUST be static for global uniqueness!

    public void eliminatePhis(CFG cfg) {
        // First pass: identify critical edges and split them
        splitCriticalEdges(cfg);

        for (BasicBlock bb : cfg.getAllBlocks()) {
            // Get Phis from the separate phiFunctions list
            List<Phi> phis = bb.getPhis();

            if (phis.isEmpty())
                continue;

            // For each predecessor, insert parallel copies at the end
            List<BasicBlock> preds = new ArrayList<>(bb.getPredecessors()); // Copy to avoid ConcurrentModification
            for (BasicBlock pred : preds) {
                List<Mov> moves = new ArrayList<>();

                // For each Phi, create a move from the source to the destination
                for (Phi phi : phis) {
                    Variable dest = (Variable) phi.getDest();
                    Value src = phi.getArgs().get(pred);

                    // Only insert move if source and dest are different
                    if (src instanceof Variable && !src.equals(dest)) {
                        moves.add(new Mov(-1, dest, (Variable) src));
                    } else if (src instanceof Immediate || src instanceof Literal) {
                        // Move immediate/literal to dest
                        moves.add(new Mov(-1, dest, src));
                    }
                }

                // Insert moves at end of predecessor (before branch)
                if (!moves.isEmpty()) {
                    // System.err.println("DEBUG SSA ELIM: Inserting " + moves.size() + " moves from BB" + pred.getNum() + " to BB" + bb.getNum());
                    // for (Mov m : moves) {
                    //     System.err.println("  MOVE: " + m.getDest() + " = " + m.getSrc());
                    // }
                    insertMovesAtEnd(pred, moves);
                }
            }
        }

        // Remove all Phi nodes from the CFG
        for (BasicBlock bb : cfg.getAllBlocks()) {
            bb.getPhis().clear();
        }
    }

    /**
     * Split critical edges to ensure phi moves are placed correctly.
     * A critical edge is from a block with multiple successors to a block with multiple predecessors.
     */
    private void splitCriticalEdges(CFG cfg) {
        List<BasicBlock> blocks = new ArrayList<>(cfg.getAllBlocks());
        
        for (BasicBlock pred : blocks) {
            if (pred.getSuccessors().size() <= 1) continue; // Not critical (single successor)
            
            List<BasicBlock> succs = new ArrayList<>(pred.getSuccessors());
            for (BasicBlock succ : succs) {
                if (succ.getPredecessors().size() <= 1) continue; // Not critical
                if (succ.getPhis().isEmpty()) continue; // No phi functions, no need to split
                
                // This is a critical edge - split it
                BasicBlock newBlock = new BasicBlock(nextBlockNum++);
                cfg.addBlock(newBlock);
                
                // Add unconditional branch to successor
                newBlock.addInstruction(new Bra(-1, succ));
                
                // Update CFG edges
                pred.getSuccessors().remove(succ);
                pred.getSuccessors().add(newBlock);
                newBlock.addSuccessor(succ);
                newBlock.addPredecessor(pred);
                
                succ.getPredecessors().remove(pred);
                succ.addPredecessor(newBlock);
                
                // Update branch target in pred to point to newBlock
                updateBranchTarget(pred, succ, newBlock);
                
                // Update phi functions: change source from pred to newBlock
                for (Phi phi : succ.getPhis()) {
                    Value srcVal = phi.getArgs().get(pred);
                    if (srcVal != null) {
                        phi.getArgs().remove(pred);
                        phi.getArgs().put(newBlock, srcVal);
                    }
                }
                
                // System.err.println("DEBUG: Split critical edge BB" + pred.getNum() + " -> BB" + succ.getNum() + " with BB" + newBlock.getNum());
            }
        }
    }

    /**
     * Update branch instruction in pred to target newBlock instead of oldTarget.
     */
    private void updateBranchTarget(BasicBlock pred, BasicBlock oldTarget, BasicBlock newBlock) {
        for (TAC inst : pred.getInstructions()) {
            if (inst instanceof Beq) {
                Beq beq = (Beq) inst;
                if (beq.getTarget() == oldTarget) {
                    beq.setTarget(newBlock);
                }
            } else if (inst instanceof Bne) {
                Bne bne = (Bne) inst;
                if (bne.getTarget() == oldTarget) {
                    bne.setTarget(newBlock);
                }
            } else if (inst instanceof Blt) {
                Blt blt = (Blt) inst;
                if (blt.getTarget() == oldTarget) {
                    blt.setTarget(newBlock);
                }
            } else if (inst instanceof Ble) {
                Ble ble = (Ble) inst;
                if (ble.getTarget() == oldTarget) {
                    ble.setTarget(newBlock);
                }
            } else if (inst instanceof Bgt) {
                Bgt bgt = (Bgt) inst;
                if (bgt.getTarget() == oldTarget) {
                    bgt.setTarget(newBlock);
                }
            } else if (inst instanceof Bge) {
                Bge bge = (Bge) inst;
                if (bge.getTarget() == oldTarget) {
                    bge.setTarget(newBlock);
                }
            } else if (inst instanceof Bra) {
                Bra bra = (Bra) inst;
                if (bra.getTarget() == oldTarget) {
                    bra.setTarget(newBlock);
                }
            }
        }
    }

    /**
     * Insert moves at the end of a block, before any branch instruction.
     * Handles parallel copy resolution to avoid cycles.
     */
    private void insertMovesAtEnd(BasicBlock block, List<Mov> moves) {
        // Resolve parallel copies (handle cycles with Swap)
        List<TAC> resolvedMoves = resolveParallelCopies(moves);

        // Find insertion point (before FIRST branch, not last!)
        // This is critical: if there's a conditional branch followed by unconditional branch,
        // we need to insert moves BEFORE the conditional branch so they execute on both paths
        List<TAC> insts = block.getInstructions();
        int insertPos = insts.size();

        // Scan from the beginning to find the FIRST branch instruction
        for (int i = 0; i < insts.size(); i++) {
            TAC inst = insts.get(i);
            if (inst instanceof Bra || inst instanceof Beq || inst instanceof Bne ||
                    inst instanceof Blt || inst instanceof Ble || inst instanceof Bgt ||
                    inst instanceof Bge || inst instanceof Return) {
                insertPos = i;
                break;  // Insert before the FIRST branch
            }
        }

        insts.addAll(insertPos, resolvedMoves);
    }

    /**
     * Resolve parallel copies, handling cycles with Swap instructions.
     */
    private List<TAC> resolveParallelCopies(List<Mov> moves) {
        List<TAC> result = new ArrayList<>();
        List<Mov> pending = new ArrayList<>(moves);

        while (!pending.isEmpty()) {
            // Remove self-moves
            pending.removeIf(m -> m.getDest().equals(m.getSrc()));
            if (pending.isEmpty())
                break;

            // Find a safe move (dest not used as source in other moves)
            Mov safeMove = null;
            for (Mov candidate : pending) {
                boolean destUsedAsSrc = false;
                for (Mov other : pending) {
                    if (other != candidate && other.getSrc().equals(candidate.getDest())) {
                        destUsedAsSrc = true;
                        break;
                    }
                }
                if (!destUsedAsSrc) {
                    safeMove = candidate;
                    break;
                }
            }

            if (safeMove != null) {
                // Emit safe move
                result.add(safeMove);
                pending.remove(safeMove);
            } else {
                // Cycle detected - use Swap to break it
                Mov cycleMove = pending.get(0);

                if (!(cycleMove.getSrc() instanceof Variable)) {
                    throw new RuntimeException("Cycle with non-variable source: " + cycleMove);
                }

                Variable dest = (Variable) cycleMove.getDest();
                Variable src = (Variable) cycleMove.getSrc();

                result.add(new Swap(-1, dest, src));
                pending.remove(cycleMove);

                // Update other moves that referenced the swapped values
                for (Mov other : pending) {
                    if (other.getSrc().equals(dest)) {
                        other.setSrc(src);
                    }
                }
            }
        }

        return result;
    }
}
