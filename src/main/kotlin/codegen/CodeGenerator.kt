package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
import nl.endevelopment.semantic.core.BuiltinRegistry
import nl.endevelopment.semantic.core.CallResolver
import nl.endevelopment.semantic.core.ProgramIndex
import nl.endevelopment.semantic.core.TypeRules
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

class CodeGenerator {
    private val context: LLVMContextRef = LLVMContextCreate()
    private val module: LLVMModuleRef = LLVMModuleCreateWithNameInContext("my_module", context)
    private val builder: LLVMBuilderRef = LLVMCreateBuilderInContext(context)
    private var codegenSymbolTable = mutableMapOf<String, SymbolInfo>()
    private val charPtrType: LLVMTypeRef = LLVMPointerType(LLVMInt8TypeInContext(context), 0)

    private val formatStrings: MutableMap<Type, LLVMValueRef> = mutableMapOf()

    private lateinit var printfFuncType: LLVMTypeRef
    private val strlenFuncType: LLVMTypeRef = run {
        val paramTypes = PointerPointer<LLVMTypeRef>(1)
        paramTypes.put(0, charPtrType)
        LLVMFunctionType(LLVMInt64TypeInContext(context), paramTypes, 1, 0)
    }
    private lateinit var mallocFuncType: LLVMTypeRef
    private lateinit var strcpyFuncType: LLVMTypeRef
    private lateinit var strcatFuncType: LLVMTypeRef
    private lateinit var strncpyFuncType: LLVMTypeRef
    private lateinit var strstrFuncType: LLVMTypeRef
    private lateinit var atoiFuncType: LLVMTypeRef

    private val functions = mutableMapOf<String, FunctionInfo>()
    private val classStructTypes = mutableMapOf<String, LLVMTypeRef>()
    private val classes = mutableMapOf<String, ClassInfo>()

    private var currentFunctionReturnType: Type? = null
    private var currentClassName: String? = null

    private val listStructType: LLVMTypeRef = LLVMStructCreateNamed(context, "List")
    private val loopTargets = mutableListOf<LoopTarget>()
    private val builtins = BuiltinRegistry()
    private val callResolver = CallResolver(builtins)
    private lateinit var programIndex: ProgramIndex
    private val codegenContext = CodegenContext(context, module, builder)
    private val statementEmitter: StatementEmitter = DefaultStatementEmitter(this)
    private val expressionEmitter: ExpressionEmitter = DefaultExpressionEmitter(this)
    private val runtimeDeclEmitter = RuntimeDeclEmitter(this)
    private val declarationEmitter = DeclarationEmitter(this)
    private val builtinCallEmitter = BuiltinCallEmitter(this)
    private val typeLowering = TypeLowering(this)
    private val valueCaster = ValueCaster(this)
    private val builtInFunctions = builtins.names()

    private data class FunctionInfo(
        val name: String,
        val returnType: Type,
        val paramTypes: List<Type>,
        val llvmFunc: LLVMValueRef,
        val llvmType: LLVMTypeRef,
        val ownerClass: String? = null
    )

    private data class ClassFieldInfo(
        val name: String,
        val type: Type,
        val mutable: Boolean,
        val index: Int
    )

    private data class ClassInfo(
        val name: String,
        val structType: LLVMTypeRef,
        val fields: List<ClassFieldInfo>,
        val fieldsByName: Map<String, ClassFieldInfo>,
        val methods: MutableMap<String, FunctionInfo>,
        val methodsAst: MutableMap<String, MethodDef>
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
        LLVMInitializeNativeTarget()
        LLVMInitializeNativeAsmPrinter()
        LLVMInitializeNativeAsmParser()

        runtimeDeclEmitter.declareCoreRuntime()
    }

    private fun fail(location: SourceLocation, message: String): Nothing {
        throw Exception(location.format(message))
    }

    private fun declareListType() {
        val elements = PointerPointer<LLVMTypeRef>(2)
        elements.put(0L, LLVMInt64TypeInContext(context))
        elements.put(1L, charPtrType)
        LLVMStructSetBody(listStructType, elements, 2, 0)
    }

    internal fun declareCoreRuntime() {
        declareListType()
        declarePrintf()
        declareFormatStrings()
    }

    fun generate(program: Program) {
        programIndex = ProgramIndex.from(program)
        val classDefs = program.statements.filterIsInstance<Stmt.ClassDef>()

        declarationEmitter.emitProgramDeclarations(program)

        program.statements.filterIsInstance<Stmt.FunctionDef>()
            .forEach { generateFunctionBody(it) }

        classDefs.forEach { classDef ->
            classDef.methods.forEach { method ->
                generateMethodBody(classDef.name, method)
            }
        }

        val mainFuncType = LLVMFunctionType(
            LLVMInt32TypeInContext(context),
            LLVMTypeRef(),
            0,
            0
        )
        val mainFunction = LLVMAddFunction(module, "main", mainFuncType)
        val entry = LLVMAppendBasicBlockInContext(context, mainFunction, "entry")
        LLVMPositionBuilderAtEnd(builder, entry)

        val previousSymbols = codegenSymbolTable
        codegenSymbolTable = mutableMapOf()

        val previousReturnType = currentFunctionReturnType
        currentFunctionReturnType = Type.INT

        val previousClass = currentClassName
        currentClassName = null

        program.statements.forEach {
            if (it !is Stmt.FunctionDef && it !is Stmt.ClassDef) {
                if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                    statementEmitter.emit(it)
                }
            }
        }

        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildRet(builder, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
        }

        currentFunctionReturnType = previousReturnType
        currentClassName = previousClass
        codegenSymbolTable = previousSymbols
    }

    fun visit(stmt: Stmt) {
        when (stmt) {
            is Stmt.IfStmt -> visit(stmt)
            is Stmt.LetStmt -> visit(stmt)
            is Stmt.VarStmt -> visit(stmt)
            is Stmt.AssignStmt -> visit(stmt)
            is Stmt.MemberAssignStmt -> visit(stmt)
            is Stmt.PrintStmt -> visit(stmt)
            is Stmt.WhileStmt -> visit(stmt)
            is Stmt.ForStmt -> visit(stmt)
            is Stmt.BreakStmt -> visit(stmt)
            is Stmt.ContinueStmt -> visit(stmt)
            is Stmt.FunctionDef -> {}
            is Stmt.ClassDef -> {}
            is Stmt.ReturnStmt -> visit(stmt)
            is Stmt.ExprStmt -> visit(stmt)
        }
    }

    internal fun emitStatement(stmt: Stmt) {
        visit(stmt)
    }

    private fun visit(stmt: Stmt.LetStmt) {
        val rawExprValue = visit(stmt.expr)
        val exprValue = castValueIfNeeded(rawExprValue, inferExprType(stmt.expr), stmt.type)
        val varPtr = LLVMBuildAlloca(builder, llvmTypeFor(stmt.type), stmt.name)
        LLVMBuildStore(builder, exprValue, varPtr)
        codegenSymbolTable[stmt.name] = SymbolInfo(stmt.type, varPtr, immutable = true)
    }

    private fun visit(stmt: Stmt.VarStmt) {
        val rawExprValue = visit(stmt.expr)
        val exprValue = castValueIfNeeded(rawExprValue, inferExprType(stmt.expr), stmt.type)
        val varPtr = LLVMBuildAlloca(builder, llvmTypeFor(stmt.type), stmt.name)
        LLVMBuildStore(builder, exprValue, varPtr)
        codegenSymbolTable[stmt.name] = SymbolInfo(stmt.type, varPtr, immutable = false)
    }

    private fun visit(stmt: Stmt.AssignStmt) {
        val symbol = codegenSymbolTable[stmt.name]
            ?: throw Exception("Undefined variable '${stmt.name}'.")

        if (symbol.immutable) {
            throw Exception("Cannot reassign immutable variable '${stmt.name}'.")
        }

        val rawExprValue = visit(stmt.expr)
        val exprValue = castValueIfNeeded(rawExprValue, inferExprType(stmt.expr), symbol.type)
        LLVMBuildStore(builder, exprValue, symbol.ptr)
    }

    private fun visit(stmt: Stmt.MemberAssignStmt) {
        val targetType = inferExprType(stmt.target)
        val classType = targetType as? Type.CLASS
            ?: fail(stmt.target.location, "Field assignment target must be a class instance.")
        val classInfo = classes[classType.name]
            ?: fail(stmt.target.location, "Unknown class '${classType.name}'.")
        val field = classInfo.fieldsByName[stmt.member]
            ?: fail(stmt.location, "Class '${classType.name}' has no field '${stmt.member}'.")

        if (!field.mutable) {
            fail(stmt.location, "Cannot assign to immutable field '${stmt.member}' in class '${classType.name}'.")
        }

        val targetValue = visit(stmt.target)
        val fieldPtr = LLVMBuildStructGEP2(builder, classInfo.structType, targetValue, field.index, "${stmt.member}_ptr")
        val rawValue = visit(stmt.expr)
        val value = castValueIfNeeded(rawValue, inferExprType(stmt.expr), field.type)
        LLVMBuildStore(builder, value, fieldPtr)
    }

    private fun visit(stmt: Stmt.PrintStmt) {
        emitPrint(stmt.expr, stmt.expr.location)
    }

    private fun emitPrint(expr: Expr, location: SourceLocation) {
        val exprValue = visit(expr)
        val printfFunc = LLVMGetNamedFunction(module, "printf") ?: declarePrintf()
        val exprType = inferExprType(expr)

        if (exprType is Type.LIST) {
            emitPrintList(exprValue, exprType.elementType ?: Type.INT, printfFunc, location)
            return
        }

        if (exprType is Type.CLASS) {
            fail(location, "Printing class instances is not supported in v1.")
        }

        val formatStr = formatStrings[exprType]
            ?: throw Exception("No format string found for type: $exprType")

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

        buildPrintfCall(printfFunc, gepFormatStr, exprValue)
    }

    private fun visit(stmt: Stmt.IfStmt) {
        if (inferExprType(stmt.condition) != Type.BOOL) {
            fail(stmt.condition.location, "If condition must be Bool.")
        }
        val condValue = visit(stmt.condition)

        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)

        val thenBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "then")
        val elseBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "else")
        val mergeBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "merge")

        LLVMBuildCondBr(builder, condValue, thenBlock, elseBlock)

        LLVMPositionBuilderAtEnd(builder, thenBlock)
        stmt.thenBranch.forEach { visit(it) }
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, mergeBlock)
        }

        LLVMPositionBuilderAtEnd(builder, elseBlock)
        stmt.elseBranch?.forEach { visit(it) }
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, mergeBlock)
        }

        LLVMPositionBuilderAtEnd(builder, mergeBlock)
    }

    private fun visit(stmt: Stmt.WhileStmt) {
        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)
        if (inferExprType(stmt.condition) != Type.BOOL) {
            fail(stmt.condition.location, "While condition must be Bool.")
        }

        val condBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_cond")
        val bodyBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_body")
        val afterBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "while_after")

        LLVMBuildBr(builder, condBlock)

        LLVMPositionBuilderAtEnd(builder, condBlock)
        val condValue = visit(stmt.condition)

        LLVMBuildCondBr(builder, condValue, bodyBlock, afterBlock)

        LLVMPositionBuilderAtEnd(builder, bodyBlock)
        loopTargets.add(LoopTarget(continueBlock = condBlock, breakBlock = afterBlock))
        try {
            stmt.body.forEach { visit(it) }
        } finally {
            loopTargets.removeAt(loopTargets.size - 1)
        }
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, condBlock)
        }

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
        val returnValue = stmt.expr?.let { expr ->
            val raw = visit(expr)
            val expected = expectedReturnType
            if (expected != null) {
                castValueIfNeeded(raw, inferExprType(expr), expected)
            } else {
                raw
            }
        }

        if (expectedReturnType == Type.VOID) {
            if (returnValue != null) {
                throw Exception("Void function should not return a value.")
            }
            LLVMBuildRetVoid(builder)
            return
        }

        if (returnValue == null) {
            throw Exception("Non-void function must return a value.")
        }
        LLVMBuildRet(builder, returnValue)
    }

    private fun visit(stmt: Stmt.ExprStmt) {
        when (val expr = stmt.expr) {
            is Expr.Call -> buildCall(expr, allowVoid = true)
            is Expr.MemberCall -> buildMemberCall(expr, allowVoid = true)
            else -> expressionEmitter.emit(expr)
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

    private fun emitPrintList(
        listPtr: LLVMValueRef,
        elementType: Type,
        printfFunc: LLVMValueRef,
        location: SourceLocation
    ) {
        if (elementType is Type.CLASS || elementType is Type.LIST) {
            fail(location, "Printing nested/object lists is not supported in v1 native mode.")
        }

        val lenPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 0, "list_len_ptr")
        val dataPtrPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 1, "list_data_ptr")

        val lenVal = LLVMBuildLoad2(builder, LLVMInt64TypeInContext(context), lenPtr, "list_len")
        val dataPtr = LLVMBuildLoad2(
            builder,
            charPtrType,
            dataPtrPtr,
            "list_data"
        )
        val typedDataPtr = LLVMBuildBitCast(
            builder,
            dataPtr,
            LLVMPointerType(llvmTypeFor(elementType), 0),
            "typed_list_data"
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
            llvmTypeFor(elementType),
            typedDataPtr,
            elemIndices,
            1,
            "list_elem_ptr"
        )
        val elemVal = LLVMBuildLoad2(builder, llvmTypeFor(elementType), elemPtr, "list_elem")

        val formatStr = formatStrings[elementType]
            ?: throw Exception("No format string found for type: $elementType")
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
        val listType = inferExprType(expr) as? Type.LIST
            ?: throw Exception("List literal must infer to a List type.")
        val elementType = listType.elementType ?: Type.INT
        val elementLLVMType = llvmTypeFor(elementType)

        val length = expr.elements.size
        val lenValue = LLVMConstInt(LLVMInt64TypeInContext(context), length.toLong(), 0)
        val dataPtr = if (length == 0) {
            LLVMConstNull(charPtrType)
        } else {
            val arrayType = LLVMArrayType(elementLLVMType, length)
            val arrayAlloca = LLVMBuildAlloca(builder, arrayType, "list_data")
            expr.elements.forEachIndexed { index, element ->
                val rawElemValue = visit(element)
                val elemType = inferExprType(element)
                val elemValue = castValueIfNeeded(rawElemValue, elemType, elementType)
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
            val rawDataPtr = LLVMBuildGEP2(
                builder,
                arrayType,
                arrayAlloca,
                dataIndices,
                2,
                "list_data_ptr"
            )
            LLVMBuildBitCast(builder, rawDataPtr, charPtrType, "list_data_i8")
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
            is Expr.This -> {
                val className = currentClassName
                    ?: throw Exception("'this' is only available inside class methods.")
                Type.CLASS(className)
            }

            is Expr.Variable -> codegenSymbolTable[expr.name]?.type
                ?: throw Exception("Undefined variable during type inference: ${expr.name}")

            is Expr.UnaryOp -> when (expr.operator) {
                "!" -> Type.BOOL
                else -> throw Exception("Unknown unary operator: ${expr.operator}")
            }

            is Expr.Call -> {
                val target = callResolver.resolve(expr.name, programIndex, expr.location) { location, message ->
                    throw Exception(location.format(message))
                }
                when (target) {
                    is CallResolver.CallTarget.Builtin -> inferBuiltInCallType(expr)
                    is CallResolver.CallTarget.Constructor -> Type.CLASS(target.className)
                    is CallResolver.CallTarget.Function -> target.returnType
                }
            }

            is Expr.MemberAccess -> {
                val receiverType = inferExprType(expr.receiver)
                val className = (receiverType as? Type.CLASS)?.name
                    ?: throw Exception("Member access requires class instance receiver.")
                val classInfo = classes[className]
                    ?: throw Exception("Unknown class '$className'.")
                classInfo.fieldsByName[expr.member]?.type
                    ?: throw Exception("Class '$className' has no field '${expr.member}'.")
            }

            is Expr.MemberCall -> {
                val receiverType = inferExprType(expr.receiver)
                val className = (receiverType as? Type.CLASS)?.name
                    ?: throw Exception("Method call requires class instance receiver.")
                val classInfo = classes[className]
                    ?: throw Exception("Unknown class '$className'.")
                classInfo.methods[expr.method]?.returnType
                    ?: throw Exception("Class '$className' has no method '${expr.method}'.")
            }

            is Expr.ListLiteral -> inferListLiteralType(expr)
            is Expr.Index -> {
                val targetType = inferExprType(expr.target) as? Type.LIST
                    ?: throw Exception("Indexing is only supported on List values.")
                targetType.elementType ?: Type.INT
            }
            is Expr.BinaryOp -> when (expr.operator) {
                ">", "<", ">=", "<=", "==", "!=", "&&", "||" -> Type.BOOL
                "+" -> {
                    val leftType = inferExprType(expr.left)
                    val rightType = inferExprType(expr.right)
                    when {
                        leftType == Type.STRING && rightType == Type.STRING -> Type.STRING
                        leftType == Type.INT && rightType == Type.INT -> Type.INT
                        isNumeric(leftType) && isNumeric(rightType) -> Type.FLOAT
                        else -> throw Exception("Unsupported operand types for '+': $leftType and $rightType.")
                    }
                }

                "-", "*", "/" -> {
                    val leftType = inferExprType(expr.left)
                    val rightType = inferExprType(expr.right)
                    when {
                        leftType == Type.INT && rightType == Type.INT -> Type.INT
                        isNumeric(leftType) && isNumeric(rightType) -> Type.FLOAT
                        else -> throw Exception("Unsupported operand types for '${expr.operator}': $leftType and $rightType.")
                    }
                }

                "%" -> Type.INT
                else -> Type.INT
            }
        }
    }

    private fun inferListLiteralType(expr: Expr.ListLiteral): Type {
        if (expr.elements.isEmpty()) {
            return Type.LIST(Type.INT)
        }

        var elementType = inferExprType(expr.elements.first())
        expr.elements.drop(1).forEach { element ->
            val nextType = inferExprType(element)
            if (elementType == nextType) {
                return@forEach
            }
            if (isNumeric(elementType) && isNumeric(nextType)) {
                elementType = Type.FLOAT
                return@forEach
            }
            throw Exception(
                "List literal element types must be homogeneous. Found $elementType and $nextType."
            )
        }
        return Type.LIST(elementType)
    }

    private fun inferBuiltInCallType(expr: Expr.Call): Type {
        return when (expr.name) {
            "len" -> {
                if (expr.args.size != 1) {
                    throw Exception("len expects 1 argument, got ${expr.args.size}.")
                }
                Type.INT
            }

            "substr" -> {
                if (expr.args.size != 3) {
                    throw Exception("substr expects 3 arguments, got ${expr.args.size}.")
                }
                Type.STRING
            }

            "contains" -> {
                if (expr.args.size != 2) {
                    throw Exception("contains expects 2 arguments, got ${expr.args.size}.")
                }
                Type.BOOL
            }

            "to_int" -> {
                if (expr.args.size != 1) {
                    throw Exception("to_int expects 1 argument, got ${expr.args.size}.")
                }
                Type.INT
            }

            "min", "max" -> {
                if (expr.args.size != 2) {
                    throw Exception("${expr.name} expects 2 arguments, got ${expr.args.size}.")
                }
                val left = inferExprType(expr.args[0])
                val right = inferExprType(expr.args[1])
                if (!isNumeric(left) || !isNumeric(right)) {
                    throw Exception("${expr.name} expects numeric arguments.")
                }
                if (left == Type.INT && right == Type.INT) Type.INT else Type.FLOAT
            }

            "abs" -> {
                if (expr.args.size != 1) {
                    throw Exception("abs expects 1 argument, got ${expr.args.size}.")
                }
                val argType = inferExprType(expr.args[0])
                if (!isNumeric(argType)) {
                    throw Exception("abs expects a numeric argument.")
                }
                argType
            }
            else -> throw Exception("Unknown built-in function '${expr.name}'.")
        }
    }

    fun visit(expr: Expr): LLVMValueRef {
        return when (expr) {
            is Expr.Number -> LLVMConstInt(LLVMInt32TypeInContext(context), expr.value.toLong(), 0)
            is Expr.FloatLiteral -> LLVMConstReal(LLVMFloatTypeInContext(context), expr.value.toDouble())
            is Expr.BoolLiteral -> LLVMConstInt(LLVMInt1TypeInContext(context), if (expr.value) 1 else 0, 0)
            is Expr.StringLiteral -> LLVMBuildGlobalStringPtr(builder, expr.value, "str")
            is Expr.This -> {
                val symbol = codegenSymbolTable["this"]
                    ?: throw Exception("'this' is only available inside class methods.")
                LLVMBuildLoad2(builder, llvmTypeFor(symbol.type), symbol.ptr, "this")
            }

            is Expr.Variable -> {
                val symbol = codegenSymbolTable[expr.name]
                    ?: throw Exception("Undefined variable during code generation: ${expr.name}")
                LLVMBuildLoad2(builder, llvmTypeFor(symbol.type), symbol.ptr, expr.name)
            }

            is Expr.UnaryOp -> {
                val operand = visit(expr.operand)
                when (expr.operator) {
                    "!" -> {
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
            is Expr.MemberAccess -> buildMemberAccess(expr)
            is Expr.MemberCall -> buildMemberCall(expr, allowVoid = false)
            is Expr.ListLiteral -> buildListLiteral(expr)
            is Expr.Index -> buildIndexExpr(expr)

            is Expr.BinaryOp -> {
                if (expr.operator == "&&" || expr.operator == "||") {
                    return buildShortCircuitLogical(expr)
                }

                var left = visit(expr.left)
                var right = visit(expr.right)
                val leftType = inferExprType(expr.left)
                val rightType = inferExprType(expr.right)

                if (expr.operator == "+" && leftType == Type.STRING && rightType == Type.STRING) {
                    return buildStringConcat(left, right)
                }

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

    internal fun emitExpression(expr: Expr): LLVMValueRef {
        return visit(expr)
    }

    private fun buildMemberAccess(expr: Expr.MemberAccess): LLVMValueRef {
        val receiverType = inferExprType(expr.receiver)
        val classType = receiverType as? Type.CLASS
            ?: throw Exception("Member access requires class instance receiver.")
        val classInfo = classes[classType.name]
            ?: throw Exception("Unknown class '${classType.name}'.")
        val field = classInfo.fieldsByName[expr.member]
            ?: throw Exception("Class '${classType.name}' has no field '${expr.member}'.")

        val receiverValue = visit(expr.receiver)
        val fieldPtr = LLVMBuildStructGEP2(builder, classInfo.structType, receiverValue, field.index, "${expr.member}_ptr")
        return LLVMBuildLoad2(builder, llvmTypeFor(field.type), fieldPtr, expr.member)
    }

    private fun buildShortCircuitLogical(expr: Expr.BinaryOp): LLVMValueRef {
        val currentBlock = LLVMGetInsertBlock(builder)
        val parentFunction = LLVMGetBasicBlockParent(currentBlock)

        if (inferExprType(expr.left) != Type.BOOL || inferExprType(expr.right) != Type.BOOL) {
            throw Exception("Logical operators require Bool operands.")
        }

        val rightBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "logical_right")
        val mergeBlock = LLVMAppendBasicBlockInContext(context, parentFunction, "logical_merge")

        val leftValue = visit(expr.left)
        val leftEndBlock = LLVMGetInsertBlock(builder)

        if (expr.operator == "&&") {
            LLVMBuildCondBr(builder, leftValue, rightBlock, mergeBlock)
        } else {
            LLVMBuildCondBr(builder, leftValue, mergeBlock, rightBlock)
        }

        LLVMPositionBuilderAtEnd(builder, rightBlock)
        val rightValue = visit(expr.right)
        val rightEndBlock = LLVMGetInsertBlock(builder)
        LLVMBuildBr(builder, mergeBlock)

        LLVMPositionBuilderAtEnd(builder, mergeBlock)
        val phi = LLVMBuildPhi(builder, LLVMInt1TypeInContext(context), "logical_result")

        val incomingValues = PointerPointer<LLVMValueRef>(2)
        val incomingBlocks = PointerPointer<LLVMBasicBlockRef>(2)

        if (expr.operator == "&&") {
            incomingValues.put(0, LLVMConstInt(LLVMInt1TypeInContext(context), 0, 0))
            incomingBlocks.put(0, leftEndBlock)
            incomingValues.put(1, rightValue)
            incomingBlocks.put(1, rightEndBlock)
        } else {
            incomingValues.put(0, LLVMConstInt(LLVMInt1TypeInContext(context), 1, 0))
            incomingBlocks.put(0, leftEndBlock)
            incomingValues.put(1, rightValue)
            incomingBlocks.put(1, rightEndBlock)
        }

        LLVMAddIncoming(phi, incomingValues, incomingBlocks, 2)
        return phi
    }

    private fun buildCall(expr: Expr.Call, allowVoid: Boolean): LLVMValueRef {
        if (expr.name in builtInFunctions) {
            return builtinCallEmitter.emit(expr)
        }

        classes[expr.name]?.let { classInfo ->
            return buildClassConstructorCall(classInfo, expr)
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
            val rawArg = visit(arg)
            val coercedArg = castValueIfNeeded(rawArg, inferExprType(arg), info.paramTypes[index])
            args.put(index.toLong(), coercedArg)
        }

        val callValue = LLVMBuildCall2(
            builder,
            info.llvmType,
            info.llvmFunc,
            args,
            expr.args.size,
            if (info.returnType == Type.VOID) "" else "calltmp"
        ) ?: throw Exception("Failed to build call for function '${expr.name}'.")
        return callValue
    }

    private fun buildBuiltInCall(expr: Expr.Call): LLVMValueRef {
        return when (expr.name) {
            "len" -> {
                if (expr.args.size != 1) {
                    fail(expr.location, "len expects 1 argument, got ${expr.args.size}.")
                }
                val argType = inferExprType(expr.args[0])
                val argValue = visit(expr.args[0])
                return when (argType) {
                    is Type.LIST -> buildListLength(argValue)
                    Type.STRING -> buildStringLength(argValue)
                    else -> fail(expr.args[0].location, "len expects a List or String argument.")
                }
            }

            "substr" -> buildSubstrCall(expr)
            "contains" -> buildContainsCall(expr)
            "to_int" -> buildToIntCall(expr)
            "min" -> buildMinOrMaxCall(expr, findMin = true)
            "max" -> buildMinOrMaxCall(expr, findMin = false)
            "abs" -> buildAbsCall(expr)
            else -> throw Exception("Unknown built-in function '${expr.name}'.")
        }
    }

    internal fun emitBuiltinCall(expr: Expr.Call): LLVMValueRef {
        return buildBuiltInCall(expr)
    }

    private fun buildMemberCall(expr: Expr.MemberCall, allowVoid: Boolean): LLVMValueRef {
        val receiverType = inferExprType(expr.receiver)
        val classType = receiverType as? Type.CLASS
            ?: throw Exception("Method call requires class instance receiver.")
        val classInfo = classes[classType.name]
            ?: throw Exception("Unknown class '${classType.name}'.")
        val methodInfo = classInfo.methods[expr.method]
            ?: throw Exception("Class '${classType.name}' has no method '${expr.method}'.")

        if (expr.args.size != methodInfo.paramTypes.size) {
            throw Exception("Method '${expr.method}' expects ${methodInfo.paramTypes.size} arguments, got ${expr.args.size}.")
        }
        if (!allowVoid && methodInfo.returnType == Type.VOID) {
            throw Exception("Cannot use void method '${expr.method}' in an expression.")
        }

        val args = PointerPointer<LLVMValueRef>((expr.args.size + 1).toLong())
        args.put(0L, visit(expr.receiver))
        expr.args.forEachIndexed { index, arg ->
            val rawArg = visit(arg)
            val coercedArg = castValueIfNeeded(rawArg, inferExprType(arg), methodInfo.paramTypes[index])
            args.put((index + 1).toLong(), coercedArg)
        }

        val callValue = LLVMBuildCall2(
            builder,
            methodInfo.llvmType,
            methodInfo.llvmFunc,
            args,
            expr.args.size + 1,
            if (methodInfo.returnType == Type.VOID) "" else "method_call"
        ) ?: throw Exception("Failed to build call for method '${expr.method}'.")

        return callValue
    }

    private fun buildSubstrCall(expr: Expr.Call): LLVMValueRef {
        if (expr.args.size != 3) {
            fail(expr.location, "substr expects 3 arguments, got ${expr.args.size}.")
        }

        val sourceType = inferExprType(expr.args[0])
        val startType = inferExprType(expr.args[1])
        val lengthType = inferExprType(expr.args[2])
        if (sourceType != Type.STRING || startType != Type.INT || lengthType != Type.INT) {
            fail(expr.location, "substr expects (String, Int, Int).")
        }

        val source = visit(expr.args[0])
        val start = visit(expr.args[1])
        val length = visit(expr.args[2])

        val strncpyFn = LLVMGetNamedFunction(module, "strncpy") ?: declareStrncpy()
        val mallocFn = LLVMGetNamedFunction(module, "malloc") ?: declareMalloc()

        val start64 = LLVMBuildSExt(builder, start, LLVMInt64TypeInContext(context), "substr_start_64")
        val length64 = LLVMBuildSExt(builder, length, LLVMInt64TypeInContext(context), "substr_len_64")

        val sourceOffsetIndices = PointerPointer<LLVMValueRef>(1).apply { put(0, start64) }
        val sourceSlice = LLVMBuildGEP2(
            builder,
            LLVMInt8TypeInContext(context),
            source,
            sourceOffsetIndices,
            1,
            "substr_slice_start"
        )

        val allocLength = LLVMBuildAdd(
            builder,
            length64,
            LLVMConstInt(LLVMInt64TypeInContext(context), 1, 0),
            "substr_alloc_len"
        )
        val mallocArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, allocLength) }
        val outBuffer = LLVMBuildCall2(
            builder,
            mallocFuncType,
            mallocFn,
            mallocArgs,
            1,
            "substr_out_buf"
        ) ?: throw Exception("Failed to allocate substr buffer.")

        val strncpyArgs = PointerPointer<LLVMValueRef>(3).apply {
            put(0, outBuffer)
            put(1, sourceSlice)
            put(2, length64)
        }
        LLVMBuildCall2(
            builder,
            strncpyFuncType,
            strncpyFn,
            strncpyArgs,
            3,
            "strncpy_call"
        ) ?: throw Exception("Failed to build strncpy call for substr.")

        val nullTermIndices = PointerPointer<LLVMValueRef>(1).apply { put(0, length64) }
        val nullTermPtr = LLVMBuildGEP2(
            builder,
            LLVMInt8TypeInContext(context),
            outBuffer,
            nullTermIndices,
            1,
            "substr_null_term_ptr"
        )
        LLVMBuildStore(builder, LLVMConstInt(LLVMInt8TypeInContext(context), 0, 0), nullTermPtr)
        return outBuffer
    }

    private fun buildContainsCall(expr: Expr.Call): LLVMValueRef {
        if (expr.args.size != 2) {
            fail(expr.location, "contains expects 2 arguments, got ${expr.args.size}.")
        }
        val haystackType = inferExprType(expr.args[0])
        val needleType = inferExprType(expr.args[1])
        if (haystackType != Type.STRING || needleType != Type.STRING) {
            fail(expr.location, "contains expects (String, String).")
        }

        val haystack = visit(expr.args[0])
        val needle = visit(expr.args[1])
        val strstrFn = LLVMGetNamedFunction(module, "strstr") ?: declareStrstr()
        val args = PointerPointer<LLVMValueRef>(2).apply {
            put(0, haystack)
            put(1, needle)
        }
        val result = LLVMBuildCall2(
            builder,
            strstrFuncType,
            strstrFn,
            args,
            2,
            "strstr_call"
        ) ?: throw Exception("Failed to build call to strstr.")
        return LLVMBuildICmp(
            builder,
            LLVMIntNE,
            result,
            LLVMConstNull(charPtrType),
            "contains_result"
        )
    }

    private fun buildToIntCall(expr: Expr.Call): LLVMValueRef {
        if (expr.args.size != 1) {
            fail(expr.location, "to_int expects 1 argument, got ${expr.args.size}.")
        }
        val argType = inferExprType(expr.args[0])
        if (argType != Type.STRING) {
            fail(expr.location, "to_int expects a String argument.")
        }

        val atoiFn = LLVMGetNamedFunction(module, "atoi") ?: declareAtoi()
        val arg = visit(expr.args[0])
        val args = PointerPointer<LLVMValueRef>(1).apply { put(0, arg) }
        return LLVMBuildCall2(
            builder,
            atoiFuncType,
            atoiFn,
            args,
            1,
            "atoi_call"
        ) ?: throw Exception("Failed to build call to atoi.")
    }

    private fun buildMinOrMaxCall(expr: Expr.Call, findMin: Boolean): LLVMValueRef {
        if (expr.args.size != 2) {
            fail(expr.location, "${expr.name} expects 2 arguments, got ${expr.args.size}.")
        }

        val leftType = inferExprType(expr.args[0])
        val rightType = inferExprType(expr.args[1])
        if (!isNumeric(leftType) || !isNumeric(rightType)) {
            fail(expr.location, "${expr.name} expects numeric arguments.")
        }

        var left = visit(expr.args[0])
        var right = visit(expr.args[1])
        val bothInt = leftType == Type.INT && rightType == Type.INT
        if (!bothInt) {
            left = castValueIfNeeded(left, leftType, Type.FLOAT)
            right = castValueIfNeeded(right, rightType, Type.FLOAT)
            val predicate = if (findMin) LLVMRealOLT else LLVMRealOGT
            val cmp = LLVMBuildFCmp(builder, predicate, left, right, "minmax_cmp")
            return LLVMBuildSelect(builder, cmp, left, right, "minmax_float")
        }

        val predicate = if (findMin) LLVMIntSLT else LLVMIntSGT
        val cmp = LLVMBuildICmp(builder, predicate, left, right, "minmax_cmp")
        return LLVMBuildSelect(builder, cmp, left, right, "minmax_int")
    }

    private fun buildAbsCall(expr: Expr.Call): LLVMValueRef {
        if (expr.args.size != 1) {
            fail(expr.location, "abs expects 1 argument, got ${expr.args.size}.")
        }

        val argType = inferExprType(expr.args[0])
        val arg = visit(expr.args[0])
        return when (argType) {
            Type.INT -> {
                val zero = LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0)
                val isNegative = LLVMBuildICmp(builder, LLVMIntSLT, arg, zero, "abs_int_is_neg")
                val negated = LLVMBuildNeg(builder, arg, "abs_int_neg")
                LLVMBuildSelect(builder, isNegative, negated, arg, "abs_int")
            }

            Type.FLOAT -> {
                val zero = LLVMConstReal(LLVMFloatTypeInContext(context), 0.0)
                val isNegative = LLVMBuildFCmp(builder, LLVMRealOLT, arg, zero, "abs_float_is_neg")
                val negated = LLVMBuildFNeg(builder, arg, "abs_float_neg")
                LLVMBuildSelect(builder, isNegative, negated, arg, "abs_float")
            }

            else -> fail(expr.location, "abs expects a numeric argument.")
        }
    }

    private fun buildClassConstructorCall(classInfo: ClassInfo, expr: Expr.Call): LLVMValueRef {
        if (expr.args.size != classInfo.fields.size) {
            fail(expr.location, "Constructor '${classInfo.name}' expects ${classInfo.fields.size} arguments, got ${expr.args.size}.")
        }

        val mallocFn = LLVMGetNamedFunction(module, "malloc") ?: declareMalloc()
        val sizeValue = LLVMSizeOf(classInfo.structType)
        val mallocArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, sizeValue) }
        val rawPtr = LLVMBuildCall2(
            builder,
            mallocFuncType,
            mallocFn,
            mallocArgs,
            1,
            "malloc_obj"
        ) ?: throw Exception("Failed to build call to malloc.")

        val objPtrType = LLVMPointerType(classInfo.structType, 0)
        val objPtr = LLVMBuildBitCast(builder, rawPtr, objPtrType, "obj_ptr")

        classInfo.fields.forEachIndexed { index, field ->
            val fieldPtr = LLVMBuildStructGEP2(builder, classInfo.structType, objPtr, index, "${field.name}_ptr")
            val rawValue = visit(expr.args[index])
            val value = castValueIfNeeded(rawValue, inferExprType(expr.args[index]), field.type)
            LLVMBuildStore(builder, value, fieldPtr)
        }

        return objPtr
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

    private fun buildStringConcat(left: LLVMValueRef, right: LLVMValueRef): LLVMValueRef {
        val strlenFn = LLVMGetNamedFunction(module, "strlen") ?: declareStrlen()
        val mallocFn = LLVMGetNamedFunction(module, "malloc") ?: declareMalloc()
        val strcpyFn = LLVMGetNamedFunction(module, "strcpy") ?: declareStrcpy()
        val strcatFn = LLVMGetNamedFunction(module, "strcat") ?: declareStrcat()

        val lenLeftArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, left) }
        val lenLeft = LLVMBuildCall2(
            builder,
            strlenFuncType,
            strlenFn,
            lenLeftArgs,
            1,
            "left_len"
        ) ?: throw Exception("Failed to build strlen call for left string.")

        val lenRightArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, right) }
        val lenRight = LLVMBuildCall2(
            builder,
            strlenFuncType,
            strlenFn,
            lenRightArgs,
            1,
            "right_len"
        ) ?: throw Exception("Failed to build strlen call for right string.")

        val totalLen = LLVMBuildAdd(builder, lenLeft, lenRight, "concat_len")
        val allocLen = LLVMBuildAdd(
            builder,
            totalLen,
            LLVMConstInt(LLVMInt64TypeInContext(context), 1, 0),
            "concat_alloc_len"
        )

        val mallocArgs = PointerPointer<LLVMValueRef>(1).apply { put(0, allocLen) }
        val concatPtr = LLVMBuildCall2(
            builder,
            mallocFuncType,
            mallocFn,
            mallocArgs,
            1,
            "concat_buf"
        ) ?: throw Exception("Failed to allocate memory for string concatenation.")

        val copyArgs = PointerPointer<LLVMValueRef>(2).apply {
            put(0, concatPtr)
            put(1, left)
        }
        LLVMBuildCall2(
            builder,
            strcpyFuncType,
            strcpyFn,
            copyArgs,
            2,
            "strcpy_call"
        ) ?: throw Exception("Failed to build strcpy call for string concatenation.")

        val appendArgs = PointerPointer<LLVMValueRef>(2).apply {
            put(0, concatPtr)
            put(1, right)
        }
        LLVMBuildCall2(
            builder,
            strcatFuncType,
            strcatFn,
            appendArgs,
            2,
            "strcat_call"
        ) ?: throw Exception("Failed to build strcat call for string concatenation.")

        return concatPtr
    }

    private fun buildIndexExpr(expr: Expr.Index): LLVMValueRef {
        val targetType = inferExprType(expr.target) as? Type.LIST
            ?: fail(expr.target.location, "Indexing is only supported on List values.")
        val elementType = targetType.elementType ?: Type.INT

        val listPtr = visit(expr.target)
        val indexValue = visit(expr.index)
        val dataPtrPtr = LLVMBuildStructGEP2(builder, listStructType, listPtr, 1, "list_data_ptr")
        val dataPtrI8 = LLVMBuildLoad2(
            builder,
            charPtrType,
            dataPtrPtr,
            "list_data"
        )
        val dataPtr = LLVMBuildBitCast(
            builder,
            dataPtrI8,
            LLVMPointerType(llvmTypeFor(elementType), 0),
            "typed_list_data"
        )
        val elemIndices = PointerPointer<LLVMValueRef>(1).apply {
            put(0, indexValue)
        }
        val elemPtr = LLVMBuildGEP2(
            builder,
            llvmTypeFor(elementType),
            dataPtr,
            elemIndices,
            1,
            "list_elem_ptr"
        )
        return LLVMBuildLoad2(builder, llvmTypeFor(elementType), elemPtr, "list_elem")
    }

    private fun declareClassTypes(classDefs: List<Stmt.ClassDef>) {
        classDefs.forEach { classDef ->
            if (classStructTypes.containsKey(classDef.name)) {
                throw Exception("Class '${classDef.name}' already declared.")
            }
            classStructTypes[classDef.name] = LLVMStructCreateNamed(context, "Class_${classDef.name}")
        }

        classDefs.forEach { classDef ->
            val structType = classStructTypes[classDef.name]
                ?: throw Exception("Missing struct type for class '${classDef.name}'.")

            val fieldInfos = classDef.fields.mapIndexed { index, field ->
                ClassFieldInfo(field.name, field.type, field.mutable, index)
            }

            val fieldTypesPtr = if (fieldInfos.isEmpty()) {
                PointerPointer<LLVMTypeRef>(0)
            } else {
                PointerPointer<LLVMTypeRef>(fieldInfos.size.toLong()).also { ptr ->
                    fieldInfos.forEachIndexed { index, field ->
                        ptr.put(index.toLong(), llvmTypeFor(field.type))
                    }
                }
            }

            LLVMStructSetBody(structType, fieldTypesPtr, fieldInfos.size, 0)

            val fieldsByName = fieldInfos.associateBy { it.name }
            classes[classDef.name] = ClassInfo(
                name = classDef.name,
                structType = structType,
                fields = fieldInfos,
                fieldsByName = fieldsByName,
                methods = mutableMapOf(),
                methodsAst = mutableMapOf()
            )
        }
    }

    internal fun declareClassTypesPhase(classDefs: List<Stmt.ClassDef>) {
        declareClassTypes(classDefs)
    }

    private fun declareMethod(className: String, method: MethodDef) {
        val classInfo = classes[className]
            ?: throw Exception("Unknown class '$className'.")
        if (classInfo.methods.containsKey(method.name)) {
            throw Exception("Method '$className.${method.name}' already declared.")
        }

        val returnType = llvmTypeFor(method.returnType)
        val paramTypes = mutableListOf<Type>()
        paramTypes.add(Type.CLASS(className))
        paramTypes.addAll(method.params.map { it.type })

        val llvmParamTypes = if (paramTypes.isEmpty()) {
            PointerPointer<LLVMTypeRef>(0)
        } else {
            PointerPointer<LLVMTypeRef>(paramTypes.size.toLong()).also { ptr ->
                paramTypes.forEachIndexed { index, type ->
                    ptr.put(index.toLong(), llvmTypeFor(type))
                }
            }
        }

        val fnType = LLVMFunctionType(returnType, llvmParamTypes, paramTypes.size, 0)
        val mangledName = "${className}__${method.name}"
        val fn = LLVMAddFunction(module, mangledName, fnType)

        val info = FunctionInfo(
            name = mangledName,
            returnType = method.returnType,
            paramTypes = method.params.map { it.type },
            llvmFunc = fn,
            llvmType = fnType,
            ownerClass = className
        )

        classInfo.methods[method.name] = info
        classInfo.methodsAst[method.name] = method
        functions[mangledName] = info
    }

    internal fun declareMethodPhase(className: String, method: MethodDef) {
        declareMethod(className, method)
    }

    private fun declareFunction(stmt: Stmt.FunctionDef) {
        if (functions.containsKey(stmt.name)) {
            throw Exception("Function '${stmt.name}' already declared.")
        }
        if (stmt.name in builtInFunctions) {
            throw Exception("Cannot redefine built-in function '${stmt.name}'.")
        }
        if (classes.containsKey(stmt.name)) {
            throw Exception("Function '${stmt.name}' conflicts with class '${stmt.name}'.")
        }

        val returnType = llvmTypeFor(stmt.returnType)
        val paramTypes = stmt.params.map { llvmTypeFor(it.type) }

        val paramPointer = if (paramTypes.isEmpty()) {
            PointerPointer<LLVMTypeRef>(0)
        } else {
            PointerPointer<LLVMTypeRef>(paramTypes.size.toLong()).also { paramsPtr ->
                paramTypes.forEachIndexed { index, llvmType -> paramsPtr.put(index.toLong(), llvmType) }
            }
        }

        val fnType = LLVMFunctionType(returnType, paramPointer, paramTypes.size, 0)
        val fn = LLVMAddFunction(module, stmt.name, fnType)
        functions[stmt.name] = FunctionInfo(
            name = stmt.name,
            returnType = stmt.returnType,
            paramTypes = stmt.params.map { it.type },
            llvmFunc = fn,
            llvmType = fnType
        )
    }

    internal fun declareFunctionPhase(stmt: Stmt.FunctionDef) {
        declareFunction(stmt)
    }

    private fun generateMethodBody(className: String, method: MethodDef) {
        val classInfo = classes[className]
            ?: throw Exception("Unknown class '$className'.")
        val info = classInfo.methods[method.name]
            ?: throw Exception("Missing method info for '$className.${method.name}'.")

        val entry = LLVMAppendBasicBlockInContext(context, info.llvmFunc, "entry")
        LLVMPositionBuilderAtEnd(builder, entry)

        val previousReturnType = currentFunctionReturnType
        currentFunctionReturnType = method.returnType

        val previousClassName = currentClassName
        currentClassName = className

        val previousSymbols = codegenSymbolTable
        codegenSymbolTable = mutableMapOf()

        val selfValue = LLVMGetParam(info.llvmFunc, 0)
        val selfPtr = LLVMBuildAlloca(builder, llvmTypeFor(Type.CLASS(className)), "this")
        LLVMBuildStore(builder, selfValue, selfPtr)
        codegenSymbolTable["this"] = SymbolInfo(Type.CLASS(className), selfPtr, immutable = true)

        method.params.forEachIndexed { index, param ->
            val paramValue = LLVMGetParam(info.llvmFunc, index + 1)
            val paramPtr = LLVMBuildAlloca(builder, llvmTypeFor(param.type), param.name)
            LLVMBuildStore(builder, paramValue, paramPtr)
            codegenSymbolTable[param.name] = SymbolInfo(param.type, paramPtr, immutable = false)
        }

        method.body.forEach { statement ->
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                visit(statement)
            }
        }

        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            buildDefaultReturn(method.returnType)
        }

        currentFunctionReturnType = previousReturnType
        currentClassName = previousClassName
        codegenSymbolTable = previousSymbols
    }

    private fun generateFunctionBody(stmt: Stmt.FunctionDef) {
        val info = functions[stmt.name] ?: throw Exception("Missing function info for '${stmt.name}'.")
        val entry = LLVMAppendBasicBlockInContext(context, info.llvmFunc, "entry")
        LLVMPositionBuilderAtEnd(builder, entry)

        val previousReturnType = currentFunctionReturnType
        currentFunctionReturnType = stmt.returnType

        val previousClassName = currentClassName
        currentClassName = null

        val previousSymbols = codegenSymbolTable
        codegenSymbolTable = mutableMapOf()

        stmt.params.forEachIndexed { index, param ->
            val paramValue = LLVMGetParam(info.llvmFunc, index)
            val paramPtr = LLVMBuildAlloca(builder, llvmTypeFor(param.type), param.name)
            LLVMBuildStore(builder, paramValue, paramPtr)
            codegenSymbolTable[param.name] = SymbolInfo(param.type, paramPtr, immutable = false)
        }

        stmt.body.forEach { statement ->
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                visit(statement)
            }
        }

        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            buildDefaultReturn(stmt.returnType)
        }

        currentFunctionReturnType = previousReturnType
        currentClassName = previousClassName
        codegenSymbolTable = previousSymbols
    }

    private fun buildDefaultReturn(type: Type) {
        when (type) {
            Type.VOID -> LLVMBuildRetVoid(builder)
            Type.INT -> LLVMBuildRet(builder, LLVMConstInt(LLVMInt32TypeInContext(context), 0, 0))
            Type.FLOAT -> LLVMBuildRet(builder, LLVMConstReal(LLVMFloatTypeInContext(context), 0.0))
            Type.BOOL -> LLVMBuildRet(builder, LLVMConstInt(LLVMInt1TypeInContext(context), 0, 0))
            Type.STRING -> LLVMBuildRet(builder, LLVMConstNull(LLVMPointerType(LLVMInt8TypeInContext(context), 0)))
            is Type.LIST -> LLVMBuildRet(builder, LLVMConstNull(LLVMPointerType(listStructType, 0)))
            is Type.CLASS -> {
                val structType = classStructTypes[type.name]
                    ?: throw Exception("Unknown class '${type.name}'.")
                LLVMBuildRet(builder, LLVMConstNull(LLVMPointerType(structType, 0)))
            }
        }
    }

    private fun llvmTypeFor(type: Type): LLVMTypeRef {
        return when (type) {
            Type.INT -> LLVMInt32TypeInContext(context)
            Type.FLOAT -> LLVMFloatTypeInContext(context)
            Type.BOOL -> LLVMInt1TypeInContext(context)
            Type.STRING -> LLVMPointerType(LLVMInt8TypeInContext(context), 0)
            is Type.LIST -> LLVMPointerType(listStructType, 0)
            Type.VOID -> LLVMVoidTypeInContext(context)
            is Type.CLASS -> {
                val structType = classStructTypes[type.name]
                    ?: throw Exception("Unknown class '${type.name}'.")
                LLVMPointerType(structType, 0)
            }
        }
    }

    internal fun lowerType(type: Type): LLVMTypeRef {
        return llvmTypeFor(type)
    }

    private fun declarePrintf(): LLVMValueRef {
        val printfReturnType = LLVMInt32TypeInContext(context)
        val printfParamType = LLVMPointerType(LLVMInt8TypeInContext(context), 0)

        val printfParamTypes = PointerPointer<LLVMTypeRef>(1)
        printfParamTypes.put(0, printfParamType)

        printfFuncType = LLVMFunctionType(
            printfReturnType,
            printfParamTypes,
            1,
            1
        )

        return LLVMAddFunction(module, "printf", printfFuncType)
    }

    private fun declareStrlen(): LLVMValueRef {
        return LLVMAddFunction(module, "strlen", strlenFuncType)
    }

    private fun declareMalloc(): LLVMValueRef {
        val mallocParamTypes = PointerPointer<LLVMTypeRef>(1)
        mallocParamTypes.put(0, LLVMInt64TypeInContext(context))
        mallocFuncType = LLVMFunctionType(
            charPtrType,
            mallocParamTypes,
            1,
            0
        )
        return LLVMAddFunction(module, "malloc", mallocFuncType)
    }

    private fun declareStrcpy(): LLVMValueRef {
        val paramTypes = PointerPointer<LLVMTypeRef>(2)
        paramTypes.put(0, charPtrType)
        paramTypes.put(1, charPtrType)
        strcpyFuncType = LLVMFunctionType(charPtrType, paramTypes, 2, 0)
        return LLVMAddFunction(module, "strcpy", strcpyFuncType)
    }

    private fun declareStrcat(): LLVMValueRef {
        val paramTypes = PointerPointer<LLVMTypeRef>(2)
        paramTypes.put(0, charPtrType)
        paramTypes.put(1, charPtrType)
        strcatFuncType = LLVMFunctionType(charPtrType, paramTypes, 2, 0)
        return LLVMAddFunction(module, "strcat", strcatFuncType)
    }

    private fun declareStrncpy(): LLVMValueRef {
        val paramTypes = PointerPointer<LLVMTypeRef>(3)
        paramTypes.put(0, charPtrType)
        paramTypes.put(1, charPtrType)
        paramTypes.put(2, LLVMInt64TypeInContext(context))
        strncpyFuncType = LLVMFunctionType(charPtrType, paramTypes, 3, 0)
        return LLVMAddFunction(module, "strncpy", strncpyFuncType)
    }

    private fun declareStrstr(): LLVMValueRef {
        val paramTypes = PointerPointer<LLVMTypeRef>(2)
        paramTypes.put(0, charPtrType)
        paramTypes.put(1, charPtrType)
        strstrFuncType = LLVMFunctionType(charPtrType, paramTypes, 2, 0)
        return LLVMAddFunction(module, "strstr", strstrFuncType)
    }

    private fun declareAtoi(): LLVMValueRef {
        val paramTypes = PointerPointer<LLVMTypeRef>(1)
        paramTypes.put(0, charPtrType)
        atoiFuncType = LLVMFunctionType(LLVMInt32TypeInContext(context), paramTypes, 1, 0)
        return LLVMAddFunction(module, "atoi", atoiFuncType)
    }

    private fun declareFormatStrings() {
        fun createGlobalString(name: String, value: String): LLVMValueRef {
            val strLength = value.length
            val arrayType = LLVMArrayType(LLVMInt8TypeInContext(context), strLength + 1)
            val global = LLVMAddGlobal(module, arrayType, name)
            LLVMSetLinkage(global, LLVMPrivateLinkage)
            val initializer = LLVMConstStringInContext(context, value, strLength, 0)
            LLVMSetInitializer(global, initializer)
            LLVMSetGlobalConstant(global, 1)
            return global
        }

        formatStrings[Type.INT] = createGlobalString("fmt_int", "%d\n")
        formatStrings[Type.STRING] = createGlobalString("fmt_str", "%s\n")
        formatStrings[Type.FLOAT] = createGlobalString("fmt_float", "%f\n")
        formatStrings[Type.BOOL] = createGlobalString("fmt_bool", "%d\n")
    }

    private fun castValueIfNeeded(value: LLVMValueRef, fromType: Type, toType: Type): LLVMValueRef {
        if (fromType == toType) {
            return value
        }
        if (fromType == Type.INT && toType == Type.FLOAT) {
            return LLVMBuildSIToFP(builder, value, LLVMFloatTypeInContext(context), "int_to_float")
        }
        return value
    }

    internal fun castValue(value: LLVMValueRef, fromType: Type, toType: Type): LLVMValueRef {
        return castValueIfNeeded(value, fromType, toType)
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

    private fun isNumeric(type: Type): Boolean = TypeRules.isNumeric(type)
}
