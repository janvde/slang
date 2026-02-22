# Slang Compiler Scripts

This directory contains two compiler scripts for compiling `.slang` files to native executables.

## Available Scripts

### 1. `slangc` (Bash script)
A bash script for Unix-like systems (Linux, macOS).

**Usage:**
```bash
./slangc <input.slang> [output_binary]
./slangc --help
```

**Examples:**
```bash
# Compile to default output
./slangc src/main/resources/example.slang

# Compile to specific binary
./slangc src/main/resources/example.slang my_program

# Compile and run
./slangc src/main/resources/example.slang --run

# Keep intermediate LLVM IR file
./slangc src/main/resources/example.slang --keep-ir

# Verbose output
./slangc src/main/resources/example.slang --verbose
```

### 2. `slangc.py` (Python script)
A Python script for cross-platform compatibility (Windows, Linux, macOS).

**Usage:**
```bash
python slangc.py <input.slang> [output_binary]
python slangc.py --help
```

**Examples:**
```bash
# Compile to default output
python slangc.py src/main/resources/example.slang

# Compile to specific binary
python slangc.py src/main/resources/example.slang my_program

# Compile and run
python slangc.py src/main/resources/example.slang --run

# Keep intermediate LLVM IR file
python slangc.py src/main/resources/example.slang --keep-ir

# Verbose output
python slangc.py src/main/resources/example.slang --verbose
```

## Options

Both scripts support the following options:

| Option | Description |
|--------|-------------|
| `--help` | Show help message and usage |
| `--version` | Show version information |
| `--keep-ir` | Keep the intermediate LLVM IR (.ll) file |
| `--verbose` | Show detailed compilation output |
| `--run` | Run the executable immediately after compilation |

## Requirements

### Essential
- **Java Runtime Environment (JRE)** - Required to run the Slang compiler
- **Gradle** - Build tool (or use the included `./gradlew`)

### Optional (for native compilation)
- **llc** - LLVM compiler (part of LLVM tools)
- **clang** - C compiler for linking

### Installing LLVM Tools

**macOS:**
```bash
brew install llvm
```

**Ubuntu/Debian:**
```bash
sudo apt-get install llvm clang
```

**Fedora/RHEL:**
```bash
sudo dnf install llvm clang
```

**Windows:**
Download and install from https://releases.llvm.org/

## Compilation Process

The scripts perform the following steps:

1. **Parse & Interpret** - The Slang compiler parses the source file and executes it with the interpreter
2. **Generate LLVM IR** - Generates intermediate representation (.ll file)
3. **Compile to Object** - Uses `llc` to compile IR to object file (.o)
4. **Link** - Uses `clang` to link the object file into an executable
5. **Cleanup** - Removes intermediate files (unless `--keep-ir` is used)

## Environment Variables

Both scripts respect the following environment variables:

- `SLANG_VERBOSE` - Set to `1` for verbose output by default
- `SLANG_KEEP_IR` - Set to `1` to keep intermediate files by default

**Example:**
```bash
export SLANG_VERBOSE=1
./slangc example.slang
```

## Output Files

By default, the compiler generates:

- `output` - The executable binary (or custom name if specified)
- `output.ll` - LLVM IR (removed unless `--keep-ir` is used)
- `output.o` - Object file (removed unless `--keep-ir` is used)

## Error Handling

If compilation fails, the scripts will:
- Display error messages from the compiler
- Exit with a non-zero status code
- Keep intermediate files for debugging

If LLVM tools are not available:
- The LLVM IR file will still be generated
- Instructions will be provided for manual compilation
- You can compile manually using:
  ```bash
  llc -filetype=obj output.ll -o output.o
  clang output.o -o output
  ```

## Examples

### Compile and Run a Simple Program

```bash
# Create a simple program
cat > hello.slang << 'EOF'
let message: String = "Hello, Slang!";
print(message);
EOF

# Compile and run
./slangc hello.slang hello --run
```

### Compile with Debugging Information

```bash
# Compile and keep intermediate files
./slangc complex_program.slang --keep-ir --verbose

# Inspect the LLVM IR
cat complex_program.ll

# Manually optimize and recompile if needed
opt -O3 complex_program.ll -o complex_program_opt.ll
llc -filetype=obj complex_program_opt.ll -o complex_program.o
clang complex_program.o -o complex_program
```

### Batch Compilation

```bash
# Compile multiple files
for file in src/main/resources/*.slang; do
    basename="${file%.slang}"
    output="$(basename "$basename")"
    echo "Compiling $file -> $output"
    ./slangc "$file" "$output"
done
```

## Troubleshooting

### "Java is not installed"
Install Java JRE from https://adoptium.net/ or your package manager.

### "Gradle is not installed and gradlew not found"
Run `./gradlew build` first to set up the Gradle wrapper, or install Gradle.

### "llc not found" or "clang not found"
Install LLVM tools for your platform (see Requirements section).

### "Compilation failed"
- Check the syntax of your `.slang` file
- Run with `--verbose` to see detailed error messages
- Verify the input file exists and is readable

### Native executable not generated
If llc/clang are not available, you'll get an LLVM IR file instead.
You can:
1. Install LLVM tools and recompile
2. Use the LLVM interpreter: `lli output.ll`
3. Manually compile using the instructions provided

## Integration with IDEs

### VS Code
Add to `.vscode/tasks.json`:
```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "Compile Slang",
            "type": "shell",
            "command": "./slangc",
            "args": ["${file}", "${fileBasenameNoExtension}"],
            "group": {
                "kind": "build",
                "isDefault": true
            }
        }
    ]
}
```

### IntelliJ IDEA
Add as an External Tool:
- Program: `${ProjectFileDir}/slangc`
- Arguments: `$FilePath$ $FileNameWithoutExtension$`
- Working directory: `$ProjectFileDir$`

## License

These scripts are part of the Slang project and follow the same license.

## Support

For issues or questions:
- Check the main README.md for language documentation
- Review example files in `src/main/resources/`
- File an issue on the project repository

---

**Quick Start:**
```bash
# Make scripts executable (Unix-like systems)
chmod +x slangc slangc.py

# Test compilation
./slangc src/main/resources/example.slang --run

# Or with Python
python slangc.py src/main/resources/example.slang --run
```

