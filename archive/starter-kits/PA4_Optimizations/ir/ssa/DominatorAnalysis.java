package ir.ssa;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;

public class DominatorAnalysis {
    private CFG cfg;
    private Map<BasicBlock, Set<BasicBlock>> dominators;
    private Map<BasicBlock, BasicBlock> immediateDominators;
    private Map<BasicBlock, Set<BasicBlock>> dominanceFrontiers;
    
    public DominatorAnalysis(CFG cfg) {
        this.cfg = cfg;
        this.dominators = new HashMap<>();
        this.immediateDominators = new HashMap<>();
        this.dominanceFrontiers = new HashMap<>();
    }
    
    public void analyze() {
        computeDominators();
        computeImmediateDominators();
        computeDominanceFrontiers();
    }
    
    public void computeDominators() {
        BasicBlock entry = cfg.getEntryBlock();
        Set<BasicBlock> allBlocks = new HashSet<>(cfg.getAllBlocks());
        
        dominators.put(entry, new HashSet<>(Arrays.asList(entry)));
        
        for (BasicBlock block : allBlocks) {
            if (block != entry) {
                dominators.put(block, new HashSet<>(allBlocks));
            }
        }
        
        boolean changed;
        do {
            changed = false;
            
            for (BasicBlock block : allBlocks) {
                if (block == entry) continue;
                
                Set<BasicBlock> newDominators = new HashSet<>();
                newDominators.add(block);
                
                List<BasicBlock> predecessors = block.getPredecessors();
                if (!predecessors.isEmpty()) {
                    Set<BasicBlock> intersect = new HashSet<>(dominators.get(predecessors.get(0)));
                    
                    for (int i = 1; i < predecessors.size(); i++) {
                        BasicBlock pred = predecessors.get(i);
                        intersect.retainAll(dominators.get(pred));
                    }
                    
                    newDominators.addAll(intersect);
                }
                
                if (!newDominators.equals(dominators.get(block))) {
                    dominators.put(block, newDominators);
                    changed = true;
                }
            }
        } while (changed);
    }
    
    public void computeImmediateDominators() {
        for (BasicBlock block : cfg.getAllBlocks()) {
            Set<BasicBlock> blockDominators = dominators.get(block);
            BasicBlock immediateDominator = null;
            
            for (BasicBlock candidate : blockDominators) {
                if (candidate == block) continue;
                
                boolean isImmediate = true;
                for (BasicBlock other : blockDominators) {
                    if (other != block && other != candidate) {
                        if (!dominators.get(other).contains(candidate)) {
                            isImmediate = false;
                            break;
                        }
                    }
                }
                
                if (isImmediate) {
                    immediateDominator = candidate;
                    break;
                }
            }
            
            immediateDominators.put(block, immediateDominator);
        }
    }
    
    public void computeDominanceFrontiers() {
        for (BasicBlock block : cfg.getAllBlocks()) {
            dominanceFrontiers.put(block, new HashSet<>());
        }
        
        for (BasicBlock block : cfg.getAllBlocks()) {
            BasicBlock idom = immediateDominators.get(block);
            if (idom == null) continue;
            
            List<BasicBlock> predecessors = block.getPredecessors();
            for (BasicBlock runner : predecessors) {
                BasicBlock current = runner;
                while (current != idom && current != block) {
                    dominanceFrontiers.get(current).add(block);
                    current = immediateDominators.get(current);
                    if (current == null) break;
                }
            }
        }
    }
    
    public BasicBlock getImmediateDominator(BasicBlock block) {
        return immediateDominators.get(block);
    }
    
    public Set<BasicBlock> getDominanceFrontier(BasicBlock block) {
        return dominanceFrontiers.get(block);
    }
    
    public Set<BasicBlock> getDominators(BasicBlock block) {
        return dominators.get(block);
    }
    
    public List<BasicBlock> getDomTreeChildren(BasicBlock block) {
        List<BasicBlock> children = new ArrayList<>();
        for (Map.Entry<BasicBlock, BasicBlock> entry : immediateDominators.entrySet()) {
            if (entry.getValue() == block) {
                children.add(entry.getKey());
            }
        }
        return children;
    }
    
    public void printDominatorTree() {
        System.out.println("Dominator Tree:");
        printDomTreeHelper(cfg.getEntryBlock(), 0);
    }
    
    private void printDomTreeHelper(BasicBlock block, int indent) {
        for (int i = 0; i < indent; i++) {
            System.out.print("  ");
        }
        System.out.println("BB" + block.getNum());
        
        for (BasicBlock child : getDomTreeChildren(block)) {
            printDomTreeHelper(child, indent + 1);
        }
    }
}
