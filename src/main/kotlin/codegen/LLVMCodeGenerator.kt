package codegen

import nl.endevelopment.ast.*
import nl.endevelopment.semantic.SemanticSymbolTable
import nl.endevelopment.semantic.Type
import org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage
import org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToString

class LLVMCodeGenerator {
    private var llvmCode = StringBuilder()
    private var currentRegister = 1
    private val symbolTable = SemanticSymbolTable()
    private val variableMap = mutableMapOf<String, String>() // Maps variable names to LLVM IR alloca instructions

    // Initialize LLVM IR with standard definitions
    init {
        llvmCode.append("""
            ; ModuleID = 'my_module'
            declare i32 @printf(i8*, ...)
            @print_int = constant [4 x i8] c"%d\0A\00"
            
        """.trimIndent())
    }

    fun generate(program: Program): String {
        llvmCode.append("""
            define i32 @main() {
        """.trimIndent())
        symbolTable.enterScope()

        // Allocate space for variables in the global scope
        for (stmt in program.statements) {
            generateStmt(stmt)
        }

        llvmCode.append("""
            ret i32 0
            }
        """.trimIndent())

        return llvmCode.toString()
    }

    fun writeIRToFile(filename: String, code: String) {
        java.io.File(filename).writeText(code)
    }

    private fun generateStmt(stmt: Stmt) {
        when (stmt) {
            is Stmt.LetStmt -> {
                val llvmType = mapType(stmt.type)
                val varName = "%${stmt.name}"
                llvmCode.append("    $varName = alloca $llvmType\n")
                variableMap[stmt.name] = varName
                val exprReg = generateExpr(stmt.expr)
                llvmCode.append("    store ${llvmType} $exprReg, ${llvmType}* $varName\n")
            }
            is Stmt.PrintStmt -> {
                val exprReg = generateExpr(stmt.expr)
                llvmCode.append("    call i32 (i8*, ...) @printf(i8* getelementptr ([4 x i8], [4 x i8]* @print_int, i32 0, i32 0), i32 $exprReg)\n")
            }
            is Stmt.IfStmt -> {
                val conditionReg = generateExpr(stmt.condition)
                val thenLabel = "then_${currentRegister}"
                val elseLabel = "else_${currentRegister}"
                val endLabel = "endif_${currentRegister}"
                currentRegister++

                llvmCode.append("    br i1 $conditionReg, label %$thenLabel, label %$elseLabel\n")

                // Then block
                llvmCode.append("$thenLabel:\n")
                symbolTable.enterScope()
                for (s in stmt.thenBranch) {
                    generateStmt(s)
                }
                llvmCode.append("    br label %$endLabel\n")
                symbolTable.exitScope()

                // Else block
                llvmCode.append("$elseLabel:\n")
                if (stmt.elseBranch != null) {
                    symbolTable.enterScope()
                    for (s in stmt.elseBranch) {
                        generateStmt(s)
                    }
                    symbolTable.exitScope()
                }
                llvmCode.append("    br label %$endLabel\n")

                // End if
                llvmCode.append("$endLabel:\n")
            }
        }
    }

    private fun generateExpr(expr: Expr): String {
        return when (expr) {
            is Expr.Number -> {
                expr.value.toString()
            }
            is Expr.Variable -> {
                val varName = variableMap[expr.name]
                    ?: throw Exception("Undefined variable '${expr.name}'")
                val loadedReg = "%${currentRegister++}"
                val llvmType = "i32" // Assuming INT type for simplicity
                llvmCode.append("    $loadedReg = load $llvmType, $llvmType* $varName\n")
                loadedReg
            }
            is Expr.BinaryOp -> {
                val leftReg = generateExpr(expr.left)
                val rightReg = generateExpr(expr.right)
                val resultReg = "%${currentRegister++}"
                val op = mapOperator(expr.operator)
                llvmCode.append("    $resultReg = $op i32 $leftReg, $rightReg\n")
                resultReg
            }
        }
    }

    private fun mapType(type: Type): String {
        return when (type) {
            Type.INT -> "i32"
            Type.FLOAT -> "float"
            Type.BOOL -> "i1"
            Type.STRING -> "i8*"
            Type.VOID -> "void"
        }
    }

    private fun mapOperator(op: String): String {
        return when (op) {
            "+" -> "add"
            "-" -> "sub"
            "*" -> "mul"
            "/" -> "sdiv"
            ">" -> "icmp sgt"
            "<" -> "icmp slt"
            "==" -> "icmp eq"
            else -> throw Exception("Unsupported operator '$op'")
        }
    }
}
