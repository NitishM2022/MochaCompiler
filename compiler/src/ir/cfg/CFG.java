package ir.cfg;

import java.util.ArrayList;
import java.util.List;
import ir.ssa.DominatorAnalysis;
import ir.tac.Variable;
import mocha.Symbol;

public class CFG {
    private Symbol functionSymbol;
    private BasicBlock entryBlock;
    private List<BasicBlock> blocks;
    private DominatorAnalysis domAnalysis;
    private List<Variable> parameters; // Function parameters
    private int frameSize = 0; // Total bytes for locals + temps

    public CFG(Symbol functionSymbol) {
        this.functionSymbol = functionSymbol;
        this.blocks = new ArrayList<>();
        this.parameters = new ArrayList<>();
    }

    public int getFrameSize() {
        return frameSize;
    }

    public void setFrameSize(int frameSize) {
        this.frameSize = frameSize;
    }

    public String getFunctionName() {
        return functionSymbol.name();
    }

    public Symbol getFunctionSymbol() {
        return functionSymbol;
    }

    public BasicBlock getEntryBlock() {
        return entryBlock;
    }

    public void setEntryBlock(BasicBlock entryBlock) {
        this.entryBlock = entryBlock;
    }

    public List<BasicBlock> getAllBlocks() {
        return blocks;
    }

    public void addBlock(BasicBlock block) {
        if (!blocks.contains(block)) {
            blocks.add(block);
        }
    }

    public List<Variable> getParameters() {
        return parameters;
    }

    public void setParameters(List<Variable> parameters) {
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    public void addParameter(Variable param) {
        this.parameters.add(param);
    }

    public void removeBlock(BasicBlock block) {
        blocks.remove(block);
        // Also remove this block from successor/predecessor lists of other blocks
        for (BasicBlock successor : block.getSuccessors()) {
            successor.getPredecessors().remove(block);
        }
        for (BasicBlock predecessor : block.getPredecessors()) {
            predecessor.getSuccessors().remove(block);
        }

        // Any modification to the CFG structure invalidates the dominator analysis
        this.domAnalysis = null;
    }

    public void setDominatorAnalysis(DominatorAnalysis domAnalysis) {
        this.domAnalysis = domAnalysis;
    }

    public DominatorAnalysis getDominatorAnalysis() {
        if (this.domAnalysis == null) {
            DominatorAnalysis analysis = new DominatorAnalysis(this);
            analysis.analyze();
            this.domAnalysis = analysis;
        }
        return domAnalysis;
    }

    public String asDotGraph() {
        CFGPrinter printer = new CFGPrinter();
        // Get dominator analysis (recomputes if null/stale after CFG changes)
        printer.printCFG(this, getDominatorAnalysis());
        return printer.getResult();
    }

    public String asDotGraph(DominatorAnalysis domAnalysis) {
        CFGPrinter printer = new CFGPrinter();
        printer.printCFG(this, domAnalysis);
        return printer.getResult();
    }
}
