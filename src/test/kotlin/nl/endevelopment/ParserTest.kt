package nl.endevelopment

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.parser.ASTBuilder
import nl.endevelopment.parser.SlangLexer
import nl.endevelopment.parser.SlangParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {

    private fun parse(source: String): Program {
        val lexer = SlangLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = SlangParser(tokens)
        val tree = parser.program()

        val astBuilder = ASTBuilder()
        return astBuilder.visit(tree) as Program
    }

    @Test
    fun testSimpleLetStatement() {
        val ast = parse("let x: Int = 5;")
        assertEquals(1, ast.statements.size)
        val stmt = ast.statements[0]
        assertIs<Stmt.LetStmt>(stmt)
        assertEquals("x", stmt.name)
        assertEquals(1, stmt.location.line)
    }

    @Test
    fun testPrintStatement() {
        val ast = parse("print(42);")
        assertEquals(1, ast.statements.size)
        val stmt = ast.statements[0]
        assertIs<Stmt.PrintStmt>(stmt)
        assertIs<Expr.Number>(stmt.expr)
    }

    @Test
    fun testIfStatementWithBoolCondition() {
        val source = """
            let x: Int = 10;
            if (x > 0) {
                print(x);
            } else {
                print(0);
            }
        """.trimIndent()

        val ast = parse(source)
        assertEquals(2, ast.statements.size)
        val ifStmt = ast.statements[1]
        assertIs<Stmt.IfStmt>(ifStmt)
        assertIs<Expr.BinaryOp>(ifStmt.condition)
        assertNotNull(ifStmt.elseBranch)
        assertEquals(1, ifStmt.thenBranch.size)
    }

    @Test
    fun testForStatementParsesIntoForStmt() {
        val source = """
            for (var i: Int = 0; i < 3; i = i + 1) {
                print(i);
            }
        """.trimIndent()

        val ast = parse(source)
        assertEquals(1, ast.statements.size)
        val stmt = ast.statements[0]
        assertIs<Stmt.ForStmt>(stmt)
        assertIs<Stmt.VarStmt>(stmt.init)
        assertIs<Expr.BinaryOp>(stmt.condition)
        assertIs<Stmt.AssignStmt>(stmt.update)
        assertEquals(1, stmt.body.size)
    }

    @Test
    fun testBreakAndContinueStatements() {
        val source = """
            while (true) {
                continue;
                break;
            }
        """.trimIndent()

        val ast = parse(source)
        val whileStmt = ast.statements[0]
        assertIs<Stmt.WhileStmt>(whileStmt)
        assertEquals(2, whileStmt.body.size)
        assertIs<Stmt.ContinueStmt>(whileStmt.body[0])
        assertIs<Stmt.BreakStmt>(whileStmt.body[1])
    }

    @Test
    fun testSourceLocationCaptured() {
        val source = """
            let a: Int = 1;
            let b: Int = a + 2;
        """.trimIndent()

        val ast = parse(source)
        val secondStmt = ast.statements[1]
        assertIs<Stmt.LetStmt>(secondStmt)
        assertEquals(2, secondStmt.location.line)
        assertTrue(secondStmt.location.column > 0)
    }
}
