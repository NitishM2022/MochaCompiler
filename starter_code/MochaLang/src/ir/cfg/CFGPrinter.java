package ir.cfg;

import java.util.List;
import ir.tac.TAC;
import ir.ssa.DominatorAnalysis;

public class CFGPrinter implements CFGVisitor {
    private StringBuilder output;
    
    public CFGPrinter() {
        this.output = new StringBuilder();
    }
    
    public String getResult() {
        return output.toString();
    }
    
    public void printCFG(CFG cfg) {
        printCFG(cfg, null);
    }
    
    public void printCFG(CFG cfg, DominatorAnalysis domAnalysis) {
        output.setLength(0);
        output.append("digraph G {\n");
        
        List<BasicBlock> blocks = cfg.getAllBlocks();
        for (BasicBlock block : blocks) {
            printBlock(block);
        }
        
        for (BasicBlock block : blocks) {
            printEdges(block);
        }
        
        if (domAnalysis != null) {
            for (BasicBlock block : blocks) {
                printDominatorEdges(block, domAnalysis);
            }
        }
        
        output.append("}\n");
    }
    
    private void printBlock(BasicBlock block) {
        output.append("bb").append(block.getNum())
              .append(" [ shape = record , label = \" <b > BB")
              .append(block.getNum()).append(" | ");
        
        boolean first = true;
        output.append("{");
        
        List<ir.tac.Phi> phis = block.getPhis();
        for (ir.tac.Phi phi : phis) {
            if (!phi.isEliminated()) {
                if (!first) output.append(" | ");
                first = false;
                output.append(escapeDot(phi.getId() + ": " + phi.toString()));
            }
        }
        
        List<TAC> instructions = block.getInstructions();
        boolean hasNonEliminated = false;
        for (TAC tac : instructions) {
            if (!tac.isEliminated()) {
                hasNonEliminated = true;
                if (!first) output.append(" | ");
                first = false;
                output.append(escapeDot(tac.getId() + ": " + tac.toString()));
            }
        }
        
        if (!hasNonEliminated && first) {
            output.append("\\< empty \\>");
        }
        output.append("}");
        output.append("\"];\n");
    }
    
    private void printEdges(BasicBlock block) {
        List<BasicBlock> successors = block.getSuccessors();
        if (successors.isEmpty()) return;
        
        List<TAC> instructions = block.getInstructions();
        TAC lastInst = null;
        
        for (int i = instructions.size() - 1; i >= 0; i--) {
            if (!instructions.get(i).isEliminated()) {
                lastInst = instructions.get(i);
                break;
            }
        }
        
        BasicBlock branchTarget = null;
        
        if (lastInst instanceof ir.tac.Bra) {
            branchTarget = ((ir.tac.Bra) lastInst).getTarget();
        } else if (lastInst instanceof ir.tac.Beq) {
            branchTarget = ((ir.tac.Beq) lastInst).getTarget();
        } else if (lastInst instanceof ir.tac.Bne) {
            branchTarget = ((ir.tac.Bne) lastInst).getTarget();
        } else if (lastInst instanceof ir.tac.Blt) {
            branchTarget = ((ir.tac.Blt) lastInst).getTarget();
        } else if (lastInst instanceof ir.tac.Ble) {
            branchTarget = ((ir.tac.Ble) lastInst).getTarget();
        } else if (lastInst instanceof ir.tac.Bgt) {
            branchTarget = ((ir.tac.Bgt) lastInst).getTarget();
        } else if (lastInst instanceof ir.tac.Bge) {
            branchTarget = ((ir.tac.Bge) lastInst).getTarget();
        }
        
        for (BasicBlock succ : successors) {
            output.append("bb").append(block.getNum())
                  .append(" : s -> bb").append(succ.getNum())
                  .append(" : n [ label = \"");
            
            if (succ == branchTarget) {
                output.append(" branch ");
            } else {
                output.append(" fall - through ");
            }
            
            output.append("\" ];\n");
        }
    }
    
    private String escapeDot(String str) {
        return str.replace("\"", "\\\"");
    }
    
    private void printDominatorEdges(BasicBlock block, DominatorAnalysis domAnalysis) {
        List<BasicBlock> children = domAnalysis.getDomTreeChildren(block);
        for (BasicBlock child : children) {
            output.append("bb").append(block.getNum())
                  .append(" : b -> bb").append(child.getNum())
                  .append(" : b [ color = blue , style = dotted ,\nlabel = \" dom \" ];\n");
        }
    }
    
    @Override
    public void visit(BasicBlock block) {
    }
}
