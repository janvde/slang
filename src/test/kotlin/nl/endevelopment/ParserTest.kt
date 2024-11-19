package nl.endevelopment

import nl.endevelopment.ast.Program
import nl.endevelopment.parser.ASTBuilder
import nl.endevelopment.parser.SlangLexer
import nl.endevelopment.parser.SlangParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun testSimpleLetStatement() {
        val source = "let x: Int = 5;"
        val lexer = SlangLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = SlangParser(tokens)
        val tree = parser.program()

        val astBuilder = ASTBuilder()
        val ast = astBuilder.visit(tree) as Program

        assertNotNull(ast)
        assertTrue(ast.statements.size == 1)
        println(ast)
    }

    @Test
    fun testPrintStatement() {
        val source = "print(x);"
        val lexer = SlangLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = SlangParser(tokens)
        val tree = parser.program()

        val astBuilder = ASTBuilder()
        val ast = astBuilder.visit(tree) as Program

        assertNotNull(ast)
        assertTrue(ast.statements.size == 1)
        println(ast)
    }

    @Test
    fun testIfStatement() {
        val source = """
            if (x > 0) {
                print(x);
            } else {
                print(-x);
            }
        """.trimIndent()
        val lexer = SlangLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = SlangParser(tokens)
        val tree = parser.program()

        val astBuilder = ASTBuilder()
        val ast = astBuilder.visit(tree) as Program

        assertNotNull(ast)
        assertTrue(ast.statements.size == 1)
        println(ast)
    }
}