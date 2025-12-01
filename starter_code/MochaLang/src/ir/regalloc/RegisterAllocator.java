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
        // Pass 1 is gone; offsets are handled by IRGenerator now.
        System.out.println("Reg Alloc");
        for (CFG cfg : cfgs) {
            System.out.println("CFG: " + cfg.getFunctionName());
            System.out.println(cfg.asDotGraph());
            System.out.println();
            
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
                        if (!isPhysicalRegister(def)) {
                            in.remove(def);
                        }
                    }

                    // Add USEs to IN
                    for (Value op : tac.getOperands()) {
                        if (op instanceof Variable) {
                            Variable v = (Variable) op;
                            if (!isPhysicalRegister(v)) {
                                in.add(v);
                            }
                        }
                    }

                    // Special case: StoreGP source is a use
                    if (tac instanceof StoreGP) {
                        StoreGP sgp = (StoreGP) tac;
                        if (sgp.getSrc() instanceof Variable) {
                            Variable v = (Variable) sgp.getSrc();
                            if (!isPhysicalRegister(v)) {
                                in.add(v);
                            }
                        }
                    }

                    // Special case: Store source is a use
                    if (tac instanceof Store) {
                        Store store = (Store) tac;
                        if (store.getSrc() instanceof Variable) {
                            Variable v = (Variable) store.getSrc();
                            if (!isPhysicalRegister(v)) {
                                in.add(v);
                            }
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

            List<TAC> insts = bb.getInstructions();
            for (int i = insts.size() - 1; i >= 0; i--) {
                TAC tac = insts.get(i);

                // Handle Definition
                Variable def = null;
                if (tac.getDest() instanceof Variable) {
                    def = (Variable) tac.getDest();
                }

                if (def != null && !isPhysicalRegister(def)) {
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
                    if (op instanceof Variable) {
                        Variable v = (Variable) op;
                        if (!isPhysicalRegister(v)) {
                            live.add(v);
                            graph.addNode(v);
                        }
                    }
                }

                // Handle StoreGP source
                if (tac instanceof StoreGP) {
                    StoreGP sgp = (StoreGP) tac;
                    if (sgp.getSrc() instanceof Variable) {
                        Variable v = (Variable) sgp.getSrc();
                        if (!isPhysicalRegister(v)) {
                            live.add(v);
                            graph.addNode(v);
                        }
                    }
                }

                // Handle Store source
                if (tac instanceof Store) {
                    Store store = (Store) tac;
                    if (store.getSrc() instanceof Variable) {
                        Variable v = (Variable) store.getSrc();
                        if (!isPhysicalRegister(v)) {
                            live.add(v);
                            graph.addNode(v);
                        }
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
        Variable baseReg; // Either GP or FP

        // Check if this is a global variable (use GP offset) or local (use FP offset)
        if (v.getSymbol() != null && v.getSymbol().isGlobal()) {
            // Global variable - spill to global memory
            offset = v.getSymbol().getGlobalOffset();
            baseReg = physicalRegisters.get(30); // R30 = GP
            if (offset == 0) {
                throw new RuntimeException("Global variable " + v + " has no GP offset assigned by IRGenerator");
            }
        } else {
            // Local variable or temp - spill to stack
            if (v.getSymbol() != null && v.getSymbol().getFpOffset() != 0) {
                offset = v.getSymbol().getFpOffset();
            } else {
                // If it's a temp without an offset, you might need to assign one here
                // or ensure IRGenerator did it. For now, assuming it exists:
                throw new RuntimeException("Variable " + v + " has no FP offset assigned by IRGenerator");
            }
            baseReg = physicalRegisters.get(28); // R28 = FP
        }

        // We use these two scratch registers to load operands safely
        Variable[] scratchRegs = { physicalRegisters.get(26), physicalRegisters.get(27) };

        for (BasicBlock bb : cfg.getAllBlocks()) {
            List<TAC> newInsts = new ArrayList<>();

            for (TAC tac : bb.getInstructions()) {

                // --- 1. Rewrite Uses (Load from memory) ---

                List<Value> operands = tac.getOperands();
                boolean needLoad = false;
                List<Value> newOperands = new ArrayList<>();

                // Track which scratch register we are using (0 or 1)
                int scratchIndex = 0;

                for (Value op : operands) {
                    if (op.equals(v)) {
                        // FIX: Cycle between R26 and R27 to avoid overwriting the first operand
                        // if both operands are the same spilled variable.
                        if (scratchIndex >= scratchRegs.length) {
                            throw new RuntimeException("Instruction has too many spilled operands (max 2 supported)");
                        }

                        Variable currentScratch = scratchRegs[scratchIndex];
                        scratchIndex++;

                        // Load logic:
                        // 1. Calc Address: currentScratch = baseReg + offset
                        // 2. Load Value: currentScratch = *currentScratch
                        newInsts.add(new Add(tac.getId(), currentScratch, baseReg, new Immediate(offset)));
                        newInsts.add(new Load(tac.getId(), currentScratch, currentScratch));

                        newOperands.add(currentScratch);
                        needLoad = true;
                    } else {
                        newOperands.add(op);
                    }
                }

                // Handle StoreGP source specially (It effectively has 1 operand)
                if (tac instanceof StoreGP) {
                    StoreGP sgp = (StoreGP) tac;
                    if (sgp.getSrc().equals(v)) {
                        // Use R26 (scratchRegs[0])
                        Variable r26 = scratchRegs[0];
                        newInsts.add(new Add(tac.getId(), r26, baseReg, new Immediate(offset)));
                        newInsts.add(new Load(tac.getId(), r26, r26));
                        sgp.setSrc(r26);
                    }
                }

                if (needLoad) {
                    tac.setOperands(newOperands);
                }
                newInsts.add(tac);

                // --- 2. Rewrite Defs (Store to memory) ---

                Variable def = (tac.getDest() instanceof Variable) ? (Variable) tac.getDest() : null;

                if (def != null && def.equals(v)) {
                    // We use R27 to hold the result value.
                    // Note: If R27 was used as an input scratch, that's fine,
                    // because the instruction has already executed by now.
                    Variable valueReg = physicalRegisters.get(27);
                    setDest(tac, valueReg);

                    // We use R26 to calculate the address
                    Variable addrReg = physicalRegisters.get(26);

                    newInsts.add(new Add(tac.getId(), addrReg, baseReg, new Immediate(offset)));
                    newInsts.add(new Store(tac.getId(), valueReg, addrReg));
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
    private boolean isPhysicalRegister(Variable v) {
        // Physical registers: R0, R26-R31 (R26/R27=scratch, R28=FP, R29=SP, R30=GP,
        // R31=RA)
        // These should never be in the interference graph
        if (v.getSymbol() == null)
            return false;
        String name = v.getSymbol().name();
        if (!name.startsWith("R"))
            return false;
        try {
            int regNum = Integer.parseInt(name.substring(1));
            return regNum == 0 || (regNum >= 26 && regNum <= 31);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void setDest(TAC tac, Variable dest) {
        if (tac instanceof Add)
            ((Add) tac).setDest(dest);
        else if (tac instanceof Sub)
            ((Sub) tac).setDest(dest);
        else if (tac instanceof Mul)
            ((Mul) tac).setDest(dest);
        else if (tac instanceof Div)
            ((Div) tac).setDest(dest);
        else if (tac instanceof Mod)
            ((Mod) tac).setDest(dest);
        else if (tac instanceof Pow)
            ((Pow) tac).setDest(dest);
        else if (tac instanceof And)
            ((And) tac).setDest(dest);
        else if (tac instanceof Or)
            ((Or) tac).setDest(dest);
        else if (tac instanceof Neg)
            ((Neg) tac).setDest(dest);
        else if (tac instanceof Not)
            ((Not) tac).setDest(dest);
        else if (tac instanceof Mov)
            ((Mov) tac).setDest(dest);
        else if (tac instanceof Load)
            ((Load) tac).setDest(dest);
        else if (tac instanceof LoadGP)
            ((LoadGP) tac).setDest(dest);
        else if (tac instanceof LoadFP)
            ((LoadFP) tac).setDest(dest);
        else if (tac instanceof Call)
            ((Call) tac).setDest(dest);
        else if (tac instanceof Cmp)
            ((Cmp) tac).setDest(dest);
        else if (tac instanceof Read)
            ((Read) tac).setDest(dest);
        else if (tac instanceof ReadB)
            ((ReadB) tac).setDest(dest);
        else if (tac instanceof Assign)
            ((Assign) tac).setDest(dest);
        else if (tac instanceof Adda)
            ((Adda) tac).setDest(dest);
        else if (tac instanceof AddaGP)
            ((AddaGP) tac).setDest(dest);
        else if (tac instanceof AddaFP)
            ((AddaFP) tac).setDest(dest);
    }
}