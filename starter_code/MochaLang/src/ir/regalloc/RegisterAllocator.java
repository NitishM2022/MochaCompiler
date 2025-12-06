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
        // Cap to R1-R24 to leave R25-R31 for scratch/system
        this.numDataRegisters = Math.min(numDataRegisters, 24);
        this.physicalRegisters = new HashMap<>();
        this.reservedRegisters = new HashSet<>();

        for (int i = 0; i <= 31; i++) {
            Variable reg = new Variable(new Symbol("R" + i), -1);
            physicalRegisters.put(i, reg);

            if (i == 0 || i >= 25) {
                reservedRegisters.add(reg);
            }
        }
    }

    public void allocate(List<CFG> cfgs) {
        for (CFG cfg : cfgs) {
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
                removeRedundantMoves(cfg);
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

                    if (tac.getDest() instanceof Variable) {
                        Variable def = (Variable) tac.getDest();
                        if (!isPhysicalRegister(def)) {
                            in.remove(def);
                        }
                    }

                    for (Value op : tac.getOperands()) {
                        if (op instanceof Variable) {
                            Variable v = (Variable) op;
                            if (!isPhysicalRegister(v)) {
                                in.add(v);
                            }
                        }
                    }

                    if (tac instanceof StoreGP) {
                        StoreGP sgp = (StoreGP) tac;
                        if (sgp.getSrc() instanceof Variable) {
                            Variable v = (Variable) sgp.getSrc();
                            if (!isPhysicalRegister(v)) {
                                in.add(v);
                            }
                        }
                    }

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

                for (Value op : tac.getOperands()) {
                    if (op instanceof Variable) {
                        Variable v = (Variable) op;
                        if (!isPhysicalRegister(v)) {
                            live.add(v);
                            graph.addNode(v);
                        }
                    }
                }

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
                return null;
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
        Variable baseReg;

        if (v.getSymbol() != null && v.getSymbol().isGlobal()) {
            offset = v.getSymbol().getGlobalOffset();
            baseReg = physicalRegisters.get(30);
            if (offset == 0) {
                throw new RuntimeException("Global variable " + v + " has no GP offset assigned by IRGenerator");
            }
        } else {
            if (v.getSymbol() != null && v.getSymbol().getFpOffset() != 0) {
                offset = v.getSymbol().getFpOffset();
            } else {
                throw new RuntimeException("Variable " + v + " has no FP offset assigned by IRGenerator");
            }
            baseReg = physicalRegisters.get(28);
        }

        for (BasicBlock bb : cfg.getAllBlocks()) {
            List<TAC> newInsts = new ArrayList<>();

            for (TAC tac : bb.getInstructions()) {
                List<Value> operands = tac.getOperands();
                boolean needLoad = false;
                List<Value> newOperands = new ArrayList<>();

                // Detect if we actually need to spill 'v' in this instruction
                boolean needSpill = false;
                for (Value op : operands) {
                    if (op != null && op.equals(v)) {
                        needSpill = true;
                        break;
                    }
                }

                int scratchIndex = 0;
                if (needSpill) {
                    // Detect if R27 or R26 are already used by previous spills in this instruction
                    boolean r27Used = false;
                    boolean r26Used = false;
                    for (Value op : operands) {
                        if (op == null) continue;
                        if (op.equals(physicalRegisters.get(27))) r27Used = true;
                        if (op.equals(physicalRegisters.get(26))) r26Used = true;
                    }

                    if (r27Used) scratchIndex = 1; // Skip R27 if used
                    if (r26Used && scratchIndex == 1) {
                         throw new RuntimeException("RegisterAllocator: Ran out of scratch registers for spilling instruction: " + tac);
                    }
                }

                // Use R26 for address computation (it's reserved for spilling, so safe to use)
                Variable addrReg = physicalRegisters.get(26);
                Variable[] valueScratchRegs = { physicalRegisters.get(27), physicalRegisters.get(26) };

                for (Value op : operands) {
                    // Skip null operands (some TAC instructions may have null operands)
                    if (op == null) {
                        newOperands.add(null);
                        continue;
                    }
                    
                    if (op.equals(v)) {
                        // Use R27 for first value, R26 for second (if both operands are spilled)
                        if (scratchIndex >= valueScratchRegs.length) {
                            throw new RuntimeException("Instruction has too many spilled operands (max 2 supported)");
                        }

                        Variable valueReg = valueScratchRegs[scratchIndex];
                        scratchIndex++;
                        
                        // Compute address in R26, then load value into valueReg (R27 or R26)
                        newInsts.add(new Add(tac.getId(), addrReg, baseReg, new Immediate(offset)));
                        newInsts.add(new Load(tac.getId(), valueReg, addrReg));

                        newOperands.add(valueReg);
                        needLoad = true;
                    } else {
                        newOperands.add(op);
                    }
                }

                if (tac instanceof StoreGP) {
                    StoreGP sgp = (StoreGP) tac;
                    if (sgp.getSrc().equals(v)) {
                        // Use addrReg (R25) for address, R27 for loaded value
                        Variable valueReg = physicalRegisters.get(27);
                        newInsts.add(new Add(tac.getId(), addrReg, baseReg, new Immediate(offset)));
                        newInsts.add(new Load(tac.getId(), valueReg, addrReg));
                        sgp.setSrc(valueReg);
                    }
                }

                if (needLoad) {
                    tac.setOperands(newOperands);
                }
                newInsts.add(tac);

                Variable def = (tac.getDest() instanceof Variable) ? (Variable) tac.getDest() : null;

                if (def != null && def.equals(v)) {
                    // We use R27 to hold the result value.
                    // Note: If R27 was used as an input scratch, that's fine,
                    // because the instruction has already executed by now.
                    Variable valueReg = physicalRegisters.get(27);
                    setDest(tac, valueReg);

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

                if (tac.getDest() instanceof Variable) {
                    Variable dest = (Variable) tac.getDest();
                    if (coloring.containsKey(dest)) {
                        setDest(tac, physicalRegisters.get(coloring.get(dest)));
                    }
                }

                if (tac instanceof StoreGP) {
                    StoreGP sgp = (StoreGP) tac;
                    if (sgp.getSrc() instanceof Variable && coloring.containsKey(sgp.getSrc())) {
                        sgp.setSrc(physicalRegisters.get(coloring.get(sgp.getSrc())));
                    }
                }
            }
        }
    }

    private boolean isPhysicalRegister(Variable v) {
        // Physical registers: R0, R25-R31 (R25=scratch for CodeGen, R26/R27=scratch for spilling,
        // R28=FP, R29=SP, R30=GP, R31=RA)
        // These should never be in the interference graph
        if (v.getSymbol() == null)
            return false;
        String name = v.getSymbol().name();
        if (!name.startsWith("R"))
            return false;
        try {
            int regNum = Integer.parseInt(name.substring(1));
            return regNum == 0 || (regNum >= 25 && regNum <= 31);
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

    private void removeRedundantMoves(CFG cfg) {
        for (BasicBlock bb : cfg.getAllBlocks()) {
            Iterator<TAC> it = bb.getInstructions().iterator();
            while (it.hasNext()) {
                TAC tac = it.next();
                if (tac instanceof Mov) {
                    Mov mov = (Mov) tac;
                    if (mov.getDest() instanceof Variable && !mov.getOperands().isEmpty() && mov.getOperands().get(0) instanceof Variable) {
                        Variable dest = (Variable) mov.getDest();
                        Variable src = (Variable) mov.getOperands().get(0);
                        if (dest.getSymbol() != null && src.getSymbol() != null && 
                            dest.getSymbol().name().equals(src.getSymbol().name())) {
                            it.remove();
                        }
                    }
                }
            }
        }
    }
}