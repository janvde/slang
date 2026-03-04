# Slang Roadmap (Next Functionality)

Last updated: 2026-03-04

## Current Baseline
- Parser, type checker, interpreter, and LLVM codegen are in place.
- Core language works: variables, control flow, functions, classes (no inheritance), lists, `len`.
- Test suite is green (`./gradlew test`).
- Main known gap: string concatenation works in interpreter but not in LLVM/native mode.

## Prioritized Roadmap

## 1) Close Interpreter/Native Parity for Strings (P0)
Goal: make string behavior identical between interpreter and LLVM output.

Scope:
- Implement LLVM codegen for `String + String`.
- Support mixed concatenation with non-strings by explicit conversion helpers (or fail with clear type errors if deferred).
- Ensure concatenated strings can be printed and passed through functions/methods.

Implementation areas:
- [src/main/kotlin/codegen/CodeGenerator.kt](/Users/jan/Projects/slang/src/main/kotlin/codegen/CodeGenerator.kt)
- [src/main/kotlin/semantic/TypeChecker.kt](/Users/jan/Projects/slang/src/main/kotlin/semantic/TypeChecker.kt)
- [src/test/kotlin/nl/endevelopment/CodeGeneratorTest.kt](/Users/jan/Projects/slang/src/test/kotlin/nl/endevelopment/CodeGeneratorTest.kt)
- [src/test/kotlin/nl/endevelopment/CompilerTest.kt](/Users/jan/Projects/slang/src/test/kotlin/nl/endevelopment/CompilerTest.kt)

Definition of done:
- Native run of `print("a" + "b");` outputs `ab`.
- At least 6 new tests for string concat paths (literal+literal, var+literal, expression nesting, function return, class field usage, failure cases).
- Docs updated to remove current limitation note.

## 2) Typed Lists and Safer Collection Semantics (P1)
Goal: move from Int-only list assumptions to typed lists that scale with the language.

Scope:
- Add typed list syntax, e.g. `List[Int]`, `List[String]`, `List[Point]`.
- Type-check list literals for homogeneity and enforce element type on index reads.
- Keep `len(List[...]) -> Int`.

Implementation areas:
- [src/main/antlr/Slang.g4](/Users/jan/Projects/slang/src/main/antlr/Slang.g4)
- [src/main/kotlin/semantic/SymbolTable.kt](/Users/jan/Projects/slang/src/main/kotlin/semantic/SymbolTable.kt)
- [src/main/kotlin/semantic/TypeChecker.kt](/Users/jan/Projects/slang/src/main/kotlin/semantic/TypeChecker.kt)
- [src/main/kotlin/ast/ASTNodes.kt](/Users/jan/Projects/slang/src/main/kotlin/ast/ASTNodes.kt)
- [src/main/kotlin/codegen/CodeGenerator.kt](/Users/jan/Projects/slang/src/main/kotlin/codegen/CodeGenerator.kt)

Definition of done:
- `let xs: List[Int] = [1,2,3]; let a: Int = xs[0];` passes.
- `let xs: List[Int] = [1, "x"];` fails with location-aware error.
- Existing untyped `List` programs either remain supported (compat mode) or have a documented migration path.

## 3) List Mutation + Basic Collection Built-ins (P1)
Goal: make lists practical for non-trivial programs.

Scope:
- Add index assignment: `xs[i] = v;`.
- Add built-ins: `push(xs, v)`, `pop(xs)`, optionally `clear(xs)`.
- Define bounds and empty-pop errors consistently in interpreter and native modes.

Implementation areas:
- Grammar + AST + type checker + interpreter + codegen.
- New runtime helpers for native path (reallocation/resize strategy).

Definition of done:
- Mutation and push/pop work in loops.
- Out-of-bounds and invalid-type operations report clear source locations.
- Bench test (simple append loop) is added to prevent severe regressions.

## 4) Better Diagnostics and Error Recovery (P1)
Goal: make compiler errors faster to understand and fix.

Scope:
- Improve type mismatch diagnostics with expected/actual formatting.
- Add related-location notes for duplicate definitions and symbol conflicts.
- Improve parser recovery so one syntax error does not hide downstream issues.

Definition of done:
- Diagnostics include location + concise fix-oriented message.
- At least 10 new tests for diagnostic quality and parser recovery.
- Error output is consistent across parser, type checker, and codegen phases.

## 5) Standard Library v1 (P1)
Goal: reduce user boilerplate with high-value built-ins.

Scope:
- Add string built-ins: `substr`, `contains`, `to_int`.
- Add numeric built-ins: `min`, `max`, `abs`.
- Keep semantics aligned between interpreter and LLVM/native.

Definition of done:
- Built-ins are available in both execution modes.
- Invalid argument types/arity produce location-aware errors.
- Syntax docs and examples include all new built-ins.

## 6) For-Each Loops (P1)
Goal: make iteration idiomatic and less index-heavy.

Scope:
- Add `for (item in list)` syntax (and optionally strings in the same release).
- Enforce loop variable typing from iterable element type.
- Preserve existing C-style `for` loop support.

Definition of done:
- For-each works in interpreter and LLVM/native modes.
- Type errors for non-iterables are precise and location-aware.
- Examples and tests cover list iteration and nested loops.

## 7) Developer Ergonomics: Local Type Inference (P2)
Goal: reduce annotation noise while keeping static guarantees.

Scope:
- Support `let x = expr;` and `var y = expr;`.
- Retain explicit annotations for function params/returns and class fields.
- Keep inference local (no global HM-style inference).

Definition of done:
- Inference works for literals, calls, member calls, and list literals.
- Ambiguous or void initializers fail with explicit diagnostics.

## 8) Module System (P2)
Goal: enable multi-file programs and reusable libraries.

Scope:
- Add `import` syntax and file/module resolution.
- Prevent circular imports with deterministic errors.
- Compile and type-check dependency graph before codegen.

Definition of done:
- Two-file program with shared functions/classes compiles and runs.
- Duplicate symbol and cycle errors include both file path and location.

## 9) Enums and Match Expressions (P2)
Goal: provide safer alternatives to large if/else chains.

Scope:
- Add enum declarations with variant values.
- Add `match` expression/statement with exhaustiveness checks.
- Integrate with type checker for unreachable or missing arms.

Definition of done:
- Exhaustiveness errors are emitted at compile time.
- Enum values are usable in functions, classes, and collections.
- Parser/type checker/codegen/interpreter tests cover happy-path and failures.

## 10) Generics for Types and Functions (P2)
Goal: improve reuse and type safety without duplicating APIs.

Scope:
- Add generic containers (starting with `List[T]` if not already fully generalized).
- Add generic functions like `fn id[T](x: T): T`.
- Constrain first iteration to compile-time monomorphization.

Definition of done:
- Generic function calls infer or validate concrete type arguments.
- Mismatched instantiations fail with clear diagnostics.
- Docs include generic syntax and limitations.

## 11) Map/Dictionary Type (P2)
Goal: support key-value data structures beyond lists.

Scope:
- Add `Map[K,V]` type and literal syntax.
- Add read/write/update operations and length support.
- Define key-type constraints for v1 (e.g., `Int`/`String`).

Definition of done:
- Common map operations work in both interpreter and LLVM/native modes.
- Missing-key behavior is clearly defined and tested.
- Type checker enforces key/value compatibility.

## 12) Visibility and Encapsulation (P2)
Goal: provide module-level API control once imports exist.

Scope:
- Add visibility modifiers (`public`/`private`) for functions, classes, and members.
- Enforce access checks at compile time across module boundaries.

Definition of done:
- Illegal access attempts fail at compile time with source locations.
- Default visibility rules are documented and tested.

## 13) Optimization Levels and Benchmarks (P2)
Goal: improve runtime performance predictably.

Scope:
- Add optimization mode flags (e.g. `-O0`, `-O1`, `-O2`) to compiler scripts/CLI.
- Add a small benchmark suite for representative language patterns.

Definition of done:
- Optimization flags propagate through build pipeline deterministically.
- Benchmark outputs are reproducible and tracked between releases.

## 14) Runtime Memory Strategy (P2)
Goal: establish a stable plan for allocation/lifetime behavior.

Scope:
- Decide and document memory model direction (manual ownership, ARC, or GC path).
- Align interpreter/native semantics for allocated values (strings, lists, objects).
- Add safety checks for high-risk memory operations in native runtime.

Definition of done:
- Written memory model notes in docs with current guarantees and non-goals.
- Runtime tests cover leak-prone and lifetime edge cases.
- Design leaves a clear migration path for future runtime improvements.

## Suggested Delivery Plan
1. Release 1.2: Item 1 (String parity), test/docs cleanup.
2. Release 1.3: Items 2, 4, and 5 (typed lists + diagnostics + stdlib v1).
3. Release 1.4: Items 3 and 6 (list mutation + for-each loops).
4. Release 1.5: Items 7 and 8 (local inference + modules/imports).
5. Release 1.6: Items 9 and 12 (enums/match + visibility/encapsulation).
6. Release 1.7: Items 10 and 11 (generics + map/dictionary).
7. Release 1.8: Items 13 and 14 (optimization levels + runtime memory strategy).

## Notes
- Keep a “parity matrix” test checklist so every new feature is validated in both interpreter and LLVM/native flows.
- For each milestone, land grammar + type checker + interpreter + codegen + tests in one PR to avoid feature drift.
