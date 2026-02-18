package ir;

import java.util.List;
import ir.cfg.CFG;

/**
 * Wrapper for List<CFG> that provides asDotGraph() method.
 * This is needed for compatibility with CompilerTester.
 */
public class IROutput {
    private List<CFG> cfgs;
    
    public IROutput(List<CFG> cfgs) {
        this.cfgs = cfgs;
    }
    
    public List<CFG> getCFGs() {
        return cfgs;
    }
    
    /**
     * Generate dot graph representation of all CFGs.
     */
    public String asDotGraph() {
        StringBuilder output = new StringBuilder();
        for (CFG cfg : cfgs) {
            output.append(cfg.asDotGraph()).append("\n");
        }
        return output.toString();
    }
}

