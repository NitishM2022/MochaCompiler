package ir.cfg;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import ir.tac.TAC;
import ir.tac.Phi;

public class BasicBlock extends Block implements Iterable<TAC> {
    private int num;
    private List<TAC> instructions;
    private List<Phi> phiFunctions;
    private List<BasicBlock> predecessors;
    private List<BasicBlock> successors;
    
    public BasicBlock(int num) {
        this.num = num;
        this.instructions = new ArrayList<>();
        this.phiFunctions = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.successors = new ArrayList<>();
    }
    
    public int getNum() {
        return num;
    }
    
    public List<TAC> getInstructions() {
        return instructions;
    }

    public List<BasicBlock> getPredecessors() {
        return predecessors;
    }
    
    public List<BasicBlock> getSuccessors() {
        return successors;
    }

    public void setInstructions(List<TAC> instructions){
        this.instructions = instructions;
    }
    
    public void addInstruction(TAC instruction) {
        instructions.add(instruction);
    }
    
    public void addPredecessor(BasicBlock pred) {
        if (!predecessors.contains(pred)) {
            predecessors.add(pred);
        }
    }
    
    public void addSuccessor(BasicBlock succ) {
        if (!successors.contains(succ)) {
            successors.add(succ);
        }
    }
    
    @Override
    public Iterator<TAC> iterator() {
        return instructions.iterator();
    }
    
    @Override
    public void resetVisited() {
        visited = false;
    }
    
    public List<Phi> getPhis() {
        return phiFunctions;
    }
    
    public void addPhi(Phi phi) {
        phiFunctions.add(phi);
    }
    
    @Override
    public void accept(CFGVisitor visitor) {
        visitor.visit(this);
    }
}
