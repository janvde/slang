package nl.endevelopment

import codegen.CodeGenerator
import nl.endevelopment.ast.Program
import nl.endevelopment.parser.ASTBuilder
import nl.endevelopment.parser.SlangLexer
import nl.endevelopment.parser.SlangParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import kotlin.test.*

class CodeGeneratorTest {

    private fun parseToAST(source: String): Program {
        val lexer = SlangLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = SlangParser(tokens)
        val tree = parser.program()
        val astBuilder = ASTBuilder()
        return astBuilder.visit(tree) as Program
    }

    private fun generateIR(source: String): String {
        val ast = parseToAST(source)
        val codeGenerator = CodeGenerator()
        try {
            codeGenerator.generate(ast)
            val tempFile = File.createTempFile("test_", ".ll")
            codeGenerator.writeIRToFile(tempFile.absolutePath)
            val ir = tempFile.readText()
            tempFile.delete()
            return ir
        } finally {
            codeGenerator.dispose()
        }
    }

    @Test
    fun testSimpleVariableDeclaration() {
        val source = "let x: Int = 42;"
        val ir = generateIR(source)

        assertNotNull(ir)
        assertTrue(ir.contains("@main()"))
        assertTrue(ir.contains("alloca i32"))
        assertTrue(ir.contains("store i32 42"))
    }

    @Test
    fun testVariableWithArithmetic() {
        val source = "let x: Int = 5 + 3;"
        val ir = generateIR(source)

        assertNotNull(ir)
        // Either constant folded to 8, or has add instruction
        assertTrue(ir.contains("store i32 8") || ir.contains("add i32"))
    }

    @Test
    fun testMultiplication() {
        val source = "let x: Int = 4 * 7;"
        val ir = generateIR(source)

        assertNotNull(ir)
        // Either constant folded to 28, or has mul instruction
        assertTrue(ir.contains("store i32 28") || ir.contains("mul i32"))
    }

    @Test
    fun testDivision() {
        val source = "let x: Int = 10 / 2;"
        val ir = generateIR(source)

        assertNotNull(ir)
        // Either constant folded to 5, or has sdiv instruction
        assertTrue(ir.contains("store i32 5") || ir.contains("sdiv i32"))
    }

    @Test
    fun testComplexExpression() {
        val source = """
            let x: Int = 10;
            let y: Int = 5;
            let z: Int = x + y * 2;
        """.trimIndent()
        val ir = generateIR(source)

        assertNotNull(ir)
        // Since we're using variables, not constants, these operations will be present
        assertTrue(ir.contains("mul i32") || ir.contains("store i32 20")) // y * 2 = 10
        assertTrue(ir.contains("add i32") || ir.contains("store i32 20")) // x + 10 = 20
        assertTrue(ir.contains("%x = alloca i32"))
        assertTrue(ir.contains("%y = alloca i32"))
        assertTrue(ir.contains("%z = alloca i32"))
    }

    @Test
    fun testPrintStatement() {
        val source = """
            let x: Int = 42;
            print(x);
        """.trimIndent()
        val ir = generateIR(source)

        assertNotNull(ir)
        assertTrue(ir.contains("@printf"))
        assertTrue(ir.contains("@fmt_int"))
        assertTrue(ir.contains("call i32"))
    }

    @Test
    fun testPrintLiteral() {
        val source = "print(123);"
        val ir = generateIR(source)

        assertNotNull(ir)
        assertTrue(ir.contains("@printf"))
        assertTrue(ir.contains("123"))
    }

    @Test
    fun testIfStatement() {
        val source = """
            let x: Int = 5;
            if (x > 0) {
                print(x);
            } else {
                print(0);
            }
        """.trimIndent()
        val ir = generateIR(source)

        assertNotNull(ir)
        assertTrue(ir.contains("icmp"))
        assertTrue(ir.contains("br i1"))
        assertTrue(ir.contains("label %then"))
        assertTrue(ir.contains("label %else"))
        assertTrue(ir.contains("label %merge"))
    }

    @Test
    fun testIfWithoutElse() {
        val source = """
            let x: Int = 5;
            if (x > 0) {
                print(x);
            }
        """.trimIndent()
        val ir = generateIR(source)

        assertNotNull(ir)
        assertTrue(ir.contains("icmp"))
        assertTrue(ir.contains("br i1"))
    }

    @Test
    fun testMultipleVariables() {
        val source = """
            let a: Int = 1;
            let b: Int = 2;
            let c: Int = 3;
            let d: Int = a + b + c;
            print(d);
        """.trimIndent()
        val ir = generateIR(source)

        assertNotNull(ir)
        assertTrue(ir.contains("%a = alloca i32"))
        assertTrue(ir.contains("%b = alloca i32"))
        assertTrue(ir.contains("%c = alloca i32"))
        assertTrue(ir.contains("%d = alloca i32"))
        assertTrue(ir.contains("add i32"))
    }

    @Test
    fun testNestedArithmetic() {
        val source = "let x: Int = (5 + 3) * (10 - 2);"
        val ir = generateIR(source)

        assertNotNull(ir)
        // Either constant folded to 64 (8*8), or has arithmetic instructions
        assertTrue(
            ir.contains("store i32 64") ||
            (ir.contains("add i32") && ir.contains("sub i32") && ir.contains("mul i32"))
        )
    }

    @Test
    fun testIRContainsMainFunction() {
        val source = "let x: Int = 1;"
        val ir = generateIR(source)

        assertTrue(ir.contains("define i32 @main()"))
        assertTrue(ir.contains("ret i32 0"))
    }

    @Test
    fun testIRContainsPrintfDeclaration() {
        val source = "print(42);"
        val ir = generateIR(source)

        assertTrue(ir.contains("declare i32 @printf"))
    }

    @Test
    fun testFormatStringsGenerated() {
        val source = "print(1);"
        val ir = generateIR(source)

        assertTrue(ir.contains("@fmt_int"))
        assertTrue(ir.contains("@fmt_str"))
        assertTrue(ir.contains("@fmt_float"))
        assertTrue(ir.contains("@fmt_bool"))
    }

    @Test
    fun testConstantFolding() {
        // The LLVM optimizer might fold constants
        val source = "let x: Int = 5 + 10;"
        val ir = generateIR(source)

        assertNotNull(ir)
        // Either stores the folded constant 15, or has the add instruction
        assertTrue(ir.contains("store i32 15") || ir.contains("add i32"))
    }

    @Test
    fun testVariableLoad() {
        val source = """
            let x: Int = 10;
            let y: Int = x;
        """.trimIndent()
        val ir = generateIR(source)

        assertNotNull(ir)
        assertTrue(ir.contains("load i32"))
        assertTrue(ir.contains("%x = alloca i32"))
        assertTrue(ir.contains("%y = alloca i32"))
    }

    @Test
    fun testLenOnStringUsesStrlen() {
        val source = """
            let message: String = "hello";
            let n: Int = len(message);
            print(n);
        """.trimIndent()
        val ir = generateIR(source)

        assertTrue(ir.contains("declare i64 @strlen"))
        assertTrue(ir.contains("call i64 @strlen"))
    }

    @Test
    fun testForLoopGeneratesBlocks() {
        val source = """
            for (var i: Int = 0; i < 3; i = i + 1) {
                print(i);
            }
        """.trimIndent()
        val ir = generateIR(source)

        assertTrue(ir.contains("label %for_cond"))
        assertTrue(ir.contains("label %for_body"))
        assertTrue(ir.contains("label %for_update"))
        assertTrue(ir.contains("label %for_after"))
    }

    @Test
    fun testBreakContinueInForLoopGenerateBranches() {
        val source = """
            for (var i: Int = 0; i < 5; i = i + 1) {
                if (i == 2) { continue; }
                if (i == 4) { break; }
                print(i);
            }
        """.trimIndent()
        val ir = generateIR(source)

        assertTrue(ir.contains("for_update"))
        assertTrue(ir.contains("for_after"))
        assertTrue(ir.contains("br label"))
    }

    @Test
    fun testNonBoolIfConditionFails() {
        val source = """
            let x: Int = 1;
            if (x) {
                print(1);
            }
        """.trimIndent()

        val error = assertFailsWith<Exception> {
            generateIR(source)
        }
        assertTrue(error.message?.contains("If condition must be Bool") == true)
    }

    @Test
    fun testClassConstructionAndFieldAccessGeneratesStructAndMalloc() {
        val source = """
            class Point(var x: Int, var y: Int) {}
            let p: Point = Point(1, 2);
            print(p.x);
        """.trimIndent()

        val ir = generateIR(source)

        assertTrue(ir.contains("%Class_Point = type { i32, i32 }"))
        assertTrue(ir.contains("declare ptr @malloc(i64)"))
        assertTrue(ir.contains("call ptr @malloc"))
        assertTrue(ir.contains("getelementptr inbounds %Class_Point"))
    }

    @Test
    fun testClassMethodLoweredToMangledFunction() {
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

        val ir = generateIR(source)
        assertTrue(ir.contains("@Counter__add"))
        assertTrue(ir.contains("call void @Counter__add"))
    }

    @Test
    fun testStringConcatGeneratesRuntimeCalls() {
        val source = """print("a" + "b");"""
        val ir = generateIR(source)

        assertTrue(ir.contains("declare i64 @strlen"))
        assertTrue(ir.contains("declare ptr @malloc(i64)"))
        assertTrue(ir.contains("declare ptr @strcpy(ptr, ptr)"))
        assertTrue(ir.contains("declare ptr @strcat(ptr, ptr)"))
        assertTrue(ir.contains("call ptr @strcat"))
    }

    @Test
    fun testTypedStringListUsesOpaqueListStorage() {
        val source = """
            let words: List[String] = ["a", "b"];
            print(words[1]);
        """.trimIndent()
        val ir = generateIR(source)

        assertTrue(ir.contains("%List = type { i64, ptr }"))
        assertTrue(ir.contains("list_data_ptr"))
    }

    @Test
    fun testBuiltInStringHelpersGenerateCalls() {
        val source = """
            let s: String = "slang";
            print(substr(s, 1, 3));
            print(contains(s, "la"));
            print(to_int("7"));
        """.trimIndent()
        val ir = generateIR(source)

        assertTrue(ir.contains("declare ptr @strncpy(ptr, ptr, i64)"))
        assertTrue(ir.contains("declare ptr @strstr(ptr, ptr)"))
        assertTrue(ir.contains("declare i32 @atoi(ptr)"))
    }
}
