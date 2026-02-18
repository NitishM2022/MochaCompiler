package ir.regalloc;

import ir.cfg.BasicBlock;
import ir.cfg.CFG;
import ir.tac.*;
import mocha.Symbol;

import java.util.*;

public class RegisterAllocator {

    private final int numDataRegisters;
    private final Map<Integer, Variable> physicalRegisters;
    private final Set<Variable> reservedRegisters;

    private int localOffset = -4;
    private Map<Variable, Integer> localSlots = new HashMap<>();

    private static class InterferenceGraph {
        private final Map<Variable, Set<Variable>> adj = new HashMap<>();

        void addNode(Variable v) {
            adj.putIfAbsent(v, new HashSet<>());
        }

        void addEdge(Variable u, Variable v) {
            addNode(u);
            addNode(v);
            adj.get(u).add(v);
            adj.get(v).add(u);
        }

        Set<Variable> getNeighbors(Variable v) {
            return adj.getOrDefault(v, Collections.emptySet());
        }

        Set<Variable> getNodes() {
            return adj.keySet();
        }
    }

    public RegisterAllocator(int numDataRegisters) {
        this.numDataRegisters = Math.min(numDataRegisters, 25); // Cap to leave room for scratch regs (26-31)
        this.physicalRegisters = new HashMap<>();
        this.reservedRegisters = new HashSet<>();

        for (int i = 0; i <= 31; i++) {
            Variable reg = new Variable(new Symbol("R" + i), -1);
            physicalRegisters.put(i, reg);

            if (i == 0 || i >= 26) {
                reservedRegisters.add(reg);
            }
        }
    }

    public void allocate(List<CFG> cfgs) {

        for (CFG cfg : cfgs) {
            System.out.println("CFG: " + cfg.getFunctionName());
            System.out.println(cfg.asDotGraph());
            System.out.println();
        }
        
        // Pass 1 is gone; offsets are handled by IRGenerator now.
        for (CFG cfg : cfgs) {
            localOffset = -4;
            localSlots.clear();
            allocate(cfg);
        }
    }

    private void allocate(CFG cfg) {
        SSAElimination ssaElim = new SSAElimination();
        ssaElim.eliminatePhis(cfg);

        while (true) {
            Map<BasicBlock, Set<Variable>> liveIn = new HashMap<>();
            Map<BasicBlock, Set<Variable>> liveOut = new HashMap<>();
            computeLiveness(cfg, liveIn, liveOut);

            InterferenceGraph graph = buildInterferenceGraph(cfg, liveOut);

            Map<Variable, Integer> coloring = colorGraph(graph);

            if (coloring != null) {
                rewriteCode(cfg, coloring);
                break;
            } else {
                Variable toSpill = selectSpillCandidate(graph);
                spillVariable(cfg, toSpill);
            }
        }
    }

    private void computeLiveness(CFG cfg,
                                 Map<BasicBlock, Set<Variable>> liveIn,
                                 Map<BasicBlock, Set<Variable>> liveOut) {
        for (BasicBlock bb : cfg.getAllBlocks()) {
            liveIn.put(bb, new HashSet<>());
            liveOut.put(bb, new HashSet<>());
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            List<BasicBlock> blocks = cfg.getAllBlocks();

            for (int i = blocks.size() - 1; i >= 0; i--) {
                BasicBlock bb = blocks.get(i);

                // OUT[block] = Union of IN[successors]
                Set<Variable> out = new HashSet<>();
                for (BasicBlock succ : bb.getSuccessors()) {
                    out.addAll(liveIn.get(succ));
                }

                if (!out.equals(liveOut.get(bb))) {
                    liveOut.put(bb, out);
                    changed = true;
                }

                // IN[B] = USE[B] union (OUT[B] - DEF[B])
                Set<Variable> in = new HashSet<>(out);
                List<TAC> insts = bb.getInstructions();

                for (int j = insts.size() - 1; j >= 0; j--) {
                    TAC tac = insts.get(j);

                    // Remove DEF from IN
                    if (tac.getDest() instanceof Variable) {
                        Variable def = (Variable) tac.getDest();
                        // Globals are memory, not registers. 
                        // Note: LoadGP puts result into a temp, which IS a register, so we remove that temp.
                        if (!isGlobal(def)) {
                            in.remove(def);
                        }
                    }

                    // Add USEs to IN
                    for (Value op : tac.getOperands()) {
                        if (op instanceof Variable) {
                            Variable v = (Variable) op;
                            if (!isGlobal(v)) {
                                in.add(v);
                            }
                        }
                    }

                    // Special case: StoreGP source is a use
                    if (tac instanceof StoreGP) {
                        StoreGP sgp = (StoreGP) tac;
                        if (sgp.getSrc() instanceof Variable && !isGlobal((Variable) sgp.getSrc())) {
                            in.add((Variable) sgp.getSrc());
                        }
                    }
                }

                if (!in.equals(liveIn.get(bb))) {
                    liveIn.put(bb, in);
                    changed = true;
                }
            }
        }
    }

    private InterferenceGraph buildInterferenceGraph(CFG cfg, Map<BasicBlock, Set<Variable>> liveOut) {
        InterferenceGraph graph = new InterferenceGraph();

        for (BasicBlock bb : cfg.getAllBlocks()) {
            Set<Variable> live = new HashSet<>(liveOut.get(bb));
            live.removeIf(this::isGlobal);

            List<TAC> insts = bb.getInstructions();
            for (int i = insts.size() - 1; i >= 0; i--) {
                TAC tac = insts.get(i);

                // Handle Definition
                Variable def = null;
                if (tac.getDest() instanceof Variable) {
                    def = (Variable) tac.getDest();
                }

                if (def != null && !isGlobal(def)) {
                    for (Variable v : live) {
                        if (!v.equals(def)) {
                            graph.addEdge(def, v);
                        }
                    }
                    live.remove(def);
                    graph.addNode(def);
                }

                // Handle Uses
                for (Value op : tac.getOperands()) {
                    if (op instanceof Variable && !isGlobal((Variable) op)) {
                        live.add((Variable) op);
                        graph.addNode((Variable) op);
                    }
                }
                
                // Handle StoreGP source
                if (tac instanceof StoreGP) {
                    StoreGP sgp = (StoreGP) tac;
                    if (sgp.getSrc() instanceof Variable && !isGlobal((Variable) sgp.getSrc())) {
                        live.add((Variable) sgp.getSrc());
                        graph.addNode((Variable) sgp.getSrc());
                    }
                }
            }
        }
        return graph;
    }

    private Map<Variable, Integer> colorGraph(InterferenceGraph graph) {
        Map<Variable, Integer> coloring = new HashMap<>();
        Stack<Variable> stack = new Stack<>();
        Set<Variable> removed = new HashSet<>();
        Set<Variable> nodes = new HashSet<>(graph.getNodes());

        while (!nodes.isEmpty()) {
            Variable lowDegreeNode = null;
            for (Variable node : nodes) {
                int degree = 0;
                for (Variable neighbor : graph.getNeighbors(node)) {
                    if (!removed.contains(neighbor)) {
                        degree++;
                    }
                }
                if (degree < numDataRegisters) {
                    lowDegreeNode = node;
                    break;
                }
            }

            if (lowDegreeNode != null) {
                stack.push(lowDegreeNode);
                nodes.remove(lowDegreeNode);
                removed.add(lowDegreeNode);
            } else {
                return null; // Spill needed
            }
        }

        while (!stack.isEmpty()) {
            Variable node = stack.pop();
            Set<Integer> usedColors = new HashSet<>();
            for (Variable neighbor : graph.getNeighbors(node)) {
                if (coloring.containsKey(neighbor)) {
                    usedColors.add(coloring.get(neighbor));
                }
            }

            int color = -1;
            for (int c = 1; c <= numDataRegisters; c++) {
                if (!usedColors.contains(c)) {
                    color = c;
                    break;
                }
            }
            coloring.put(node, color);
        }
        return coloring;
    }

    private Variable selectSpillCandidate(InterferenceGraph graph) {
        Variable candidate = null;
        int maxDegree = -1;
        for (Variable node : graph.getNodes()) {
            int degree = graph.getNeighbors(node).size();
            if (degree > maxDegree) {
                maxDegree = degree;
                candidate = node;
            }
        }
        return candidate;
    }

    private void spillVariable(CFG cfg, Variable v) {
        int offset;
        // Use pre-assigned FP offset from Symbol if available
        if (v.getSymbol() != null && v.getSymbol().hasStackSlot()) {
            offset = v.getSymbol().getFpOffset();
        } else {
            if (!localSlots.containsKey(v)) {
                localSlots.put(v, localOffset);
                localOffset -= 4;
            }
            offset = localSlots.get(v);
        }

        Variable fp = physicalRegisters.get(28); // R28 = FP

        for (BasicBlock bb : cfg.getAllBlocks()) {
            List<TAC> newInsts = new ArrayList<>();

            for (TAC tac : bb.getInstructions()) {
                // 1. Rewrite Uses (Load from stack)
                List<Value> operands = tac.getOperands();
                boolean needLoad = false;
                List<Value> newOperands = new ArrayList<>();

                for (Value op : operands) {
                    if (op.equals(v)) {
                        Variable r26 = physicalRegisters.get(26); // Scratch
                        // Expand spill: Add R26, FP, offset -> Load R26, R26
                        newInsts.add(new Add(tac.getId(), r26, fp, new Immediate(offset)));
                        newInsts.add(new Load(tac.getId(), r26, r26));
                        newOperands.add(r26);
                        needLoad = true;
                    } else {
                        newOperands.add(op);
                    }
                }
                
                // Handle StoreGP source specially
                if (tac instanceof StoreGP) {
                    StoreGP sgp = (StoreGP) tac;
                    if (sgp.getSrc().equals(v)) {
                        Variable r26 = physicalRegisters.get(26);
                        newInsts.add(new Add(tac.getId(), r26, fp, new Immediate(offset)));
                        newInsts.add(new Load(tac.getId(), r26, r26));
                        sgp.setSrc(r26); 
                    }
                }

                if (needLoad) {
                    tac.setOperands(newOperands);
                }
                newInsts.add(tac);

                // 2. Rewrite Defs (Store to stack)
                Variable def = (tac.getDest() instanceof Variable) ? (Variable) tac.getDest() : null;

                if (def != null && def.equals(v)) {
                    Variable r27 = physicalRegisters.get(27); // Scratch
                    setDest(tac, r27);

                    newInsts.add(new Add(tac.getId(), physicalRegisters.get(26), fp, new Immediate(offset)));
                    newInsts.add(new Store(tac.getId(), r27, physicalRegisters.get(26)));
                }
            }
            bb.setInstructions(newInsts);
        }
    }

    private void rewriteCode(CFG cfg, Map<Variable, Integer> coloring) {
        for (BasicBlock bb : cfg.getAllBlocks()) {
            for (TAC tac : bb.getInstructions()) {
                
                // 1. Rewrite Operands
                List<Value> newOperands = new ArrayList<>();
                boolean changed = false;
                for (Value op : tac.getOperands()) {
                    if (op instanceof Variable && coloring.containsKey(op)) {
                        newOperands.add(physicalRegisters.get(coloring.get(op)));
                        changed = true;
                    } else {
                        newOperands.add(op);
                    }
                }
                if (changed) {
                    tac.setOperands(newOperands);
                }

                // 2. Rewrite Destination
                if (tac.getDest() instanceof Variable) {
                    Variable dest = (Variable) tac.getDest();
                    if (coloring.containsKey(dest)) {
                        setDest(tac, physicalRegisters.get(coloring.get(dest)));
                    }
                }

                // 3. Handle StoreGP Source
                if (tac instanceof StoreGP) {
                    StoreGP sgp = (StoreGP) tac;
                    if (sgp.getSrc() instanceof Variable && coloring.containsKey(sgp.getSrc())) {
                        sgp.setSrc(physicalRegisters.get(coloring.get(sgp.getSrc())));
                    }
                }
            }
        }
    }

    // Helper Methods
    private boolean isGlobal(Variable v) {
        return v.getSymbol() != null && v.getSymbol().isGlobal();
    }

    private void setDest(TAC tac, Variable dest) {
        if (tac instanceof Add) ((Add) tac).setDest(dest);
        else if (tac instanceof Sub) ((Sub) tac).setDest(dest);
        else if (tac instanceof Mul) ((Mul) tac).setDest(dest);
        else if (tac instanceof Div) ((Div) tac).setDest(dest);
        else if (tac instanceof Mod) ((Mod) tac).setDest(dest);
        else if (tac instanceof And) ((And) tac).setDest(dest);
        else if (tac instanceof Or) ((Or) tac).setDest(dest);
        else if (tac instanceof Neg) ((Neg) tac).setDest(dest);
        else if (tac instanceof Not) ((Not) tac).setDest(dest);
        else if (tac instanceof Mov) ((Mov) tac).setDest(dest);
        else if (tac instanceof Load) ((Load) tac).setDest(dest);
        else if (tac instanceof Call) ((Call) tac).setDest(dest);
        else if (tac instanceof Cmp) ((Cmp) tac).setDest(dest);
        else if (tac instanceof Read) ((Read) tac).setDest(dest);
        else if (tac instanceof ReadB) ((ReadB) tac).setDest(dest);
        else if (tac instanceof Assign) ((Assign) tac).setDest(dest);
        else if (tac instanceof LoadFP) ((LoadFP) tac).setDest(dest);
        else if (tac instanceof LoadGP) ((LoadGP) tac).setDest(dest);
    }
}