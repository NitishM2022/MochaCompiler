# Compiler Module

Core compiler implementation and helper scripts.

## Layout

- `src/mocha`: scanner/parser/compiler front-end and CLI
- `src/ast`: AST node types and visitors
- `src/types`: type system and checker
- `src/ir`: IR generation, SSA, optimizations, regalloc, TAC, codegen
- `scripts`: auxiliary analysis/document-generation scripts

## Entrypoint

`mocha.CompilerTester` in `src/mocha/CompilerTester.java`

## Build

```bash
javac -d target/classes -cp third_party/lib/commons-cli-1.9.0.jar -sourcepath compiler/src compiler/src/mocha/CompilerTester.java
```
