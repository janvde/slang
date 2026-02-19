# Slang Programming Language

A lightweight programming language compiler written in Kotlin that compiles to LLVM IR and can be executed or compiled to native executables.

## Features

- **Simple syntax** inspired by modern languages
- **Type system** with Int, Float, Bool, and String types
- **Control flow** with if/else statements
- **Comparison operators** (>, <, >=, <=, ==, !=)
- **Arithmetic operations** with operator precedence
- **LLVM IR generation** for efficient compilation
- **Native code compilation** to standalone executables
- **Direct interpretation** for quick execution

## Project Structure

```
slang/
├── src/main/
│   ├── antlr/
│   │   └── Slang.g4              # ANTLR grammar definition
│   ├── kotlin/
│   │   ├── Main.kt               # Entry point
│   │   ├── ast/
│   │   │   └── ASTNodes.kt        # Abstract Syntax Tree node definitions
│   │   ├── codegen/
│   │   │   └── CodeGenerator.kt   # LLVM IR code generation
│   │   ├── compiler/
│   │   │   └── Compiler.kt        # Main compiler logic
│   │   ├── interpreter/
│   │   │   └── Interpreter.kt     # Direct interpretation
│   │   ├── parser/
│   │   │   └── ASTBuilder.kt      # Parse tree to AST conversion
│   │   ├── semantic/
│   │   │   └── SymbolTable.kt     # Type checking and symbol management
│   │   └── utils/
│   │       └── Utils.kt           # Utility functions
│   └── resources/
│       └── example.slang          # Example Slang program
├── build.gradle.kts              # Gradle build configuration
└── gradle.properties             # Gradle properties (includes Java toolchain)
```

## Prerequisites

### Required
- **Java 21** or later (JDK)
- **Gradle** (included via gradlew)
- **LLVM tools** (for native compilation and execution)

### Installation

#### 1. Install Java 21
Ensure Java 21 is installed. The project is configured to use JBR 21 by default.

#### 2. Install LLVM (for native compilation)
On macOS:
```bash
brew install llvm
```

Add LLVM to your PATH:
```bash
echo 'export PATH="/opt/homebrew/opt/llvm/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

(On Intel Macs, use `/usr/local/opt/llvm/bin` instead)

## Building

```bash
./gradlew build
```

This will:
- Generate ANTLR parser from the grammar
- Compile Kotlin source code
- Run tests
- Create an executable JAR

## Running

### Method 1: Run with Gradle (Recommended for development)
```bash
./gradlew run --args="path/to/input.slang output.ll"
```

Example:
```bash
./gradlew run --args="src/main/resources/comparison_demo.slang output.ll"
```

This will:
1. Execute the program with the interpreter
2. Generate LLVM IR to `output.ll`
3. Compile to a native executable `output`

### Method 2: Execute Generated Output
After generation, you can run the output in three ways:

**Option A: With LLVM interpreter (fastest for testing)**
```bash
lli output.ll
```

**Option B: As compiled executable**
```bash
./output
```

**Option C: Recompile from LLVM IR**
```bash
clang output.ll -o output
./output
```

## Language Syntax

### Variable Declaration
```slang
let x: Int = 10;
let name: String = "hello";
let flag: Bool = true;
```

### Print Statement
```slang
print(x);
print(name);
```

### Arithmetic Operations
```slang
let a: Int = 2 + 3 * 4;  // Result: 14 (respects operator precedence)
let b: Int = 10 - 5;      // Result: 5
let c: Int = 3 * 4;       // Result: 12
let d: Int = 10 / 2;      // Result: 5
```

### Comparison Operators
```slang
if (x > 5) { print(1); }
if (x < 10) { print(2); }
if (x >= 10) { print(3); }
if (x <= 10) { print(4); }
if (x == 10) { print(5); }
if (x != 5) { print(6); }
```

### Control Flow
```slang
if (condition) {
    print(1);
} else {
    print(2);
}
```

### Example Program
See `src/main/resources/comparison_demo.slang` for a comprehensive example demonstrating all comparison operators.

## Architecture

### Compilation Pipeline
1. **Lexing & Parsing** (ANTLR): Source code → Parse tree
2. **AST Building**: Parse tree → Abstract Syntax Tree
3. **Semantic Analysis**: Type checking and symbol resolution
4. **Interpretation** (optional): Direct execution of AST
5. **Code Generation**: AST → LLVM IR
6. **Native Compilation** (optional): LLVM IR → Machine code

### Key Components

- **Interpreter.kt**: Executes the AST directly for quick testing
- **CodeGenerator.kt**: Generates LLVM IR from the AST using JavaCPP bindings
- **SymbolTable.kt**: Manages variable scopes and type information
- **ASTBuilder.kt**: Converts ANTLR parse trees to custom AST nodes

## Troubleshooting

### Build Issues

**Error: "llc/clang not found"**
- Ensure LLVM is installed and added to PATH
- Verify: `which llc` and `which clang`

**Error: Java version incompatibility**
- The project requires Java 21
- Check your Java version: `java -version`
- The gradle.properties file specifies the correct Java home

### Runtime Issues

**LLVM IR compilation fails**
- Check the generated `output.ll` for syntax errors
- Ensure you're using a recent version of LLVM (14+)

## Development

### Running Tests
```bash
./gradlew test
```

### Cleaning Build Artifacts
```bash
./gradlew clean
```

### Modifying the Grammar
Edit `src/main/antlr/Slang.g4` and the parser will be regenerated on the next build.

## Future Enhancements

- [ ] Loop constructs (while, for)
- [ ] Function definitions and calls
- [ ] Arrays and complex data types
- [ ] Better error messages and line number tracking
- [ ] Optimization passes
- [ ] More built-in functions

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

## References

- [LLVM Documentation](https://llvm.org/docs/)
- [ANTLR Documentation](https://www.antlr.org/)
- [JavaCPP Bindings for LLVM](https://github.com/bytedeco/javacpp-presets)


