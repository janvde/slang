package nl.endevelopment

import nl.endevelopment.ast.Program
import nl.endevelopment.interpreter.Interpreter
import nl.endevelopment.parser.ASTBuilder
import nl.endevelopment.parser.SlangLexer
import nl.endevelopment.parser.SlangParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.*

class InterpreterTest {

    private fun parseToAST(source: String): Program {
        val lexer = SlangLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = SlangParser(tokens)
        val tree = parser.program()
        val astBuilder = ASTBuilder()
        return astBuilder.visit(tree) as Program
    }

    private fun captureInterpreterOutput(source: String): String {
        val ast = parseToAST(source)
        val interpreter = Interpreter()

        val outputStream = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(outputStream))
        try {
            interpreter.interpret(ast)
            return outputStream.toString().trim()
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun testSimpleVariableAndPrint() {
        val source = """
            let x: Int = 42;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("42", output)
    }

    @Test
    fun testArithmeticAddition() {
        val source = """
            let x: Int = 5 + 3;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("8", output)
    }

    @Test
    fun testArithmeticSubtraction() {
        val source = """
            let x: Int = 10 - 3;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("7", output)
    }

    @Test
    fun testArithmeticMultiplication() {
        val source = """
            let x: Int = 6 * 7;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("42", output)
    }

    @Test
    fun testArithmeticDivision() {
        val source = """
            let x: Int = 20 / 4;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("5", output)
    }

    @Test
    fun testComplexExpression() {
        val source = """
            let x: Int = 10;
            let y: Int = 5;
            let z: Int = x + y * 2;
            print(z);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("20", output) // 10 + (5*2) = 10 + 10 = 20
    }

    @Test
    fun testOperatorPrecedence() {
        val source = """
            let x: Int = 2 + 3 * 4;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("14", output) // 2 + (3*4) = 2 + 12 = 14
    }

    @Test
    fun testParentheses() {
        val source = """
            let x: Int = (2 + 3) * 4;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("20", output) // (2+3)*4 = 5*4 = 20
    }

    @Test
    fun testVariableReference() {
        val source = """
            let x: Int = 10;
            let y: Int = x;
            print(y);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("10", output)
    }

    @Test
    fun testMultipleVariables() {
        val source = """
            let a: Int = 1;
            let b: Int = 2;
            let c: Int = 3;
            let sum: Int = a + b + c;
            print(sum);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("6", output)
    }

    @Test
    fun testIfStatementTrueBranch() {
        val source = """
            let x: Int = 10;
            if (x) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output) // non-zero is truthy
    }

    @Test
    fun testIfStatementFalseBranch() {
        val source = """
            let x: Int = 0;
            if (x) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0", output) // zero is falsy
    }

    @Test
    fun testIfWithoutElse() {
        val source = """
            let x: Int = 10;
            if (x) {
                print(99);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("99", output)
    }

    @Test
    fun testIfWithoutElseNotExecuted() {
        val source = """
            let x: Int = 0;
            if (x) {
                print(99);
            }
            print(1);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output) // if not executed because x is 0 (falsy)
    }

    @Test
    fun testMultiplePrints() {
        val source = """
            print(1);
            print(2);
            print(3);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        val lines = output.split("\n")
        assertEquals(3, lines.size)
        assertEquals("1", lines[0])
        assertEquals("2", lines[1])
        assertEquals("3", lines[2])
    }

    @Test
    fun testNestedArithmetic() {
        val source = """
            let x: Int = ((10 + 5) * 2) / 3;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("10", output) // ((10+5)*2)/3 = (15*2)/3 = 30/3 = 10
    }

    @Test
    fun testIfWithExpression() {
        val source = """
            let x: Int = 10;
            let y: Int = x - 10;
            if (y) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0", output) // y = 0, which is falsy
    }

    @Test
    fun testVariableUpdateWithExpression() {
        val source = """
            let x: Int = 10;
            let y: Int = x + 5;
            let z: Int = y * 2;
            print(z);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("30", output) // (10+5)*2 = 15*2 = 30
    }

    @Test
    fun testPrintLiteral() {
        val source = "print(42);"

        val output = captureInterpreterOutput(source)
        assertEquals("42", output)
    }

    @Test
    fun testZeroValue() {
        val source = """
            let x: Int = 0;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0", output)
    }

    @Test
    fun testNegativeResult() {
        val source = """
            let x: Int = 5 - 10;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("-5", output)
    }

    @Test
    fun testComplexIfCondition() {
        val source = """
            let x: Int = 5;
            let y: Int = 3;
            let sum: Int = x + y;
            if (sum) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output) // 5+3=8, which is truthy
    }

    @Test
    fun testEmptyProgram() {
        val source = ""

        val output = captureInterpreterOutput(source)
        assertEquals("", output)
    }
}
