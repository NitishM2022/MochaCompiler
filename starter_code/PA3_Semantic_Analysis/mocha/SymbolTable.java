package mocha;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.List;
import java.util.ArrayList;
import types.*;

public class SymbolTable {

    private Stack<Map<String, List<Symbol>>> scopeStack;

    public SymbolTable () {
        scopeStack = new Stack<>();
        enterScope();
        initializePredefinedFunctions();
    }
    
    public void enterScope() {
        scopeStack.push(new HashMap<String, List<Symbol>>());
    }
    
    public void exitScope() {
        if (scopeStack.size() > 1) { // dont pop global scope
            scopeStack.pop();
        }
    }
    
    private Map<String, List<Symbol>> getCurrentScope() {
        return scopeStack.peek();
    }

    // lookup name in SymbolTable (returns first match for variables)
    public Symbol lookup (String name) throws SymbolNotFoundError {
        // Search from top (innermost) to bottom (global)
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            Map<String, List<Symbol>> scope = scopeStack.get(i);
            if (scope.containsKey(name) && !scope.get(name).isEmpty()) {
                return scope.get(name).get(0); // Return first symbol (for variables)
            }
        }
        throw new SymbolNotFoundError(name);
    }

    // lookup function by name and parameter types (for function overloading)
    public Symbol lookupFunction (String name, List<Type> paramTypes) throws SymbolNotFoundError {
        // Search from top (innermost) to bottom (global)
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            Map<String, List<Symbol>> scope = scopeStack.get(i);
            if (scope.containsKey(name)) {
                List<Symbol> symbols = scope.get(name);
                for (Symbol symbol : symbols) {
                    if (symbol.isFunction() && symbol.type() instanceof FuncType) {
                        FuncType funcType = (FuncType) symbol.type();
                        if (funcType.getParams().getList().size() == paramTypes.size()) {
                            boolean match = true;
                            for (int j = 0; j < paramTypes.size(); j++) {
                                if (!funcType.getParams().getList().get(j).equivalent(paramTypes.get(j))) {
                                    match = false;
                                    break;
                                }
                            }
                            if (match) {
                                return symbol;
                            }
                        }
                    }
                }
            }
        }
        throw new SymbolNotFoundError(name);
    }

    // insert name in SymbolTable
    public Symbol insert (String name) throws RedeclarationError {
        Map<String, List<Symbol>> currentScope = getCurrentScope();
        if (!currentScope.containsKey(name)) {
            currentScope.put(name, new ArrayList<Symbol>());
        }
        
        List<Symbol> symbols = currentScope.get(name);
        // Check for variable redeclaration (only one variable per name per scope)
        for (Symbol symbol : symbols) {
            if (!symbol.isFunction()) {
                throw new RedeclarationError(name);
            }
        }
        
        Symbol symbol = new Symbol(name);
        symbols.add(symbol);
        return symbol;
    }
    
    // insert symbol with type in current scope
    public Symbol insert (String name, Type type) throws RedeclarationError {
        Map<String, List<Symbol>> currentScope = getCurrentScope();
        if (!currentScope.containsKey(name)) {
            currentScope.put(name, new ArrayList<Symbol>());
        }
        
        List<Symbol> symbols = currentScope.get(name);
        // Check for variable redeclaration (only one variable per name per scope)
        for (Symbol symbol : symbols) {
            if (!symbol.isFunction()) {
                throw new RedeclarationError(name);
            }
        }
        
        Symbol symbol = new Symbol(name, type);
        symbols.add(symbol);
        return symbol;
    }
    
    // insert function symbol in current scope (supports overloading)
    public Symbol insertFunction (String name, Type type) throws RedeclarationError {
        Map<String, List<Symbol>> currentScope = getCurrentScope();
        if (!currentScope.containsKey(name)) {
            currentScope.put(name, new ArrayList<Symbol>());
        }
        
        List<Symbol> symbols = currentScope.get(name);
        
        // Check for function signature conflicts
        if (type instanceof FuncType) {
            FuncType newFuncType = (FuncType) type;
            for (Symbol symbol : symbols) {
                if (symbol.isFunction() && symbol.type() instanceof FuncType) {
                    FuncType existingFuncType = (FuncType) symbol.type();
                    if (existingFuncType.getParams().getList().size() == newFuncType.getParams().getList().size()) {
                        boolean sameSignature = true;
                        for (int i = 0; i < newFuncType.getParams().getList().size(); i++) {
                            if (!existingFuncType.getParams().getList().get(i).equivalent(newFuncType.getParams().getList().get(i))) {
                                sameSignature = false;
                                break;
                            }
                        }
                        if (sameSignature) {
                            throw new RedeclarationError(name);
                        }
                    }
                }
            }
        }
        
        Symbol symbol = new Symbol(name, type, true);
        symbols.add(symbol);
        return symbol;
    }
    
    private void initializePredefinedFunctions() {
        TypeList printIntParams = new TypeList();
        printIntParams.append(new IntType());
        FuncType printIntType = new FuncType(printIntParams, new VoidType());
        insertFunction("printInt", printIntType);
        
        TypeList printFloatParams = new TypeList();
        printFloatParams.append(new FloatType());
        FuncType printFloatType = new FuncType(printFloatParams, new VoidType());
        insertFunction("printFloat", printFloatType);
        
        TypeList printBoolParams = new TypeList();
        printBoolParams.append(new BoolType());
        FuncType printBoolType = new FuncType(printBoolParams, new VoidType());
        insertFunction("printBool", printBoolType);
        
        TypeList printlnParams = new TypeList();
        FuncType printlnType = new FuncType(printlnParams, new VoidType());
        insertFunction("println", printlnType);
        
        TypeList readIntParams = new TypeList();
        FuncType readIntType = new FuncType(readIntParams, new IntType());
        insertFunction("readInt", readIntType);
        
        TypeList readFloatParams = new TypeList();
        FuncType readFloatType = new FuncType(readFloatParams, new FloatType());
        insertFunction("readFloat", readFloatType);
        
        TypeList readBoolParams = new TypeList();
        FuncType readBoolType = new FuncType(readBoolParams, new BoolType());
        insertFunction("readBool", readBoolType);
    }

}

class SymbolNotFoundError extends Error {

    private static final long serialVersionUID = 1L;
    private final String name;

    public SymbolNotFoundError (String name) {
        super("Symbol " + name + " not found.");
        this.name = name;
    }

    public String name () {
        return name;
    }
}

class RedeclarationError extends Error {

    private static final long serialVersionUID = 1L;
    private final String name;

    public RedeclarationError (String name) {
        super("Symbol " + name + " being redeclared.");
        this.name = name;
    }

    public String name () {
        return name;
    }
}