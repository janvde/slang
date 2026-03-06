# Slang Programming Language

A lightweight programming language compiler written in Kotlin that compiles to LLVM IR and can be executed or compiled to native executables.

## Features

- **Simple syntax** inspired by modern languages
- **Type system** with Int, Float, Bool, String, Void, and List types
- **Literals** for all types: integers, floats (with scientific notation), booleans, strings (with escape sequences)
- **Variable mutability**: `let` (immutable) and `var` (mutable) with reassignment support
- **Arithmetic operations** (+, -, *, /, %) with operator precedence
- **Type promotion**: Automatic Int→Float conversion in mixed operations
- **String operations**: Concatenation with `+` operator (`String + String` in interpreter and LLVM/native)
- **Logical operators**: `!` (NOT), `&&` (AND), `||` (OR) with short-circuit evaluation
- **Control flow** with if/else statements, while loops, and C-style for loops
- **Loop control**: `break` and `continue`
- **Comparison operators** (>, <, >=, <=, ==, !=)
- **Functions** with parameters, return values, and `return` statements
- **Classes (class-lite)** with constructor fields, instance methods, and `this` (no inheritance)
- **Member operations**: field access (`obj.x`), field assignment (`obj.x = 1`), method calls (`obj.method()`)
- **List literals** with indexing (`xs[0]`) and length via `len(xs)`
- **Built-in length**: `len(List)` and `len(String)`
- **Strict Bool conditions**: `if` / `while` / `for` conditions must be `Bool`
- **Source diagnostics**: Errors include line/column locations
- **LLVM IR generation** for efficient compilation
- **Native code compilation** to standalone executables
- **Direct interpretation** for quick execution
- **Compiler scripts** for easy compilation to native binaries

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
│   │   │   └── TypeChecker.kt     # Static type checking and semantic validation
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

## Quick Start - Compile .slang to Binary

The easiest way to compile Slang programs is using the included compiler scripts:

### Using the Bash Script (Linux/macOS)
```bash
# Make script executable (first time only)
chmod +x slangc

# Compile a .slang file to binary
./slangc src/main/resources/example.slang my_program

# Compile and run immediately
./slangc src/main/resources/example.slang --run

# See all options
./slangc --help
```

### Using the Python Script (Cross-platform)
```bash
# Make script executable (first time only)
chmod +x slangc.py

# Compile a .slang file to binary
python slangc.py src/main/resources/example.slang my_program

# Compile and run immediately
python slangc.py src/main/resources/example.slang --run

# See all options
python slangc.py --help
```

### Using Make (Optional)
```bash
# Compile a specific file
make compile FILE=src/main/resources/example.slang OUT=my_program

# Compile and run
make run FILE=src/main/resources/example.slang

# Build the compiler
make build
```

### Script Options
- `--run` - Run the executable after compilation
- `--keep-ir` - Keep intermediate LLVM IR file for inspection
- `--verbose` - Show detailed compilation output
- `--help` - Display full usage information

For detailed documentation, see [`COMPILER_SCRIPTS_README.md`](COMPILER_SCRIPTS_README.md).

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

### Method 1: Use Compiler Scripts (Recommended)

The easiest way to compile Slang programs to native executables:

**Bash Script (Linux/macOS):**
```bash
# Compile to binary
./slangc input.slang output_binary

# Compile and run
./slangc input.slang --run

# With options
./slangc input.slang mybinary --keep-ir --verbose
```

**Python Script (Cross-platform):**
```bash
# Compile to binary
python slangc.py input.slang output_binary

# Compile and run
python slangc.py input.slang --run

# With options
python slangc.py input.slang mybinary --keep-ir --verbose
```

**Using Make:**
```bash
# Compile specific file
make compile FILE=hello.slang OUT=hello

# Compile and run
make run FILE=hello.slang
```

**Script Options:**
- `--run` - Execute the binary after compilation
- `--keep-ir` - Keep intermediate LLVM IR file
- `--verbose` - Show detailed compilation output
- `--help` - Display full usage information

See [`COMPILER_SCRIPTS_README.md`](COMPILER_SCRIPTS_README.md) for complete documentation.

### Method 2: Run with Gradle (For development)
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

For complete language syntax reference, see **[LANGUAGE_SYNTAX.md](LANGUAGE_SYNTAX.md)**.

### Quick Reference

**Variables:**
```slang
let x: Int = 10;           // Immutable
var counter: Int = 0;      // Mutable
counter = 5;               // Reassignment
```

**Control Flow:**
```slang
if (x > 5) { print(1); }

while (i < 10) {
    print(i);
    i = i + 1;
}

for (var j: Int = 0; j < 10; j = j + 1) {
    if (j == 4) { continue; }
    if (j == 8) { break; }
    print(j);
}
```

**Logical Operators:**
```slang
!flag                      // NOT
a && b                     // AND (short-circuit)
a || b                     // OR (short-circuit)
```

**Functions:**
```slang
fn add(a: Int, b: Int): Int {
    return a + b;
}
```

**Classes:**
```slang
class Counter(var value: Int) {
    fn add(delta: Int): Void {
        this.value = this.value + delta;
        return;
    }
}

let c: Counter = Counter(1);
c.add(2);
print(c.value);  // 3
```

**Data Types:** Int, Float, Bool, String, List, Void, and user-defined `class` types

### Condition Semantics

Conditions in `if`, `while`, and `for` must evaluate to `Bool`.

```slang
let x: Int = 1;
if (x > 0) { print(1); }   // valid
if (x) { print(1); }       // type error
```

See [LANGUAGE_SYNTAX.md](LANGUAGE_SYNTAX.md) for:
- Complete syntax reference
- All operators and precedence
- Type system details
- Scoping rules
- Code examples and patterns
- Best practices

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
- **TypeChecker.kt**: Enforces static type rules before execution/codegen
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

## Compiler Scripts

Slang includes convenient compiler scripts that handle the entire compilation pipeline from `.slang` source files to native executables:

### Available Scripts

1. **`slangc`** - Bash script (Linux/macOS)
   - Full-featured compilation pipeline
   - Colorized output
   - Automatic dependency checking

2. **`slangc.py`** - Python script (Cross-platform)
   - Works on Windows, Linux, macOS
   - Same features as bash version
   - Requires Python 3.6+

3. **`Makefile`** - Make targets
   - `make build` - Build the compiler
   - `make compile FILE=...` - Compile a file
   - `make run FILE=...` - Compile and run
   - `make install` - Install scripts system-wide

### Quick Examples

```bash
# Compile a program
./slangc hello.slang

# Compile with custom output name
./slangc hello.slang my_program

# Compile and run immediately
./slangc hello.slang --run

# Keep intermediate files for inspection
./slangc hello.slang --keep-ir

# Verbose output for debugging
./slangc hello.slang --verbose
```

### Installation

```bash
# Local use (from project directory)
chmod +x slangc slangc.py

# System-wide installation
sudo make install
# Or manually:
sudo cp slangc slangc.py /usr/local/bin/
```

### Compilation Pipeline

```
.slang source → Slang Compiler → LLVM IR → llc → Object File → clang → Native Binary
```

## Future Enhancements

- [ ] Arrays and complex data types
- [ ] Optimization passes
- [ ] More built-in functions
- [ ] String manipulation functions
- [ ] Module/import system

## Testing

Run the test suite:
```bash
./gradlew test
```

Test compilation scripts:
```bash
# Test basic compilation
./slangc src/main/resources/example.slang test_output --run

# Test all example programs
make examples
```

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

## References

- [LLVM Documentation](https://llvm.org/docs/)
- [ANTLR Documentation](https://www.antlr.org/)
- [JavaCPP Bindings for LLVM](https://github.com/bytedeco/javacpp-presets)
