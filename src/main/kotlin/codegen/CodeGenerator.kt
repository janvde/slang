package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

class CodeGenerator() {
    private val context: LLVMContextRef = LLVMContextCreate()
    private val module: LLVMModuleRef = LLVMModuleCreateWithNameInContext("my_module", context)
    private val builder: LLVMBuilderRef = LLVMCreateBuilderInContext(context)
    private var codegenSymbolTable = mutableMapOf<String, SymbolInfo>() // Maps variable names to type + pointer

    // Predefined format strings
    private val formatStrings: MutableMap<Type, LLVMValueRef> = mutableMapOf()

    // Store function types for later use in calls
    private lateinit var printfFuncType: LLVMTypeRef
    private val strlenFuncType: LLVMTypeRef = run {
        val paramTypes = PointerPointer<LLVMTypeRef>(1)
        paramTypes.put(0, LLVMPointerType(LLVMInt8TypeInContext(context), 0))
        LLVMFunctionType(LLVMInt64TypeInContext(context), paramTypes, 1, 0)
    }
    private val functions = mutableMapOf<String, FunctionInfo>()
    private var currentFunctionReturnType: Type? = null
    private val listStructType: LLVMTypeRef = LLVMStructCreateNamed(context, "List")
    private val loopTargets = mutableListOf<LoopTarget>()

    private data class FunctionInfo(
        val name: String,
        val returnType: Type,
        val paramTypes: List<Type>,
        val llvmFunc: LLVMValueRef,
        val llvmType: LLVMTypeRef
    )

    private data class SymbolInfo(
        val type: Type,
        val ptr: LLVMValueRef,
        val immutable: Boolean = true
    )

    private data class LoopTarget(
        val continueBlock: LLVMBasicBlockRef,
        val breakBlock: LLVMBasicBlockRef
    )


    init {
        // Initialize LLVM components
        LLVMInitializeNativeTarget()
        LLVMInitializeNativeAsmPrinter()
        LLVMInitializeNativeAsmParser()

        declareListType()

        // Declare external functions like printf
        declarePrintf()

        declareFormatStrings()
    }

    private fun fail(location: SourceLocation, message: String): Nothing {
        throw Exception(location.format(message))
    }

    private fun declareListType() {
        val elements = PointerPointer<LLVMTypeRef>(2)
        elements.put(0L, LLVMInt64TypeInContext(context))
        elements.put(1L, LLVMPointerType(LLVMInt32TypeInContext(context), 0))
        LLVMStructSetBody(listStructType, elements, 2, 0)
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
            is Stmt.VarStmt -> visit(stmt)
            is Stmt.AssignStmt -> visit(stmt)
            is Stmt.PrintStmt -> visit(stmt)
            is Stmt.WhileStmt -> visit(stmt)
            is Stmt.ForStmt -> visit(stmt)
            is Stmt.BreakStmt -> visit(stmt)
            is Stmt.ContinueStmt -> visit(stmt)
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
            Type.LIST -> LLVMPointerType(listStructType, 0)
            Type.VOID -> LLVMVoidTypeInContext(context)
        }
        val varPtr = LLVMBuildAlloca(builder, llvmType, stmt.name)
        LLVMBuildStore(builder, exprValue, varPtr)
        codegenSymbolTable[stmt.name] = SymbolInfo(stmt.type, varPtr, immutable = true)
    }

    private fun visit(stmt: Stmt.VarStmt) {
        val exprValue = visit(stmt.expr)
        val llvmType = when (stmt.type) {
            Type.INT -> LLVMInt32TypeInContext(context)
            Type.FLOAT -> LLVMFloatTypeInContext(context)
            Type.BOOL -> LLVMInt1TypeInContext(context)
            Type.STRING -> LLVMPointerType(LLVMInt8TypeInContext(context), 0)
            Type.LIST -> LLVMPointerType(listStructType, 0)
            Type.VOID -> LLVMVoidTypeInContext(context)
        }
        val varPtr = LLVMBuildAlloca(builder, llvmType, stmt.name)
        LLVMBuildStore(builder, exprValue, varPtr)
        codegenSymbolTable[stmt.name] = SymbolInfo(stmt.type, varPtr, immutable = false)
    }

    private fun visit(stmt: Stmt.AssignStmt) {
        val symbol = codegenSymbolTable[stmt.name]
            ?: throw Exception("Undefined variable '${stmt.name}'.")

        if (symbol.immutable) {
            throw Exception("Cannot reassign immutable variable '${stmt.name}'.")
        }

        val exprValue = visit(stmt.expr)
        LLVMBuildStore(builder, exprValue, symbol.ptr)
    }


    /**
     * Generates LLVM IR for a PrintStmt.
     */
    private fun visit(stmt: Stmt.PrintStmt) {
        // Visit the expression to be printed and get its LLVMValueRef
        val exprValue = visit(stmt.expr)

        // Get the printf function, declaring it if not already declared
        val printfFunc = LLVMGetNamedFunction(module, "printf") ?: declarePrintf()

        // Determine the type of the expression
        val exprType = inferExprType(stmt.expr)

        if (exprType == Type.LIST) {
            emitPrintList(exprValue, printfFunc)
            return
        }

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

        buildPrintfCall(printfFunc, gepFormatStr, exprValue)
    }


    private fun visit(stmt: Stmt.IfStmt) {
        if (inferExprType(stmt.condition) != Type.BOOL) {
            fail(stmt.condition.location, "If condition must be Bool.")
        }
        val condValue = visit(stmt.condition)

        // Get the current block and its parent function
        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)

        // Create basic blocks for then, else, and merge
        val thenBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "then")
        val elseBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "else")
        val mergeBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "merge")

        // Build conditional branch
        LLVMBuildCondBr(builder, condValue, thenBlock, elseBlock)

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

    private fun visit(stmt: Stmt.WhileStmt) {
        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)
        if (inferExprType(stmt.condition) != Type.BOOL) {
            fail(stmt.condition.location, "While condition must be Bool.")
        }

        // Create basic blocks for while loop
        val condBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_cond")
        val bodyBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_body")
        val afterBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_after")

        // Branch to condition block
        LLVMBuildBr(builder, condBlock)

        // Condition block
        LLVMPositionBuilderAtEnd(builder, condBlock)
        val condValue = visit(stmt.condition)

        LLVMBuildCondBr(builder, condValue, bodyBlock, afterBlock)

        // Body block
        LLVMPositionBuilderAtEnd(builder, bodyBlock)
        loopTargets.add(LoopTarget(continueBlock = condBlock, breakBlock = afterBlock))
        try {
            stmt.body.forEach { visit(it) }
        } finally {
            loopTargets.removeAt(loopTargets.size - 1)
        }
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, condBlock)  // Loop back to condition
        }

        // After block
        LLVMPositionBuilderAtEnd(builder, afterBlock)
    }

    private fun visit(stmt: Stmt.ForStmt) {
        stmt.init?.let { visit(it) }

        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)

        val condBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "for_cond")
        val bodyBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "for_body")
        val updateBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "for_update")
        val afterBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "for_after")

        LLVMBuildBr(builder, condBlock)

        LLVMPositionBuilderAtEnd(builder, condBlock)
        if (stmt.condition != null) {
            if (inferExprType(stmt.condition) != Type.BOOL) {
                fail(stmt.condition.location, "For-loop condition must be Bool.")
            }
            val condValue = visit(stmt.condition)
            LLVMBuildCondBr(builder, condValue, bodyBlock, afterBlock)
        } else {
            LLVMBuildBr(builder, bodyBlock)
        }

        LLVMPositionBuilderAtEnd(builder, bodyBlock)
        loopTargets.add(LoopTarget(continueBlock = updateBlock, breakBlock = afterBlock))
        try {
            stmt.body.forEach { visit(it) }
        } finally {
            loopTargets.removeAt(loopTargets.size - 1)
        }
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, updateBlock)
        }

        LLVMPositionBuilderAtEnd(builder, updateBlock)
        stmt.update?.let { visit(it) }
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, condBlock)
        }

        LLVMPositionBuilderAtEnd(builder, afterBlock)
    }

    private fun visit(stmt: Stmt.BreakStmt) {
        val target = loopTargets.lastOrNull()
            ?: fail(stmt.location, "'break' is only allowed inside a loop.")
        LLVMBuildBr(builder, target.breakBlock)
    }

    private fun visit(stmt: Stmt.ContinueStmt) {
        val target = loopTargets.lastOrNull()
            ?: fail(stmt.location, "'continue' is only allowed inside a loop.")
        LLVMBuildBr(builder, target.continueBlock)
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

    private fun buildPrintfCall(
        printfFunc: LLVMValueRef,
        formatPtr: LLVMValueRef,
        value: LLVMValueRef
    ) {
        val args = PointerPointer<LLVMValueRef>(2).apply {
            put(0, formatPtr)
            put(1, value)
        }

        LLVMBuildCall2(
            builder,
            printfFuncType,
            printfFunc,
            args,
            2,
            "callprintf"
        ) ?: throw Exception("Failed to build call to printf")
    }

    private fun emitPrintList(listPtr: LLVMValueRef, printfFunc: LLVMValueRef) {
        val lenPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 0, "list_len_ptr")
        val dataPtrPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 1, "list_data_ptr")

        val lenVal = LLVMBuildLoad2(builder, LLVMInt64TypeInContext(context), lenPtr, "list_len")
        val dataPtr = LLVMBuildLoad2(
            builder,
            LLVMPointerType(LLVMInt32TypeInContext(context), 0),
            dataPtrPtr,
            "list_data"
        )

        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)

        val loopBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "list_loop")
        val bodyBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "list_body")
        val afterBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "list_after")

        val indexPtr = LLVMBuildAlloca(builder, LLVMInt64TypeInContext(context), "list_index")
        LLVMBuildStore(builder, LLVMConstInt(LLVMInt64TypeInContext(context), 0, 0), indexPtr)
        LLVMBuildBr(builder, loopBlock)

        LLVMPositionBuilderAtEnd(builder, loopBlock)
        val indexVal = LLVMBuildLoad2(builder, LLVMInt64TypeInContext(context), indexPtr, "list_index_val")
        val loopCond = LLVMBuildICmp(builder, LLVMIntSLT, indexVal, lenVal, "list_loop_cond")
        LLVMBuildCondBr(builder, loopCond, bodyBlock, afterBlock)

        LLVMPositionBuilderAtEnd(builder, bodyBlock)
        val elemIndices = PointerPointer<LLVMValueRef>(1).apply {
            put(0, indexVal)
        }
        val elemPtr = LLVMBuildGEP2(
            builder,
            LLVMInt32TypeInContext(context),
            dataPtr,
            elemIndices,
            1,
            "list_elem_ptr"
        )
        val elemVal = LLVMBuildLoad2(builder, LLVMInt32TypeInContext(context), elemPtr, "list_elem")

        val formatStr = formatStrings[Type.INT]
            ?: throw Exception("No format string found for type: ${Type.INT}")
        val zero = LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0)
        val indices = PointerPointer<LLVMValueRef>(2).apply {
            put(0, zero)
            put(1, zero)
        }
        val gepFormatStr = LLVMBuildGEP2(
            builder,
            LLVMInt8TypeInContext(context),
            formatStr,
            indices,
            2,
            "fmt_ptr"
        ) ?: throw Exception("Failed to build GEP for format string")
        buildPrintfCall(printfFunc, gepFormatStr, elemVal)

        val nextIndex = LLVMBuildAdd(
            builder,
            indexVal,
            LLVMConstInt(LLVMInt64TypeInContext(context), 1, 0),
            "list_index_next"
        )
        LLVMBuildStore(builder, nextIndex, indexPtr)
        LLVMBuildBr(builder, loopBlock)

        LLVMPositionBuilderAtEnd(builder, afterBlock)
    }

    private fun buildListLiteral(expr: Expr.ListLiteral): LLVMValueRef {
        val length = expr.elements.size
        val lenValue = LLVMConstInt(LLVMInt64TypeInContext(context), length.toLong(), 0)
        val dataPtr = if (length == 0) {
            LLVMConstNull(LLVMPointerType(LLVMInt32TypeInContext(context), 0))
        } else {
            val arrayType = LLVMArrayType(LLVMInt32TypeInContext(context), length)
            val arrayAlloca = LLVMBuildAlloca(builder, arrayType, "list_data")
            expr.elements.forEachIndexed { index, element ->
                val elemValue = visit(element)
                val indices = PointerPointer<LLVMValueRef>(2).apply {
                    put(0, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
                    put(1, LLVMConstInt(LLVMInt32TypeInContext(context), index.toLong(), 0))
                }
                val elemPtr = LLVMBuildGEP2(
                    builder,
                    arrayType,
                    arrayAlloca,
                    indices,
                    2,
                    "list_elem_ptr"
                )
                LLVMBuildStore(builder, elemValue, elemPtr)
            }

            val dataIndices = PointerPointer<LLVMValueRef>(2).apply {
                put(0, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
                put(1, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
            }
            LLVMBuildGEP2(
                builder,
                arrayType,
                arrayAlloca,
                dataIndices,
                2,
                "list_data_ptr"
            )
        }

        val listAlloca = LLVMBuildAlloca(builder, listStructType, "list_struct")
        val lenPtr = LLVMBuildStructGEP2(builder, listStructType, listAlloca, 0, "list_len_ptr")
        LLVMBuildStore(builder, lenValue, lenPtr)
        val dataPtrPtr = LLVMBuildStructGEP2(builder, listStructType, listAlloca, 1, "list_data_ptr")
        LLVMBuildStore(builder, dataPtr, dataPtrPtr)

        return listAlloca
    }

    private fun inferExprType(expr: Expr): Type {
        return when (expr) {
            is Expr.Number -> Type.INT
            is Expr.FloatLiteral -> Type.FLOAT
            is Expr.BoolLiteral -> Type.BOOL
            is Expr.StringLiteral -> Type.STRING
            is Expr.Variable -> codegenSymbolTable[expr.name]?.type
                ?: throw Exception("Undefined variable during type inference: ${expr.name}")
            is Expr.UnaryOp -> when (expr.operator) {
                "!" -> Type.BOOL
                else -> throw Exception("Unknown unary operator: ${expr.operator}")
            }
            is Expr.Call -> {
                if (expr.name == "len") {
                    Type.INT
                } else {
                    functions[expr.name]?.returnType
                        ?: throw Exception("Undefined function during type inference: ${expr.name}")
                }
            }
            is Expr.ListLiteral -> Type.LIST
            is Expr.Index -> Type.INT
            is Expr.BinaryOp -> when (expr.operator) {
                ">", "<", ">=", "<=", "==", "!=", "&&", "||" -> Type.BOOL
                "+", "-", "*", "/" -> {
                    val leftType = inferExprType(expr.left)
                    val rightType = inferExprType(expr.right)
                    if (leftType == Type.FLOAT || rightType == Type.FLOAT) Type.FLOAT else Type.INT
                }
                "%" -> Type.INT
                else -> Type.INT
            }
        }
    }

    fun visit(expr: Expr): LLVMValueRef {
        return when (expr) {
            is Expr.Number -> LLVMConstInt(LLVMInt32TypeInContext(context), expr.value.toLong(), 0)
            is Expr.FloatLiteral -> LLVMConstReal(LLVMFloatTypeInContext(context), expr.value.toDouble())
            is Expr.BoolLiteral -> LLVMConstInt(LLVMInt1TypeInContext(context), if (expr.value) 1 else 0, 0)
            is Expr.StringLiteral -> LLVMBuildGlobalStringPtr(builder, expr.value, "str")
            is Expr.Variable -> {
                val symbol = codegenSymbolTable[expr.name]
                    ?: throw Exception("Undefined variable during code generation: ${expr.name}")
                LLVMBuildLoad2(builder, llvmTypeFor(symbol.type), symbol.ptr, expr.name)
            }
            is Expr.UnaryOp -> {
                val operand = visit(expr.operand)
                when (expr.operator) {
                    "!" -> {
                        // Type check: must be Bool
                        val operandType = inferExprType(expr.operand)
                        if (operandType != Type.BOOL) {
                            throw Exception("NOT operator requires Bool operand.")
                        }
                        LLVMBuildNot(builder, operand, "nottmp")
                    }
                    else -> throw Exception("Unknown unary operator: ${expr.operator}")
                }
            }
            is Expr.Call -> buildCall(expr, allowVoid = false)
            is Expr.ListLiteral -> buildListLiteral(expr)
            is Expr.Index -> buildIndexExpr(expr)

            is Expr.BinaryOp -> {
                // Special handling for short-circuit operators
                if (expr.operator == "&&" || expr.operator == "||") {
                    return buildShortCircuitLogical(expr)
                }

                var left = visit(expr.left)
                var right = visit(expr.right)
                val leftType = inferExprType(expr.left)
                val rightType = inferExprType(expr.right)

                // Type promotion: if mixed Int/Float, promote Int to Float
                if (leftType == Type.INT && rightType == Type.FLOAT) {
                    left = LLVMBuildSIToFP(builder, left, LLVMFloatTypeInContext(context), "inttofloat")
                } else if (leftType == Type.FLOAT && rightType == Type.INT) {
                    right = LLVMBuildSIToFP(builder, right, LLVMFloatTypeInContext(context), "inttofloat")
                }

                val isFloat = (leftType == Type.FLOAT || rightType == Type.FLOAT)

                when (expr.operator) {
                    "+" -> if (isFloat) LLVMBuildFAdd(builder, left, right, "faddtmp")
                           else LLVMBuildAdd(builder, left, right, "addtmp")
                    "-" -> if (isFloat) LLVMBuildFSub(builder, left, right, "fsubtmp")
                           else LLVMBuildSub(builder, left, right, "subtmp")
                    "*" -> if (isFloat) LLVMBuildFMul(builder, left, right, "fmultmp")
                           else LLVMBuildMul(builder, left, right, "multmp")
                    "/" -> if (isFloat) LLVMBuildFDiv(builder, left, right, "fdivtmp")
                           else LLVMBuildSDiv(builder, left, right, "divtmp")
                    "%" -> LLVMBuildSRem(builder, left, right, "modtmp")
                    ">" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOGT, left, right, "fcmptmp")
                           else LLVMBuildICmp(builder, LLVMIntSGT, left, right, "cmptmp")
                    "<" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOLT, left, right, "fcmptmp")
                           else LLVMBuildICmp(builder, LLVMIntSLT, left, right, "cmptmp")
                    ">=" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOGE, left, right, "fcmptmp")
                            else LLVMBuildICmp(builder, LLVMIntSGE, left, right, "cmptmp")
                    "<=" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOLE, left, right, "fcmptmp")
                            else LLVMBuildICmp(builder, LLVMIntSLE, left, right, "cmptmp")
                    "==" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealOEQ, left, right, "fcmptmp")
                            else LLVMBuildICmp(builder, LLVMIntEQ, left, right, "cmptmp")
                    "!=" -> if (isFloat) LLVMBuildFCmp(builder, LLVMRealONE, left, right, "fcmptmp")
                            else LLVMBuildICmp(builder, LLVMIntNE, left, right, "cmptmp")
                    else -> throw Exception("Unknown operator: ${expr.operator}")
                }
            }
        }
    }

    private fun buildShortCircuitLogical(expr: Expr.BinaryOp): LLVMValueRef {
        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)

        // Type check
        if (inferExprType(expr.left) != Type.BOOL || inferExprType(expr.right) != Type.BOOL) {
            throw Exception("Logical operators require Bool operands.")
        }

        val rightBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "logical_right")
        val mergeBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "logical_merge")

        val leftValue = visit(expr.left)
        val leftEndBlock = LLVMGetInsertBlock(builder)

        if (expr.operator == "&&") {
            // If left is false, skip right and return false
            LLVMBuildCondBr(builder, leftValue, rightBlock, mergeBlock)
        } else {  // ||
            // If left is true, skip right and return true
            LLVMBuildCondBr(builder, leftValue, mergeBlock, rightBlock)
        }

        // Right block
        LLVMPositionBuilderAtEnd(builder, rightBlock)
        val rightValue = visit(expr.right)
        val rightEndBlock = LLVMGetInsertBlock(builder)
        LLVMBuildBr(builder, mergeBlock)

        // Merge block with phi node
        LLVMPositionBuilderAtEnd(builder, mergeBlock)
        val phi = LLVMBuildPhi(builder, LLVMInt1TypeInContext(context), "logical_result")

        val incomingValues = PointerPointer<LLVMValueRef>(2)
        val incomingBlocks = PointerPointer<LLVMBasicBlockRef>(2)

        if (expr.operator == "&&") {
            // From left block: false
            incomingValues.put(0, LLVMConstInt(LLVMInt1TypeInContext(context), 0, 0))
            incomingBlocks.put(0, leftEndBlock)
            // From right block: actual right value
            incomingValues.put(1, rightValue)
            incomingBlocks.put(1, rightEndBlock)
        } else {  // ||
            // From left block: true
            incomingValues.put(0, LLVMConstInt(LLVMInt1TypeInContext(context), 1, 0))
            incomingBlocks.put(0, leftEndBlock)
            // From right block: actual right value
            incomingValues.put(1, rightValue)
            incomingBlocks.put(1, rightEndBlock)
        }

        LLVMAddIncoming(phi, incomingValues, incomingBlocks, 2)
        return phi
    }

    private fun buildCall(expr: Expr.Call, allowVoid: Boolean): LLVMValueRef {
        if (expr.name == "len") {
            if (expr.args.size != 1) {
                fail(expr.location, "len expects 1 argument, got ${expr.args.size}.")
            }
            val argType = inferExprType(expr.args[0])
            val argValue = visit(expr.args[0])
            return when (argType) {
                Type.LIST -> buildListLength(argValue)
                Type.STRING -> buildStringLength(argValue)
                else -> fail(expr.args[0].location, "len expects a List or String argument.")
            }
        }
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

    private fun buildListLength(listPtr: LLVMValueRef): LLVMValueRef {
        val lenPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 0, "list_len_ptr")
        val lenVal = LLVMBuildLoad2(builder, LLVMInt64TypeInContext(context), lenPtr, "list_len")
        return LLVMBuildTrunc(builder, lenVal, LLVMInt32TypeInContext(context), "list_len_i32")
    }

    private fun buildStringLength(strPtr: LLVMValueRef): LLVMValueRef {
        val strlenFn = LLVMGetNamedFunction(module, "strlen") ?: declareStrlen()
        val args = PointerPointer<LLVMValueRef>(1).apply { put(0, strPtr) }
        val len64 = LLVMBuildCall2(
            builder,
            strlenFuncType,
            strlenFn,
            args,
            1,
            "strlen_call"
        ) ?: throw Exception("Failed to build call to strlen.")
        return LLVMBuildTrunc(builder, len64, LLVMInt32TypeInContext(context), "strlen_i32")
    }

    private fun buildIndexExpr(expr: Expr.Index): LLVMValueRef {
        val listPtr = visit(expr.target)
        val indexValue = visit(expr.index)
        val dataPtrPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 1, "list_data_ptr")
        val dataPtr = LLVMBuildLoad2(
            builder,
            LLVMPointerType(LLVMInt32TypeInContext(context), 0),
            dataPtrPtr,
            "list_data"
        )
        val elemIndices = PointerPointer<LLVMValueRef>(1).apply {
            put(0, indexValue)
        }
        val elemPtr = LLVMBuildGEP2(
            builder,
            LLVMInt32TypeInContext(context),
            dataPtr,
            elemIndices,
            1,
            "list_elem_ptr"
        )
        return LLVMBuildLoad2(builder, LLVMInt32TypeInContext(context), elemPtr, "list_elem")
    }

    private fun declareFunction(stmt: Stmt.FunctionDef) {
        if (functions.containsKey(stmt.name)) {
            throw Exception("Function '${stmt.name}' already declared.")
        }
        if (stmt.name == "len") {
            throw Exception("Cannot redefine built-in function 'len'.")
        }
        if (stmt.returnType == Type.LIST) {
            throw Exception("List return types are not supported in LLVM codegen yet.")
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
            codegenSymbolTable[param.name] = SymbolInfo(param.type, paramPtr)
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
                Type.LIST -> throw Exception("List return type is not supported in LLVM codegen yet.")
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
            Type.LIST -> LLVMPointerType(listStructType, 0)
            Type.VOID -> LLVMVoidTypeInContext(context)
        }
    }

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

    private fun declareStrlen(): LLVMValueRef {
        return LLVMAddFunction(module, "strlen", strlenFuncType)
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
