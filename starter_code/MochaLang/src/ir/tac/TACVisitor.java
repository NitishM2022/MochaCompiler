package ir.tac;

public interface TACVisitor {
    void visit(Add add);

    void visit(Sub sub);

    void visit(Mul mul);

    void visit(Div div);

    void visit(Mod mod);

    void visit(Pow pow);

    void visit(And and);

    void visit(Or or);

    void visit(Not not);

    void visit(Neg neg);

    void visit(Cmp cmp);

    void visit(Load load);

    void visit(Store store);

    void visit(StoreGP storeGP);

    void visit(Adda adda);

    void visit(Bra bra);

    void visit(Bne bne);

    void visit(Beq beq);

    void visit(Ble ble);

    void visit(Blt blt);

    void visit(Bge bge);

    void visit(Bgt bgt);

    void visit(Read read);

    void visit(Write write);

    void visit(ReadB readB);

    void visit(WriteB writeB);

    void visit(WriteNL writeNL);

    void visit(End end);

    void visit(Phi phi);

    void visit(Call call);

    void visit(Return returnStmt);

    void visit(Mov mov);

    void visit(LoadFP loadFP);

    void visit(LoadGP loadGP);

    void visit(AddaGP addaGP);

    void visit(AddaFP addaFP);
}
