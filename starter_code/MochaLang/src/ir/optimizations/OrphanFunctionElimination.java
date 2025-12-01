package ir.optimizations;

import java.util.*;
import ir.cfg.CFG;
import ir.cfg.BasicBlock;
import ir.tac.*;

public class OrphanFunctionElimination extends BaseOptimization {
    
    public OrphanFunctionElimination(Optimizer optimizer) { 
        super(optimizer); 
    }
    
    @Override
    protected String getName() { 
        return "OFE"; 
    }
    
    @Override
    public boolean optimize(CFG cfg) {
        return false;
    }
    
    public boolean eliminateOrphans(List<CFG> cfgs) {
        if (cfgs == null || cfgs.isEmpty()) return false;
        
        Map<String, Set<String>> callGraph = buildCallGraph(cfgs);
        Map<String, CFG> cfgMap = new HashMap<>();
        for (CFG cfg : cfgs) {
            cfgMap.put(cfg.getFunctionName(), cfg);
        }
        
        Set<String> reachable = findReachableFunctions(callGraph, "main");
        
        boolean changed = false;
        List<CFG> orphans = new ArrayList<>();
        
        for (CFG cfg : cfgs) {
            String funcName = cfg.getFunctionName();
            if (!reachable.contains(funcName)) {
                orphans.add(cfg);
                changed = true;
            }
        }
        
        if (changed) {
            cfgs.removeAll(orphans);
            log("Eliminated " + orphans.size() + " orphan function(s)");
        }
        
        return changed;
    }
    
    private Map<String, Set<String>> buildCallGraph(List<CFG> cfgs) {
        Map<String, Set<String>> callGraph = new HashMap<>();
        
        for (CFG cfg : cfgs) {
            String caller = cfg.getFunctionName();
            Set<String> callees = new HashSet<>();
            
            for (BasicBlock block : cfg.getAllBlocks()) {
                if (block == null) continue;
                
                for (TAC instruction : block.getInstructions()) {
                    if (instruction instanceof Call) {
                        Call call = (Call) instruction;
                        String callee = call.getFunction().name();
                        callees.add(callee);
                    }
                }
            }
            
            callGraph.put(caller, callees);
        }
        
        return callGraph;
    }
    
    private Set<String> findReachableFunctions(Map<String, Set<String>> callGraph, String start) {
        Set<String> reachable = new HashSet<>();
        Queue<String> worklist = new LinkedList<>();
        
        worklist.add(start);
        reachable.add(start);
        
        while (!worklist.isEmpty()) {
            String current = worklist.poll();
            
            Set<String> callees = callGraph.get(current);
            if (callees != null) {
                for (String callee : callees) {
                    if (!reachable.contains(callee)) {
                        reachable.add(callee);
                        worklist.add(callee);
                    }
                }
            }
        }
        
        return reachable;
    }
}
