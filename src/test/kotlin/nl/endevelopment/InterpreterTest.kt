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
            if (x > 0) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    @Test
    fun testIfStatementFalseBranch() {
        val source = """
            let x: Int = 0;
            if (x > 0) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0", output)
    }

    @Test
    fun testIfWithoutElse() {
        val source = """
            let x: Int = 10;
            if (x > 0) {
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
            if (x > 0) {
                print(99);
            }
            print(1);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
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
            if (y > 0) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0", output)
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
            if (sum > 0) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    @Test
    fun testEmptyProgram() {
        val source = ""

        val output = captureInterpreterOutput(source)
        assertEquals("", output)
    }

    @Test
    fun testModuloOperation() {
        val source = """
            let x: Int = 10 % 3;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    @Test
    fun testModuloWithExpressions() {
        val source = """
            let a: Int = 17;
            let b: Int = 5;
            let result: Int = a % b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("2", output)
    }

    @Test
    fun testBooleanLiteralTrue() {
        val source = """
            let flag: Bool = true;
            print(flag);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("true", output)
    }

    @Test
    fun testBooleanLiteralFalse() {
        val source = """
            let flag: Bool = false;
            print(flag);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("false", output)
    }

    @Test
    fun testBooleanInIfCondition() {
        val source = """
            let isReady: Bool = true;
            if (isReady) {
                print(100);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("100", output)
    }

    @Test
    fun testFloatLiteral() {
        val source = """
            let pi: Float = 3.14;
            print(pi);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("3.14", output)
    }

    @Test
    fun testFloatArithmetic() {
        val source = """
            let a: Float = 2.5;
            let b: Float = 1.5;
            let result: Float = a + b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("4.0", output)
    }

    @Test
    fun testIntFloatMixedAddition() {
        val source = """
            let a: Int = 5;
            let b: Float = 2.5;
            let result: Float = a + b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("7.5", output)
    }

    @Test
    fun testIntFloatMixedComparison() {
        val source = """
            let a: Int = 5;
            let b: Float = 3.14;
            if (a > b) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    @Test
    fun testStringLiteral() {
        val source = """
            let message: String = "Hello";
            print(message);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("Hello", output)
    }

    @Test
    fun testStringWithEscapeNewline() {
        val source = """
            let text: String = "Line1\nLine2";
            print(text);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("Line1\nLine2", output)
    }

    @Test
    fun testStringWithEscapeTab() {
        val source = """
            let text: String = "Col1\tCol2";
            print(text);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("Col1\tCol2", output)
    }

    @Test
    fun testStringConcatenation() {
        val source = """
            let a: String = "Hello";
            let b: String = "World";
            let result: String = a + b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("HelloWorld", output)
    }

    @Test
    fun testVarDeclaration() {
        val source = """
            var x: Int = 5;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("5", output)
    }

    @Test
    fun testVarReassignment() {
        val source = """
            var x: Int = 5;
            x = 10;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("10", output)
    }

    @Test
    fun testVarMultipleReassignments() {
        val source = """
            var x: Int = 1;
            x = 2;
            x = 3;
            x = 4;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("4", output)
    }

    @Test
    fun testLetImmutability() {
        val source = """
            let x: Int = 5;
            x = 10;
            print(x);
        """.trimIndent()

        val ast = parseToAST(source)
        val interpreter = Interpreter()

        val outputStream = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(outputStream))
        try {
            interpreter.interpret(ast)
            val output = outputStream.toString()
            assertTrue(output.contains("immutable"), "Expected error about immutable variable, got: $output")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun testVarReassignmentWithExpression() {
        val source = """
            var x: Int = 5;
            var y: Int = 3;
            x = y + 2;
            print(x);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("5", output)
    }

    // ============= Phase 3: Logical Operators Tests =============

    @Test
    fun testNotOperatorTrue() {
        val source = """
            let flag: Bool = true;
            let result: Bool = !flag;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("false", output)
    }

    @Test
    fun testNotOperatorFalse() {
        val source = """
            let flag: Bool = false;
            let result: Bool = !flag;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("true", output)
    }

    @Test
    fun testDoubleNotOperator() {
        val source = """
            let flag: Bool = true;
            let result: Bool = !!flag;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("true", output)
    }

    @Test
    fun testNotInIfCondition() {
        val source = """
            let flag: Bool = false;
            if (!flag) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    @Test
    fun testAndOperatorBothTrue() {
        val source = """
            let a: Bool = true;
            let b: Bool = true;
            let result: Bool = a && b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("true", output)
    }

    @Test
    fun testAndOperatorBothFalse() {
        val source = """
            let a: Bool = false;
            let b: Bool = false;
            let result: Bool = a && b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("false", output)
    }

    @Test
    fun testAndOperatorMixed() {
        val source = """
            let a: Bool = true;
            let b: Bool = false;
            let result: Bool = a && b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("false", output)
    }

    @Test
    fun testAndShortCircuit() {
        val source = """
            let a: Bool = false;
            let b: Bool = true;
            let result: Bool = a && b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("false", output)
    }

    @Test
    fun testOrOperatorBothTrue() {
        val source = """
            let a: Bool = true;
            let b: Bool = true;
            let result: Bool = a || b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("true", output)
    }

    @Test
    fun testOrOperatorBothFalse() {
        val source = """
            let a: Bool = false;
            let b: Bool = false;
            let result: Bool = a || b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("false", output)
    }

    @Test
    fun testOrOperatorMixed() {
        val source = """
            let a: Bool = true;
            let b: Bool = false;
            let result: Bool = a || b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("true", output)
    }

    @Test
    fun testOrShortCircuit() {
        val source = """
            let a: Bool = true;
            let b: Bool = false;
            let result: Bool = a || b;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("true", output)
    }

    @Test
    fun testComplexLogicalExpression() {
        val source = """
            let a: Bool = true;
            let b: Bool = false;
            let c: Bool = true;
            let result: Bool = (a && b) || c;
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("true", output)
    }

    @Test
    fun testLogicalWithComparison() {
        val source = """
            let x: Int = 10;
            let y: Int = 5;
            if (x > 5 && y < 10) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    @Test
    fun testLogicalOrWithComparison() {
        val source = """
            let x: Int = 10;
            let y: Int = 5;
            if (x < 5 || y > 0) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    @Test
    fun testNotWithComparison() {
        val source = """
            let x: Int = 10;
            if (!(x < 5)) {
                print(1);
            } else {
                print(0);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    // ============= Phase 4: While Loops Tests =============

    @Test
    fun testSimpleWhileLoop() {
        val source = """
            var i: Int = 0;
            while (i < 3) {
                print(i);
                i = i + 1;
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0\n1\n2", output)
    }

    @Test
    fun testWhileLoopCounter() {
        val source = """
            var count: Int = 1;
            while (count <= 5) {
                print(count);
                count = count + 1;
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1\n2\n3\n4\n5", output)
    }

    @Test
    fun testWhileLoopNeverExecutes() {
        val source = """
            var i: Int = 0;
            while (i > 10) {
                print(100);
            }
            print(1);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1", output)
    }

    @Test
    fun testWhileLoopWithBreakCondition() {
        val source = """
            var i: Int = 0;
            while (i < 10) {
                if (i == 3) {
                    print(99);
                } else {
                    print(i);
                }
                i = i + 1;
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0\n1\n2\n99\n4\n5\n6\n7\n8\n9", output)
    }

    @Test
    fun testNestedWhileLoops() {
        val source = """
            var i: Int = 1;
            while (i <= 2) {
                var j: Int = 1;
                while (j <= 2) {
                    print(i * 10 + j);
                    j = j + 1;
                }
                i = i + 1;
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("11\n12\n21\n22", output)
    }

    @Test
    fun testWhileLoopWithLogicalCondition() {
        val source = """
            var i: Int = 0;
            while (i < 5 && i != 2) {
                print(i);
                i = i + 1;
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0\n1", output)
    }

    @Test
    fun testWhileLoopSum() {
        val source = """
            var i: Int = 1;
            var sum: Int = 0;
            while (i <= 5) {
                sum = sum + i;
                i = i + 1;
            }
            print(sum);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("15", output)
    }

    @Test
    fun testWhileLoopFactorial() {
        val source = """
            var n: Int = 5;
            var result: Int = 1;
            while (n > 1) {
                result = result * n;
                n = n - 1;
            }
            print(result);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("120", output)
    }

    @Test
    fun testWhileLoopDivision() {
        val source = """
            var x: Int = 100;
            var count: Int = 0;
            while (x > 1) {
                x = x / 2;
                count = count + 1;
            }
            print(count);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("6", output)
    }

    @Test
    fun testWhileLoopStringMutation() {
        val source = """
            var i: Int = 0;
            while (i < 3) {
                i = i + 1;
            }
            print(i);
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("3", output)
    }

    @Test
    fun testLenString() {
        val source = """
            let text: String = "hello";
            print(len(text));
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("5", output)
    }

    @Test
    fun testLenEmptyString() {
        val source = """
            let text: String = "";
            print(len(text));
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0", output)
    }

    @Test
    fun testLenInvalidTypeProducesError() {
        val source = "print(len(42));"
        val output = captureInterpreterOutput(source)
        assertTrue(output.contains("len expects a List or String argument"))
    }

    @Test
    fun testConditionMustBeBool() {
        val source = """
            let x: Int = 1;
            if (x) {
                print(1);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertTrue(output.contains("Condition must evaluate to Bool"))
    }

    @Test
    fun testBreakInWhileLoop() {
        val source = """
            var i: Int = 0;
            while (i < 10) {
                if (i == 3) {
                    break;
                }
                print(i);
                i = i + 1;
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0\n1\n2", output)
    }

    @Test
    fun testContinueInWhileLoop() {
        val source = """
            var i: Int = 0;
            while (i < 5) {
                i = i + 1;
                if (i == 3) {
                    continue;
                }
                print(i);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("1\n2\n4\n5", output)
    }

    @Test
    fun testForLoopExecution() {
        val source = """
            for (var i: Int = 0; i < 4; i = i + 1) {
                print(i);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0\n1\n2\n3", output)
    }

    @Test
    fun testForLoopWithBreakAndContinue() {
        val source = """
            for (var i: Int = 0; i < 6; i = i + 1) {
                if (i == 2) {
                    continue;
                }
                if (i == 4) {
                    break;
                }
                print(i);
            }
        """.trimIndent()

        val output = captureInterpreterOutput(source)
        assertEquals("0\n1\n3", output)
    }
}
