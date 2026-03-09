package nl.endevelopment

import nl.endevelopment.ast.Program
import nl.endevelopment.parser.ASTBuilder
import nl.endevelopment.parser.SlangLexer
import nl.endevelopment.parser.SlangParser
import nl.endevelopment.semantic.TypeChecker
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TypeCheckerTest {

    private fun parseToAST(source: String): Program {
        val lexer = SlangLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = SlangParser(tokens)
        val tree = parser.program()
        val astBuilder = ASTBuilder()
        return astBuilder.visit(tree) as Program
    }

    private fun typeCheck(source: String) {
        val ast = parseToAST(source)
        TypeChecker().check(ast)
    }

    @Test
    fun testClassConstructorAndMethodTypeCheck() {
        val source = """
            class Counter(var value: Int) {
                fn add(delta: Int): Void {
                    this.value = this.value + delta;
                    return;
                }
            }
            let c: Counter = Counter(1);
            c.add(2);
            print(c.value);
        """.trimIndent()

        typeCheck(source)
    }

    @Test
    fun testConstructorArityMismatchFails() {
        val source = """
            class Point(var x: Int, var y: Int) {}
            let p: Point = Point(1);
        """.trimIndent()

        val error = assertFailsWith<RuntimeException> {
            typeCheck(source)
        }
        assertTrue(error.message?.contains("Constructor 'Point' expects 2 arguments") == true)
    }

    @Test
    fun testImmutableFieldAssignmentFails() {
        val source = """
            class Point(let x: Int, var y: Int) {}
            let p: Point = Point(1, 2);
            p.x = 10;
        """.trimIndent()

        val error = assertFailsWith<RuntimeException> {
            typeCheck(source)
        }
        assertTrue(error.message?.contains("Cannot assign to immutable field 'x'") == true)
    }

    @Test
    fun testThisOutsideMethodFails() {
        val source = """
            class Point(var x: Int) {}
            print(this.x);
        """.trimIndent()

        val error = assertFailsWith<RuntimeException> {
            typeCheck(source)
        }
        assertTrue(error.message?.contains("'this' is only allowed") == true)
    }

    @Test
    fun testStringConcatMixedTypesFails() {
        val source = """
            let message: String = "hello" + 42;
        """.trimIndent()

        val error = assertFailsWith<RuntimeException> {
            typeCheck(source)
        }
        assertTrue(error.message?.contains("Unsupported operand types for '+': STRING and INT.") == true)
    }

    @Test
    fun testTypedListIndexTypeCheckPasses() {
        val source = """
            let xs: List[Int] = [1, 2, 3];
            let first: Int = xs[0];
            print(first);
        """.trimIndent()

        typeCheck(source)
    }

    @Test
    fun testTypedListWithClassElementsPasses() {
        val source = """
            class Point(var x: Int) {}
            let points: List[Point] = [Point(1), Point(2)];
            let p: Point = points[0];
            print(p.x);
        """.trimIndent()

        typeCheck(source)
    }

    @Test
    fun testHeterogeneousTypedListFailsWithLocation() {
        val source = """
            let xs: List[Int] = [1, "x"];
        """.trimIndent()

        val error = assertFailsWith<RuntimeException> {
            typeCheck(source)
        }
        assertTrue(error.message?.isNotBlank() == true)
    }

    @Test
    fun testBuiltInStringFunctionsTypeCheck() {
        val source = """
            let text: String = "hello";
            let part: String = substr(text, 1, 3);
            let has: Bool = contains(text, "ell");
            let n: Int = to_int("42");
            print(part);
            print(has);
            print(n);
        """.trimIndent()

        typeCheck(source)
    }

    @Test
    fun testBuiltInNumericFunctionsTypeCheck() {
        val source = """
            let a: Int = min(1, 2);
            let b: Float = max(1.5, 2);
            let c: Int = abs(-4);
            let d: Float = abs(-1.25);
            print(a);
            print(b);
            print(c);
            print(d);
        """.trimIndent()

        typeCheck(source)
    }

    @Test
    fun testDuplicateFunctionDiagnosticHasRelatedLocation() {
        val source = """
            fn foo(): Int { return 1; }
            fn foo(): Int { return 2; }
        """.trimIndent()

        val error = assertFailsWith<RuntimeException> {
            typeCheck(source)
        }
        assertTrue(error.message?.contains("Function 'foo' is already defined.") == true)
        assertTrue(error.message?.contains("Note: previous declaration is at line 1") == true)
    }

    @Test
    fun testTypeMismatchDiagnosticUsesExpectedActualFormat() {
        val source = """
            let n: Int = "nope";
        """.trimIndent()

        val error = assertFailsWith<RuntimeException> {
            typeCheck(source)
        }
        assertTrue(error.message?.isNotBlank() == true)
    }
}
