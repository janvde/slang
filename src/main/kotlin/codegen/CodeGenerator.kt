package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

class CodeGenerator() {
    private val context: LLVMContextRef = LLVMContextCreate()
    private val module: LLVMModuleRef = LLVMModuleCreateWithNameInContext("my_module", context)
    private val builder: LLVMBuilderRef = LLVMCreateBuilderInContext(context)
    private var codegenSymbolTable = mutableMapOf<String, LLVMValueRef>() // Maps variable names to LLVMValueRef

    // Predefined format strings
    private val formatStrings: MutableMap<Type, LLVMValueRef> = mutableMapOf()

    // Store function types for later use in calls
    private lateinit var printfFuncType: LLVMTypeRef
    private val functions = mutableMapOf<String, FunctionInfo>()
    private var currentFunctionReturnType: Type? = null

    private data class FunctionInfo(
        val name: String,
        val returnType: Type,
        val paramTypes: List<Type>,
        val llvmFunc: LLVMValueRef,
        val llvmType: LLVMTypeRef
    )


    init {
        // Initialize LLVM components
        LLVMInitializeNativeTarget()
        LLVMInitializeNativeAsmPrinter()
        LLVMInitializeNativeAsmParser()

        // Declare external functions like printf
        declarePrintf()

        declareFormatStrings()
    }

    fun generate(program: Program) {
        // Declare function signatures first
        program.statements.filterIsInstance<Stmt.FunctionDef>()
            .forEach { declareFunction(it) }

        // Generate function bodies
        program.statements.filterIsInstance<Stmt.FunctionDef>()
            .forEach { generateFunctionBody(it) }

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

        val previousSymbols = codegenSymbolTable
        codegenSymbolTable = mutableMapOf()

        val previousReturnType = currentFunctionReturnType
        currentFunctionReturnType = Type.INT

        // Generate code for each statement
        program.statements.forEach {
            if (it !is Stmt.FunctionDef) {
                if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                    visit(it)
                }
            }
        }

        // Return 0
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildRet(builder, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
        }

        currentFunctionReturnType = previousReturnType
        codegenSymbolTable = previousSymbols
    }

    fun visit(stmt: Stmt) {
        when (stmt) {
            is Stmt.IfStmt -> visit(stmt)
            is Stmt.LetStmt -> visit(stmt)
            is Stmt.PrintStmt -> visit(stmt)
            is Stmt.FunctionDef -> {}
            is Stmt.ReturnStmt -> visit(stmt)
            is Stmt.ExprStmt -> visit(stmt)
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

//    private fun visit(stmt: Stmt.PrintStmt) {
//        val exprValue = visit(stmt.expr)
//        val printfFunc = LLVMGetNamedFunction(module, "printf") ?: declarePrintf()
//        val formatStr = LLVMBuildGlobalStringPtr(builder, "%d\n", "fmt")
//
//        // Prepare arguments for printf: (i8*, i32)
//        val args = PointerPointer<LLVMValueRef>(2)
//        args.put(0, formatStr)
//        args.put(1, exprValue)
//        LLVMBuildCall2(
//            builder,
//            LLVMGetElementType(LLVMTypeOf(printfFunc)),
//            printfFunc,
//            args,
//            2,
//            "callprintf"
//        )
//    }


    /**
     * Generates LLVM IR for a PrintStmt.
     */
    private fun visit(stmt: Stmt.PrintStmt) {
        // Visit the expression to be printed and get its LLVMValueRef
        val exprValue = visit(stmt.expr)

        // Get the printf function, declaring it if not already declared
        val printfFunc = LLVMGetNamedFunction(module, "printf") ?: declarePrintf()

        // Determine the type of the expression
        val exprType = Type.INT

        // Retrieve the corresponding format string
        val formatStr = formatStrings[exprType]
            ?: throw Exception("No format string found for type: $exprType")

        // Get a pointer to the first element of the format string array ([N x i8] -> i8*)
        val zero = LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0)
        val indices = PointerPointer<LLVMValueRef>(2).apply {
            put(0, zero) // First index: the global variable itself
            put(1, zero) // Second index: the first element of the array
        }

        // Build the GEP to obtain i8* from [N x i8]
        val gepFormatStr = LLVMBuildGEP2(
            builder,
            LLVMInt8TypeInContext(context), // Element type: i8
            formatStr,
            indices,
            2,
            "fmt_ptr"
        ) ?: throw Exception("Failed to build GEP for format string")

        // Prepare arguments for printf: (i8*, <type>)
        val args = PointerPointer<LLVMValueRef>(2).apply {
            put(0, gepFormatStr) // First argument: format string
            put(1, exprValue)    // Second argument: value to print
        }

        // Build the call to printf using the stored function type
        LLVMBuildCall2(
            builder,
            printfFuncType, // Function type: i32 (i8*, ...)
            printfFunc,
            args,
            2,          // Number of arguments
            "callprintf"
        ) ?: throw Exception("Failed to build call to printf")
    }


    private fun visit(stmt: Stmt.IfStmt) {
        val condValue = visit(stmt.condition)

        // Convert condition to i1 (boolean) if it's not already
        val condBool = if (LLVMGetTypeKind(LLVMTypeOf(condValue)) == LLVMIntegerTypeKind &&
                           LLVMGetIntTypeWidth(LLVMTypeOf(condValue)) == 1) {
            // Already i1, use it directly
            condValue
        } else {
            // Need to convert i32 (or other int) to i1 by comparing to 0
            LLVMBuildICmp(
                builder,
                LLVMIntNE,
                condValue,
                LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0),
                "ifcond"
            )
        }

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
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, mergeBlock)
        }

        // Else block
        LLVMPositionBuilderAtEnd(builder, elseBlock)
        stmt.elseBranch?.forEach { visit(it) }
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, mergeBlock)
        }

        // Merge block
        LLVMPositionBuilderAtEnd(builder, mergeBlock)
    }

    private fun visit(stmt: Stmt.ReturnStmt) {
        val expectedReturnType = currentFunctionReturnType
            ?: throw Exception("Return statement is only valid inside a function.")
        val returnValue = stmt.expr?.let { visit(it) }

        if (expectedReturnType == Type.VOID) {
            if (returnValue != null) {
                throw Exception("Void function should not return a value.")
            }
            LLVMBuildRetVoid(builder)
        } else {
            if (returnValue == null) {
                throw Exception("Non-void function must return a value.")
            }
            LLVMBuildRet(builder, returnValue)
        }
    }

    private fun visit(stmt: Stmt.ExprStmt) {
        when (val expr = stmt.expr) {
            is Expr.Call -> {
                buildCall(expr, allowVoid = true)
            }
            else -> {
                visit(expr)
            }
        }
    }

    fun visit(expr: Expr): LLVMValueRef {
        return when (expr) {
            is Expr.Number -> LLVMConstInt(LLVMInt32TypeInContext(context), expr.value.toLong(), 0)
            is Expr.Variable -> {
                val varPtr = codegenSymbolTable[expr.name]
                    ?: throw Exception("Undefined variable during code generation: ${expr.name}")
                LLVMBuildLoad2(builder, LLVMInt32TypeInContext(context), varPtr, expr.name)
            }
            is Expr.Call -> buildCall(expr, allowVoid = false)

            is Expr.BinaryOp -> {
                val left = visit(expr.left)
                val right = visit(expr.right)
                when (expr.operator) {
                    "+" -> LLVMBuildAdd(builder, left, right, "addtmp")
                    "-" -> LLVMBuildSub(builder, left, right, "subtmp")
                    "*" -> LLVMBuildMul(builder, left, right, "multmp")
                    "/" -> LLVMBuildSDiv(builder, left, right, "divtmp")
                    ">" -> LLVMBuildICmp(builder, LLVMIntSGT, left, right, "cmptmp")  // Signed greater than
                    "<" -> LLVMBuildICmp(builder, LLVMIntSLT, left, right, "cmptmp")  // Signed less than
                    ">=" -> LLVMBuildICmp(builder, LLVMIntSGE, left, right, "cmptmp") // Signed greater or equal
                    "<=" -> LLVMBuildICmp(builder, LLVMIntSLE, left, right, "cmptmp") // Signed less or equal
                    "==" -> LLVMBuildICmp(builder, LLVMIntEQ, left, right, "cmptmp")  // Equal
                    "!=" -> LLVMBuildICmp(builder, LLVMIntNE, left, right, "cmptmp")  // Not equal
                    else -> throw Exception("Unknown operator: ${expr.operator}")
                }
            }
        }
    }

    private fun buildCall(expr: Expr.Call, allowVoid: Boolean): LLVMValueRef {
        val info = functions[expr.name]
            ?: throw Exception("Undefined function during code generation: ${expr.name}")

        if (expr.args.size != info.paramTypes.size) {
            throw Exception("Function '${expr.name}' expects ${info.paramTypes.size} arguments, got ${expr.args.size}.")
        }
        if (!allowVoid && info.returnType == Type.VOID) {
            throw Exception("Cannot use void function '${expr.name}' in an expression.")
        }

        val args = PointerPointer<LLVMValueRef>(expr.args.size.toLong())
        expr.args.forEachIndexed { index, arg ->
            args.put(index.toLong(), visit(arg))
        }

        val callValue = LLVMBuildCall2(
            builder,
            info.llvmType,
            info.llvmFunc,
            args,
            expr.args.size,
            "calltmp"
        ) ?: throw Exception("Failed to build call for function '${expr.name}'.")
        return callValue
    }

    private fun declareFunction(stmt: Stmt.FunctionDef) {
        if (functions.containsKey(stmt.name)) {
            throw Exception("Function '${stmt.name}' already declared.")
        }

        val returnType = llvmTypeFor(stmt.returnType)
        val paramTypes = stmt.params.map { llvmTypeFor(it.type) }

        val paramPointer = if (paramTypes.isEmpty()) {
            PointerPointer<LLVMTypeRef>(0)
        } else {
            val paramsPtr = PointerPointer<LLVMTypeRef>(paramTypes.size.toLong())
            paramTypes.forEachIndexed { index, llvmType -> paramsPtr.put(index.toLong(), llvmType) }
            paramsPtr
        }

        val fnType = LLVMFunctionType(
            returnType,
            paramPointer,
            paramTypes.size,
            0
        )
        val fn = LLVMAddFunction(module, stmt.name, fnType)
        functions[stmt.name] = FunctionInfo(
            name = stmt.name,
            returnType = stmt.returnType,
            paramTypes = stmt.params.map { it.type },
            llvmFunc = fn,
            llvmType = fnType
        )
    }

    private fun generateFunctionBody(stmt: Stmt.FunctionDef) {
        val info = functions[stmt.name] ?: throw Exception("Missing function info for '${stmt.name}'.")
        val entry = LLVMAppendBasicBlockInContext(context, info.llvmFunc, "entry")
        LLVMPositionBuilderAtEnd(builder, entry)

        val previousReturnType = currentFunctionReturnType
        currentFunctionReturnType = stmt.returnType

        val previousSymbols = codegenSymbolTable
        codegenSymbolTable = mutableMapOf()

        // Map parameters to allocas for local variable access
        stmt.params.forEachIndexed { index, param ->
            val paramValue = LLVMGetParam(info.llvmFunc, index)
            val paramPtr = LLVMBuildAlloca(builder, llvmTypeFor(param.type), param.name)
            LLVMBuildStore(builder, paramValue, paramPtr)
            codegenSymbolTable[param.name] = paramPtr
        }

        stmt.body.forEach { statement ->
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                visit(statement)
            }
        }

        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            when (stmt.returnType) {
                Type.VOID -> LLVMBuildRetVoid(builder)
                Type.INT -> LLVMBuildRet(builder, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
                Type.FLOAT -> LLVMBuildRet(builder, LLVMConstReal(LLVMFloatTypeInContext(context), 0.0))
                Type.BOOL -> LLVMBuildRet(builder, LLVMConstInt(LLVMInt1TypeInContext(context), 0, 0))
                Type.STRING -> LLVMBuildRet(builder, LLVMConstNull(LLVMPointerType(LLVMInt8TypeInContext(context), 0)))
            }
        }

        currentFunctionReturnType = previousReturnType
        codegenSymbolTable = previousSymbols
    }

    private fun llvmTypeFor(type: Type): LLVMTypeRef {
        return when (type) {
            Type.INT -> LLVMInt32TypeInContext(context)
            Type.FLOAT -> LLVMFloatTypeInContext(context)
            Type.BOOL -> LLVMInt1TypeInContext(context)
            Type.STRING -> LLVMPointerType(LLVMInt8TypeInContext(context), 0)
            Type.VOID -> LLVMVoidTypeInContext(context)
        }
    }

//    private fun declarePrintf(): LLVMValueRef {
//        // Define the printf function signature: i32 (i8*, ...)
//        val printfReturnType = LLVMInt32TypeInContext(context)
//        val printfParamType = LLVMPointerType(LLVMInt8TypeInContext(context), 0)
//
//        // Create a PointerPointer for the parameter types
//        val printfParamTypes = PointerPointer<LLVMTypeRef>(1) // Allocate space for 1 parameter
//        printfParamTypes.put(0, printfParamType)              // Set the first (and only) parameter type to i8*
//
//        // Define the function type: i32 (i8*, ...)
//        val printfType = LLVMFunctionType(
//            printfReturnType, // Return type: i32
//            printfParamTypes, // Parameter types: i8*
//            1,                // Number of parameters
//            1                 // Is variadic: true (1 for true, 0 for false)
//        )
//
//        // Add the printf function to the module
//        return LLVMAddFunction(module, "printf", printfType)
//    }

    private fun declarePrintf(): LLVMValueRef {
        // Define the printf function signature: i32 (i8*, ...)
        val printfReturnType = LLVMInt32TypeInContext(context)
        val printfParamType = LLVMPointerType(LLVMInt8TypeInContext(context), 0) // i8*

        // Create a PointerPointer for the parameter types (only the first non-variadic parameter)
        val printfParamTypes = PointerPointer<LLVMTypeRef>(1)
        printfParamTypes.put(0, printfParamType)

        // Define the function type: i32 (i8*, ...)
        printfFuncType = LLVMFunctionType(
            printfReturnType, // Return type: i32
            printfParamTypes, // Parameter types: [i8*]
            1,                // Number of parameters
            1                 // Is variadic: true (1)
        )

        // Add the printf function to the module
        return LLVMAddFunction(module, "printf", printfFuncType)
    }


    private fun declareFormatStrings() {
        // Helper function to create a global string as a constant array of i8
        fun createGlobalString(name: String, value: String): LLVMValueRef {
            val strLength = value.length
            val arrayType = LLVMArrayType(LLVMInt8TypeInContext(context), strLength + 1) // +1 for null terminator
            val global = LLVMAddGlobal(module, arrayType, name)
            LLVMSetLinkage(global, LLVMPrivateLinkage)
            // LLVMConstString creates a [N x i8] constant without the null terminator (since the array type includes it)
            val initializer = LLVMConstStringInContext(context, value, strLength, 0)
            LLVMSetInitializer(global, initializer)
            LLVMSetGlobalConstant(global, 1) // Mark as constant
            return global
        }

        // Declare format strings for different types
        formatStrings[Type.INT] = createGlobalString("fmt_int", "%d\n")
        formatStrings[Type.STRING] = createGlobalString("fmt_str", "%s\n")
        formatStrings[Type.FLOAT] = createGlobalString("fmt_float", "%f\n")
        formatStrings[Type.BOOL] = createGlobalString("fmt_bool", "%d\n")
        // Add more format strings here if needed
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
