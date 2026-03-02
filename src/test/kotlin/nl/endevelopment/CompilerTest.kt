package nl.endevelopment

import nl.endevelopment.compiler.Compiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.*

class CompilerTest {

    private fun captureOutput(block: () -> Unit): String {
        val outputStream = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(outputStream))
        try {
            block()
            return outputStream.toString()
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun testCompileSimpleProgram() {
        val source = """
            let x: Int = 42;
            print(x);
        """.trimIndent()

        val tempSource = File.createTempFile("test_", ".slang")
        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            tempSource.writeText(source)

            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("Executing with interpreter..."))
            assertTrue(output.contains("42"))
            assertTrue(output.contains("Code Generation Complete"))

            assertTrue(tempOutput.exists())
            val ir = tempOutput.readText()
            assertTrue(ir.contains("@main()"))
            assertTrue(ir.contains("@printf"))
        } finally {
            tempSource.delete()
            tempOutput.delete()
        }
    }

    @Test
    fun testCompileWithArithmetic() {
        val source = """
            let a: Int = 10;
            let b: Int = 5;
            let c: Int = a + b;
            print(c);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("15"))
            assertTrue(tempOutput.exists())

            val ir = tempOutput.readText()
            assertTrue(ir.contains("add i32"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testCompileIfStatement() {
        val source = """
            let x: Int = 10;
            if (x > 0) {
                print(100);
            } else {
                print(0);
            }
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("100"))

            val ir = tempOutput.readText()
            assertTrue(ir.contains("icmp"))
            assertTrue(ir.contains("br i1"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testCompileComplexExpression() {
        val source = """
            let x: Int = 5;
            let y: Int = 10;
            let z: Int = x * 2 + y / 2;
            print(z);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("15")) // 5*2 + 10/2 = 10 + 5 = 15

            val ir = tempOutput.readText()
            assertTrue(ir.contains("mul i32"))
            assertTrue(ir.contains("sdiv i32"))
            assertTrue(ir.contains("add i32"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testInterpreterExecution() {
        val source = """
            let x: Int = 7;
            let y: Int = 3;
            print(x + y);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            // Interpreter should print 10
            assertTrue(output.contains("10"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testMultiplePrintStatements() {
        val source = """
            let a: Int = 1;
            print(a);
            let b: Int = 2;
            print(b);
            let c: Int = 3;
            print(c);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("1"))
            assertTrue(output.contains("2"))
            assertTrue(output.contains("3"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testNestedIfStatements() {
        val source = """
            let x: Int = 10;
            if (x > 0) {
                let y: Int = x - 2;
                if (y > 0) {
                    print(1);
                } else {
                    print(2);
                }
            } else {
                print(3);
            }
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("1"))

            val ir = tempOutput.readText()
            assertTrue(ir.contains("icmp"))
            assertTrue(ir.contains("br i1"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testOutputFileCreated() {
        val source = "let x: Int = 1;"
        val tempOutput = File.createTempFile("test_", ".ll")
        tempOutput.delete() // Delete to ensure compiler creates it

        try {
            val compiler = Compiler()
            compiler.compile(source, tempOutput.absolutePath)

            assertTrue(tempOutput.exists())
            assertTrue(tempOutput.length() > 0)
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testValidLLVMIRFormat() {
        val source = """
            let x: Int = 42;
            print(x);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            compiler.compile(source, tempOutput.absolutePath)

            val ir = tempOutput.readText()

            // Check for basic LLVM IR structure
            assertTrue(ir.contains("ModuleID"))
            assertTrue(ir.contains("define i32 @main()"))
            assertTrue(ir.contains("ret i32 0"))
            assertTrue(ir.contains("}"))

            // Check for proper LLVM declarations
            assertTrue(ir.contains("declare i32 @printf"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testSubtraction() {
        val source = """
            let x: Int = 10 - 3;
            print(x);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("7"))

            val ir = tempOutput.readText()
            // Either constant folded to 7, or has sub instruction
            assertTrue(ir.contains("store i32 7") || ir.contains("sub i32"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testOperatorPrecedence() {
        val source = """
            let x: Int = 2 + 3 * 4;
            print(x);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            // Should be 14 (3*4=12, then 2+12=14), not 20 (2+3=5, then 5*4=20)
            assertTrue(output.contains("14"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testEmptyProgram() {
        val source = ""

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            compiler.compile(source, tempOutput.absolutePath)

            assertTrue(tempOutput.exists())
            val ir = tempOutput.readText()
            assertTrue(ir.contains("define i32 @main()"))
            assertTrue(ir.contains("ret i32 0"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testParenthesesExpression() {
        val source = """
            let x: Int = (2 + 3) * 4;
            print(x);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("20")) // (2+3)*4 = 5*4 = 20
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testLenStringCompilation() {
        val source = """
            let s: String = "abc";
            print(len(s));
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("3"))
            val ir = tempOutput.readText()
            assertTrue(ir.contains("declare i64 @strlen"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testBoolOnlyConditionErrorHasLocation() {
        val source = """
            let x: Int = 1;
            if (x) {
                print(1);
            }
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("Error at line"))
            assertTrue(output.contains("If condition must be Bool"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testForLoopWithBreakAndContinue() {
        val source = """
            for (var i: Int = 0; i < 6; i = i + 1) {
                if (i == 2) { continue; }
                if (i == 4) { break; }
                print(i);
            }
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("0"))
            assertTrue(output.contains("1"))
            assertTrue(output.contains("3"))
            val ir = tempOutput.readText()
            assertTrue(ir.contains("for_cond"))
            assertTrue(ir.contains("for_update"))
            assertTrue(ir.contains("for_after"))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testBreakOutsideLoopTypeErrorHasLocation() {
        val source = "break;"
        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }
            assertTrue(output.contains("Error at line"))
            assertTrue(output.contains("'break' is only allowed inside a loop."))
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testClassProgramCompilesAndRuns() {
        val source = """
            class Counter(var value: Int) {
                fn add(delta: Int): Void {
                    this.value = this.value + delta;
                    return;
                }
            }
            let c: Counter = Counter(1);
            c.add(4);
            print(c.value);
        """.trimIndent()

        val tempOutput = File.createTempFile("test_", ".ll")
        try {
            val compiler = Compiler()
            val output = captureOutput {
                compiler.compile(source, tempOutput.absolutePath)
            }

            assertTrue(output.contains("5"))
            assertTrue(tempOutput.exists())
            val ir = tempOutput.readText()
            assertTrue(ir.contains("@Counter__add"))
            assertTrue(ir.contains("%Class_Counter = type { i32 }"))
        } finally {
            tempOutput.delete()
        }
    }
}
