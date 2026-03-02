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
}
