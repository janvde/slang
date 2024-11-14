package codegen

import nl.endevelopment.ast.*
import nl.endevelopment.semantic.SemanticSymbolTable
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

class CodeGenerator(private val semanticSymbolTable: SemanticSymbolTable) {
    private val context: LLVMContextRef = LLVMContextCreate()
    private val module: LLVMModuleRef = LLVMModuleCreateWithNameInContext("my_module", context)
    private val builder: LLVMBuilderRef = LLVMCreateBuilderInContext(context)
    private val codegenSymbolTable = mutableMapOf<String, LLVMValueRef>() // Maps variable names to LLVMValueRef


    init {
        // Initialize LLVM components
        LLVMInitializeNativeTarget()
        LLVMInitializeNativeAsmPrinter()
        LLVMInitializeNativeAsmParser()

        // Declare external functions like printf
        declarePrintf()
    }

    fun generate(program: Program) {
        // Define main function
        val mainFuncType = LLVMFunctionType(
            LLVMInt32TypeInContext(context), // Return type: i32
            LLVMTypeRef(),                  // Parameter types: none
            0,                               // Is variadic: false
            0                                // Is varargs: false
        )
        val mainFunction = LLVMAddFunction(module, "main", mainFuncType)
        val entry = LLVMAppendBasicBlockInContext(context, mainFunction, "entry")
        LLVMPositionBuilderAtEnd(builder, entry)

        // Generate code for each statement
        program.statements.forEach { visit(it) }

        // Return 0
        LLVMBuildRet(builder, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
    }

    fun visit(stmt: Stmt) {
        when (stmt) {
            is Stmt.IfStmt -> visit(stmt)
            is Stmt.LetStmt -> visit(stmt)
            is Stmt.PrintStmt -> visit(stmt)
        }
    }

    private fun visit(stmt: Stmt.LetStmt) {
        val exprValue = visit(stmt.expr)
        // Allocate space based on the declared type
        val llvmType = when (stmt.type) {
            Type.INT -> LLVMInt32TypeInContext(context)
            Type.FLOAT -> LLVMFloatTypeInContext(context)
            Type.BOOL -> LLVMInt1TypeInContext(context)
            Type.STRING -> LLVMPointerType(LLVMInt8TypeInContext(context), 0)
            Type.VOID -> LLVMVoidTypeInContext(context)
        }
        // Allocate space for the variable (i32)
        val varPtr = LLVMBuildAlloca(builder, llvmType, stmt.name)
        // Store the value
        LLVMBuildStore(builder, exprValue, varPtr)
        // Update symbol table
        codegenSymbolTable[stmt.name] = varPtr
    }

    private fun visit(stmt: Stmt.PrintStmt) {
        val exprValue = visit(stmt.expr)
        val printfFunc = LLVMGetNamedFunction(module, "printf") ?: declarePrintf()
        val formatStr = LLVMBuildGlobalStringPtr(builder, "%d\n", "fmt")
        // Prepare arguments for printf: (i8*, i32)
        val args = PointerPointer<LLVMValueRef>(2)
        args.put(0, formatStr)
        args.put(1, exprValue)
        LLVMBuildCall2(
            builder,
            LLVMGetElementType(LLVMTypeOf(printfFunc)),
            printfFunc,
            args,
            2,
            "callprintf"
        )
    }

    private fun visit(stmt: Stmt.IfStmt) {
        val condValue = visit(stmt.condition)

        // Convert condition to i1 (boolean)
        val condBool = LLVMBuildICmp(
            builder,
            LLVMIntNE,
            condValue,
            LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0),
            "ifcond"
        )

        // Get the current block and its parent function
        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)

        // Create basic blocks for then, else, and merge
        val thenBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "then")
        val elseBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "else")
        val mergeBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "merge")

        // Build conditional branch
        LLVMBuildCondBr(builder, condBool, thenBlock, elseBlock)

        // Then block
        LLVMPositionBuilderAtEnd(builder, thenBlock)
        stmt.thenBranch.forEach { visit(it) }
        LLVMBuildBr(builder, mergeBlock)

        // Else block
        LLVMPositionBuilderAtEnd(builder, elseBlock)
        stmt.elseBranch?.forEach { visit(it) }
        LLVMBuildBr(builder, mergeBlock)

        // Merge block
        LLVMPositionBuilderAtEnd(builder, mergeBlock)
    }

    fun visit(expr: Expr): LLVMValueRef {
        return when (expr) {
            is Expr.Number -> LLVMConstInt(LLVMInt32TypeInContext(context), expr.value.toLong(), 0)
            is Expr.Variable -> {
                val varPtr = codegenSymbolTable[expr.name]
                    ?: throw Exception("Undefined variable during code generation: ${expr.name}")
                LLVMBuildLoad2(builder, LLVMInt32TypeInContext(context), varPtr, expr.name)
            }

            is Expr.BinaryOp -> {
                val left = visit(expr.left)
                val right = visit(expr.right)
                when (expr.operator) {
                    "+" -> LLVMBuildAdd(builder, left, right, "addtmp")
                    "-" -> LLVMBuildSub(builder, left, right, "subtmp")
                    "*" -> LLVMBuildMul(builder, left, right, "multmp")
                    "/" -> LLVMBuildSDiv(builder, left, right, "divtmp")
                    ">" -> LLVMBuildICmp(builder, LLVMIntSGT, left, right, "cmptmp") // Signed greater than
                    else -> throw Exception("Unknown operator: ${expr.operator}")
                }
            }
        }
    }

    private fun declarePrintf(): LLVMValueRef {
        // Define the printf function signature: i32 (i8*, ...)
        val printfReturnType = LLVMInt32TypeInContext(context)
        val printfParamType = LLVMPointerType(LLVMInt8TypeInContext(context), 0)

        // Create a PointerPointer for the parameter types
        val printfParamTypes = PointerPointer<LLVMTypeRef>(1) // Allocate space for 1 parameter
        printfParamTypes.put(0, printfParamType)              // Set the first (and only) parameter type to i8*

        // Define the function type: i32 (i8*, ...)
        val printfType = LLVMFunctionType(
            printfReturnType, // Return type: i32
            printfParamTypes, // Parameter types: i8*
            1,                // Number of parameters
            1                 // Is variadic: true (1 for true, 0 for false)
        )

        // Add the printf function to the module
        return LLVMAddFunction(module, "printf", printfType)
    }

    fun printIR() {
        val ir = LLVMPrintModuleToString(module)
        println(ir.string)
        LLVMDisposeMessage(ir)
    }

    fun writeIRToFile(filename: String) {
        val ir = LLVMPrintModuleToString(module)
        java.io.File(filename).writeText(ir.string)
        LLVMDisposeMessage(ir)
    }

    fun dispose() {
        LLVMDisposeBuilder(builder)
        LLVMDisposeModule(module)
        LLVMContextDispose(context)
    }
}