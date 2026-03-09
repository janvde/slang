package nl.endevelopment

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.parser.ASTBuilder
import nl.endevelopment.parser.SlangLexer
import nl.endevelopment.parser.SlangParser
import nl.endevelopment.semantic.Type
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

    @Test
    fun testClassDefinitionParses() {
        val source = """
            class Point(var x: Int, let y: Int) {
                fn sum(): Int {
                    return this.x + this.y;
                }
            }
        """.trimIndent()

        val ast = parse(source)
        assertEquals(1, ast.statements.size)
        val stmt = ast.statements[0]
        assertIs<Stmt.ClassDef>(stmt)
        assertEquals("Point", stmt.name)
        assertEquals(2, stmt.fields.size)
        assertEquals(1, stmt.methods.size)
        assertTrue(stmt.fields[0].mutable)
        assertTrue(!stmt.fields[1].mutable)
        assertEquals("sum", stmt.methods[0].name)
    }

    @Test
    fun testMemberAssignAndCallStatementsParse() {
        val source = """
            class Counter(var value: Int) {
                fn inc(delta: Int): Void {
                    this.value = this.value + delta;
                    return;
                }
            }
            let c: Counter = Counter(1);
            c.value = 2;
            c.inc(3);
            print(c.value);
        """.trimIndent()

        val ast = parse(source)
        assertEquals(5, ast.statements.size)
        assertIs<Stmt.ClassDef>(ast.statements[0])
        assertIs<Stmt.MemberAssignStmt>(ast.statements[2])
        val callStmt = ast.statements[3]
        assertIs<Stmt.ExprStmt>(callStmt)
        assertIs<Expr.MemberCall>(callStmt.expr)
        val printStmt = ast.statements[4]
        assertIs<Stmt.PrintStmt>(printStmt)
        assertIs<Expr.MemberAccess>(printStmt.expr)
    }

    @Test
    fun testTypedListAnnotationParses() {
        val source = """
            let words: List[String] = ["a", "b"];
            let nums: List = [1, 2, 3];
        """.trimIndent()

        val ast = parse(source)
        assertEquals(2, ast.statements.size)

        val wordsStmt = ast.statements[0]
        assertIs<Stmt.LetStmt>(wordsStmt)
        assertEquals(Type.LIST(Type.STRING), wordsStmt.type)

        val numsStmt = ast.statements[1]
        assertIs<Stmt.LetStmt>(numsStmt)
        assertEquals(Type.LIST(Type.INT), numsStmt.type)
    }
}
