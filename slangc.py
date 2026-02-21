#!/usr/bin/env python3
"""
slangc.py - Slang Compiler Script (Python version)
Compiles .slang source files to native executables

Usage: python slangc.py <input.slang> [output_binary]
"""

import sys
import os
import subprocess
import argparse
from pathlib import Path

# Colors
class Color:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    NC = '\033[0m'  # No Color

def info(msg):
    print(f"{Color.GREEN}[INFO]{Color.NC} {msg}")

def error(msg):
    print(f"{Color.RED}[ERROR]{Color.NC} {msg}", file=sys.stderr)
    sys.exit(1)

def warn(msg):
    print(f"{Color.YELLOW}[WARN]{Color.NC} {msg}")

def verbose(msg, is_verbose):
    if is_verbose:
        print(f"{Color.BLUE}[VERBOSE]{Color.NC} {msg}")

def check_command(cmd):
    """Check if a command exists"""
    try:
        subprocess.run([cmd, '--version'], capture_output=True, check=False)
        return True
    except FileNotFoundError:
        return False

def main():
    parser = argparse.ArgumentParser(
        description='Slang Compiler - Compile .slang files to native executables',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python slangc.py hello.slang              # Compiles to './output'
  python slangc.py hello.slang hello        # Compiles to './hello'
  python slangc.py hello.slang --run        # Compile and run
  python slangc.py test.slang --keep-ir     # Keep LLVM IR file
        """
    )

    parser.add_argument('input', help='Source .slang file to compile')
    parser.add_argument('output', nargs='?', default='output',
                       help='Output executable name (default: output)')
    parser.add_argument('--keep-ir', action='store_true',
                       help='Keep the intermediate LLVM IR file')
    parser.add_argument('--verbose', action='store_true',
                       help='Show detailed compilation output')
    parser.add_argument('--run', action='store_true',
                       help='Run the executable after compilation')
    parser.add_argument('--version', action='version', version='Slang Compiler 1.0-SNAPSHOT')

    args = parser.parse_args()

    # Validate input file
    input_file = Path(args.input)
    if not input_file.exists():
        error(f"Input file not found: {args.input}")

    if input_file.suffix != '.slang':
        warn("Input file does not have .slang extension")

    # Get script directory (where gradlew should be)
    script_dir = Path(__file__).parent.absolute()

    # Check dependencies
    if not check_command('java'):
        error("Java is not installed. Please install Java Runtime Environment.")

    verbose("Java found", args.verbose)

    # Check for gradlew
    gradlew = script_dir / 'gradlew'
    if sys.platform == 'win32':
        gradlew = script_dir / 'gradlew.bat'

    if not gradlew.exists():
        if not check_command('gradle'):
            error("Gradle is not installed and gradlew not found.")
        gradle_cmd = 'gradle'
    else:
        gradle_cmd = str(gradlew)

    # Check for LLVM tools
    has_llc = check_command('llc')
    has_clang = check_command('clang')

    if not has_llc:
        warn("llc not found. Native compilation may not be available.")
    if not has_clang:
        warn("clang not found. Native compilation may not be available.")

    # Setup paths
    input_absolute = input_file.absolute()
    output_path = Path(args.output)
    output_absolute = output_path.absolute()
    ir_file = output_absolute.with_suffix('.ll')
    obj_file = output_absolute.with_suffix('.o')

    info(f"Compiling {input_file.name} -> {output_path.name}")
    verbose(f"Input: {input_absolute}", args.verbose)
    verbose(f"Output: {output_absolute}", args.verbose)
    verbose(f"IR file: {ir_file}", args.verbose)

    # Compile with Slang compiler
    info("Running Slang compiler...")

    os.chdir(script_dir)

    try:
        cmd = [gradle_cmd, 'run', f'--args={input_absolute} {ir_file}', '--console=plain']

        if args.verbose:
            result = subprocess.run(cmd, check=True)
        else:
            result = subprocess.run(cmd, capture_output=True, text=True, check=True)
            # Show only important lines
            for line in result.stdout.split('\n'):
                if any(keyword in line for keyword in ['LOG', 'Error', 'error', 'FAILED']):
                    print(line)

    except subprocess.CalledProcessError as e:
        error("Compilation failed. Check the error messages above.")

    # Check if IR file was generated
    if not ir_file.exists():
        error(f"LLVM IR file was not generated: {ir_file}")

    info("LLVM IR generated successfully")

    # Check if native executable was created by the compiler
    if output_absolute.exists():
        info(f"Native executable generated: {output_absolute}")

        # Make it executable (Unix-like systems)
        if sys.platform != 'win32':
            os.chmod(output_absolute, 0o755)

        # Cleanup intermediate files if requested
        if not args.keep_ir:
            verbose("Cleaning up intermediate files...", args.verbose)
            ir_file.unlink(missing_ok=True)
            obj_file.unlink(missing_ok=True)
        else:
            info(f"Kept LLVM IR file: {ir_file}")

        print(f"{Color.GREEN}✓ Compilation successful!{Color.NC}")
        print(f"  Executable: {Color.BLUE}{output_absolute}{Color.NC}")
        print(f"  Size: {output_absolute.stat().st_size:,} bytes")

        # Run if requested
        if args.run:
            print()
            info("Running executable...")
            print("--- Output ---")
            subprocess.run([str(output_absolute)])
            print("--- End Output ---")

    else:
        warn("Native executable was not created automatically")
        info("Attempting manual compilation with llc and clang...")

        # Try to compile with llc and clang
        if has_llc and has_clang:
            try:
                verbose("Compiling IR to object file...", args.verbose)
                subprocess.run(['llc', '-filetype=obj', str(ir_file), '-o', str(obj_file)],
                             check=True, capture_output=not args.verbose)

                verbose("Linking object file...", args.verbose)
                subprocess.run(['clang', str(obj_file), '-o', str(output_absolute)],
                             check=True, capture_output=not args.verbose)

                # Make executable
                if sys.platform != 'win32':
                    os.chmod(output_absolute, 0o755)

                # Cleanup
                if not args.keep_ir:
                    verbose("Cleaning up intermediate files...", args.verbose)
                    ir_file.unlink(missing_ok=True)
                    obj_file.unlink(missing_ok=True)
                else:
                    info(f"Kept LLVM IR file: {ir_file}")
                    info(f"Kept object file: {obj_file}")

                print(f"{Color.GREEN}✓ Compilation successful!{Color.NC}")
                print(f"  Executable: {Color.BLUE}{output_absolute}{Color.NC}")

                if args.run:
                    print()
                    info("Running executable...")
                    print("--- Output ---")
                    subprocess.run([str(output_absolute)])
                    print("--- End Output ---")

            except subprocess.CalledProcessError:
                error("Failed to compile with llc/clang")
        else:
            print(f"{Color.YELLOW}Note:{Color.NC} LLVM IR file generated at: {ir_file}")
            print("Install llc and clang to compile to native executable:")
            print(f"  llc -filetype=obj {ir_file} -o {obj_file}")
            print(f"  clang {obj_file} -o {output_absolute}")
            sys.exit(1)

if __name__ == '__main__':
    main()

