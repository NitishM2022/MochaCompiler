package ir.regalloc;

import ir.cfg.BasicBlock;
import ir.cfg.CFG;
import ir.tac.*;

import java.util.*;

public class SSAElimination {

    public SSAElimination() {
    }

    public void eliminatePhis(CFG cfg) {
        for (BasicBlock bb : cfg.getAllBlocks()) {
            // Get Phis from the separate phiFunctions list
            List<Phi> phis = bb.getPhis();

            if (phis.isEmpty())
                continue;

            // For each predecessor, insert parallel copies at the end
            List<BasicBlock> preds = bb.getPredecessors();
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
                    insertMovesAtEnd(pred, moves);
                }
            }
        }

        // Remove all Phi nodes from the CFG
        for (BasicBlock bb : cfg.getAllBlocks()) {
            bb.getPhis().clear();
        }
        
        // Debug: Print CFG after SSA elimination
//        System.err.println("After SSA Elimination");
//        System.err.println("CFG: " + cfg.getFunctionName());
//        System.err.println(cfg.asDotGraph());
//        System.err.println();
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
