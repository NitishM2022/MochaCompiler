package mocha;

import java.util.HashSet;
import java.util.Set;

public enum NonTerminal {

    // nonterminal FIRST sets for grammar

    // operators
    POW_OP(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.POW);
        }
    }),
    MUL_OP(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.MUL);
            add(Token.Kind.DIV);
            add(Token.Kind.MOD);
            add(Token.Kind.AND);
        }
    }),
    ADD_OP(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.ADD);
            add(Token.Kind.SUB);
            add(Token.Kind.OR);
        }
    }),
    REL_OP(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.EQUAL_TO);
            add(Token.Kind.NOT_EQUAL);
            add(Token.Kind.LESS_THAN);
            add(Token.Kind.LESS_EQUAL);
            add(Token.Kind.GREATER_EQUAL);
            add(Token.Kind.GREATER_THAN);
        }
    }),
    ASSIGN_OP(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.ASSIGN);
            add(Token.Kind.ADD_ASSIGN);
            add(Token.Kind.SUB_ASSIGN);
            add(Token.Kind.MUL_ASSIGN);
            add(Token.Kind.DIV_ASSIGN);
            add(Token.Kind.MOD_ASSIGN);
            add(Token.Kind.POW_ASSIGN);
        }
    }),
    UNARY_OP(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.UNI_INC);
            add(Token.Kind.UNI_DEC);
        }
    }),

    // literals (integer and float handled by Scanner)
    BOOL_LIT(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.TRUE);
            add(Token.Kind.FALSE);
        }
    }),
    LITERAL(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.INT_VAL);
            add(Token.Kind.FLOAT_VAL);
            add(Token.Kind.TRUE);
            add(Token.Kind.FALSE);
        }
    }),

    // designator (ident handled by Scanner)
    DESIGNATOR(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.IDENT);
        }
    }),

    // expressions
    GROUP_EXPR(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.TRUE);
            add(Token.Kind.FALSE);
            add(Token.Kind.INT_VAL);
            add(Token.Kind.FLOAT_VAL);
            add(Token.Kind.IDENT);
            add(Token.Kind.NOT);
            add(Token.Kind.OPEN_PAREN);
            add(Token.Kind.CALL);
        }
    }),
    POW_EXPR(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.TRUE);
            add(Token.Kind.FALSE);
            add(Token.Kind.INT_VAL);
            add(Token.Kind.FLOAT_VAL);
            add(Token.Kind.IDENT);
            add(Token.Kind.NOT);
            add(Token.Kind.OPEN_PAREN);
            add(Token.Kind.CALL);
        }
    }),
    MULT_EXPR(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.TRUE);
            add(Token.Kind.FALSE);
            add(Token.Kind.INT_VAL);
            add(Token.Kind.FLOAT_VAL);
            add(Token.Kind.IDENT);
            add(Token.Kind.NOT);
            add(Token.Kind.OPEN_PAREN);
            add(Token.Kind.CALL);
        }
    }),
    ADD_EXPR(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.TRUE);
            add(Token.Kind.FALSE);
            add(Token.Kind.INT_VAL);
            add(Token.Kind.FLOAT_VAL);
            add(Token.Kind.IDENT);
            add(Token.Kind.NOT);
            add(Token.Kind.OPEN_PAREN);
            add(Token.Kind.CALL);
        }
    }),
    REL_EXPR(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.TRUE);
            add(Token.Kind.FALSE);
            add(Token.Kind.INT_VAL);
            add(Token.Kind.FLOAT_VAL);
            add(Token.Kind.IDENT);
            add(Token.Kind.NOT);
            add(Token.Kind.OPEN_PAREN);
            add(Token.Kind.CALL);
        }
    }),
    RELATION(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.OPEN_PAREN);
        }
    }),

    // statements
    ASSIGN(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.IDENT);
        }
    }),
    FUNC_CALL(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.CALL);
        }
    }),
    IF_STAT(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.IF);
        }
    }),
    WHILE_STAT(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.WHILE);
        }
    }),
    REPEAT_STAT(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.REPEAT);
        }
    }),
    RETURN_STAT(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.RETURN);
        }
    }),
    STATEMENT(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.IDENT);
            add(Token.Kind.CALL);
            add(Token.Kind.IF);
            add(Token.Kind.WHILE);
            add(Token.Kind.REPEAT);
            add(Token.Kind.RETURN);
        }
    }),
    STAT_SEQ(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.IDENT);
            add(Token.Kind.CALL);
            add(Token.Kind.IF);
            add(Token.Kind.WHILE);
            add(Token.Kind.REPEAT);
            add(Token.Kind.RETURN);
        }
    }),

    // declarations
    TYPE_DECL(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.BOOL);
            add(Token.Kind.INT);
            add(Token.Kind.FLOAT);
            add(Token.Kind.VOID);
        }
    }),
    VAR_DECL(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.BOOL);
            add(Token.Kind.INT);
            add(Token.Kind.FLOAT);
            add(Token.Kind.VOID);
        }
    }),
    PARAM_TYPE(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.BOOL);
            add(Token.Kind.INT);
            add(Token.Kind.FLOAT);
            add(Token.Kind.VOID);
        }
    }),
    PARAM_DECL(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.BOOL);
            add(Token.Kind.INT);
            add(Token.Kind.FLOAT);
            add(Token.Kind.VOID);
        }
    }),

    // functions
    FORMAL_PARAM(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.OPEN_PAREN);
        }
    }),
    FUNC_BODY(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.OPEN_BRACE);
        }
    }),
    FUNC_DECL(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.FUNC);
        }
    }),

    // computation
    COMPUTATION(new HashSet<Token.Kind>() {
        private static final long serialVersionUID = 1L;
        {
            add(Token.Kind.MAIN);
        }
    })
    ;

    private final Set<Token.Kind> firstSet = new HashSet<>();

    private NonTerminal (Set<Token.Kind> set) {
        firstSet.addAll(set);
    }

    public final Set<Token.Kind> firstSet () {
        return firstSet;
    }
}
