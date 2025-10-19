package ir.cfg;

import java.util.Iterator;

public class BasicBlock extends Block implements Iterable<TAC> {

    private int num; // block number;
    private List<TAC> instructions;


    private List<BasicBlock> predecessors;
    private List<BasicBlock> successors;
    
    
    
    @Override
    public Iterator<TAC> iterator() {
        return instructions.iterator();
    }
}