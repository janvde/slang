# CLAUDE.md

## Project Overview

Slang is a statically-typed programming language compiler written in Kotlin. It compiles Slang source code to LLVM IR and optionally to native executables. The project also includes a direct AST interpreter for quick execution without LLVM.

## Build & Test

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run the compiler
./gradlew run --args="path/to/input.slang output.ll"

# Compile and run a Slang program (requires LLVM tools)
./slangc input.slang --run
```

**Prerequisites:** Java 21+. For native compilation: `brew install llvm` (macOS) or `apt-get install llvm clang` (Linux).

## Architecture

The compiler pipeline runs in six phases:

1. **Lexing/Parsing** — ANTLR 4 grammar (`src/main/antlr/Slang.g4`) generates `SlangLexer` and `SlangParser`.
2. **AST Building** — `parser/ASTBuilder.kt` (facade) delegates to `parser/builder/` sub-builders that convert the ANTLR parse tree to typed AST nodes (`ast/ASTNodes.kt`).
3. **Semantic Analysis** — `semantic/TypeChecker.kt` (facade) runs two passes: `DeclarationCollectorPass` then `StatementCheckerPass`. Supporting modules: `ScopeStack`, `ExprTyper`, `DiagnosticFactory`, `BuiltinRegistry`, `CallResolver`, `TypeRules`.
4. **Interpretation** — `interpreter/Interpreter.kt` (facade) walks the AST directly via `runtime/` subsystems: `StatementExecutor`, `ExpressionEvaluator`, `BuiltinRuntime`, `NumericOps`, `RuntimeContext`.
5. **Code Generation** — `codegen/CodeGenerator.kt` (facade) emits LLVM IR via JavaCPP bindings. Sub-emitters: `DeclarationEmitter`, `BuiltinCallEmitter`, `RuntimeDeclEmitter`, `TypeLowering`, `ValueCaster`, `CodegenContext`.
6. **Native Compilation** — `compiler/Compiler.kt` invokes `llc` + `clang` to produce a native executable from the generated IR.

Entry point: `src/main/kotlin/Main.kt`.

## Key Technologies

- **Kotlin 2.0.21** on JVM 21
- **ANTLR 4.12.0** for lexer/parser generation
- **LLVM 17.0.6** via JavaCPP bindings

## Code Structure Conventions

- Each major compiler phase has a **facade** class (e.g., `TypeChecker`, `Interpreter`, `CodeGenerator`) that delegates to focused helper classes in a subdirectory.
- New semantic checks belong in `semantic/typechecker/` or `semantic/core/`.
- New built-in functions require entries in `semantic/core/BuiltinRegistry.kt`, `interpreter/runtime/BuiltinRuntime.kt`, and `codegen/BuiltinCallEmitter.kt`.
- AST nodes are sealed classes in `ast/ASTNodes.kt`; adding a new node requires handling it in all visitor/emitter switch branches.

## Running Tests

Tests live in `src/test/` and cover: Parser, TypeChecker, Interpreter, and CodeGenerator. Run with `./gradlew test`. Tests use the Kotlin test framework on JUnit Platform.
