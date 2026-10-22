package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
import nl.endevelopment.semantic.core.BuiltinRegistry
import nl.endevelopment.semantic.core.CallResolver
import nl.endevelopment.semantic.core.ProgramIndex
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal data class FunctionInfo(
    val name: String,
    val returnType: Type,
    val paramTypes: List<Type>,
    val llvmFunc: LLVMValueRef,
    val llvmType: LLVMTypeRef,
    val ownerClass: String? = null
)

internal data class ClassFieldInfo(
    val name: String,
    val type: Type,
    val mutable: Boolean,
    val index: Int
)

internal data class ClassInfo(
    val name: String,
    val structType: LLVMTypeRef,
    val fields: List<ClassFieldInfo>,
    val fieldsByName: Map<String, ClassFieldInfo>,
    val methods: MutableMap<String, FunctionInfo>,
    val methodsAst: MutableMap<String, MethodDef>
)

internal data class SymbolInfo(
    val type: Type,
    val ptr: LLVMValueRef,
    val immutable: Boolean = true
)

internal data class LoopTarget(
    val continueBlock: LLVMBasicBlockRef,
    val breakBlock: LLVMBasicBlockRef
)

/** Shared LLVM resources, symbols, and services for one generator. */
internal class CodegenContext {
    val context: LLVMContextRef = LLVMContextCreate()
    val module: LLVMModuleRef = LLVMModuleCreateWithNameInContext("my_module", context)
    val builder: LLVMBuilderRef = LLVMCreateBuilderInContext(context)
    var codegenSymbolTable = mutableMapOf<String, SymbolInfo>()
    val charPtrType: LLVMTypeRef = LLVMPointerType(LLVMInt8TypeInContext(context), 0)

    val formatStrings: MutableMap<Type, LLVMValueRef> = mutableMapOf()

    lateinit var printfFuncType: LLVMTypeRef
    val strlenFuncType: LLVMTypeRef = run {
        val paramTypes = PointerPointer<LLVMTypeRef>(1)
        paramTypes.put(0, charPtrType)
        LLVMFunctionType(LLVMInt64TypeInContext(context), paramTypes, 1, 0)
    }
    lateinit var mallocFuncType: LLVMTypeRef
    lateinit var strcpyFuncType: LLVMTypeRef
    lateinit var strcatFuncType: LLVMTypeRef
    lateinit var strncpyFuncType: LLVMTypeRef
    lateinit var strstrFuncType: LLVMTypeRef
    lateinit var atoiFuncType: LLVMTypeRef

    val functions = mutableMapOf<String, FunctionInfo>()
    val classStructTypes = mutableMapOf<String, LLVMTypeRef>()
    val classes = mutableMapOf<String, ClassInfo>()

    var currentFunctionReturnType: Type? = null
    var currentClassName: String? = null

    val listStructType: LLVMTypeRef = LLVMStructCreateNamed(context, "List")
    val loopTargets = mutableListOf<LoopTarget>()
    private val builtins = BuiltinRegistry()
    val callResolver = CallResolver(builtins)
    lateinit var programIndex: ProgramIndex
    val builtInFunctions = builtins.names()
    val runtime = RuntimeDeclEmitter(this)
    val types = TypeLowering(this)
    val caster = ValueCaster(this)
    val builtinCalls = BuiltinCallEmitter(this)
    val expressions = DefaultExpressionEmitter(this)
    val statements = DefaultStatementEmitter(this)
    val declarations = DeclarationEmitter(this)

    init {
        LLVMInitializeNativeTarget()
        LLVMInitializeNativeAsmPrinter()
        LLVMInitializeNativeAsmParser()
        runtime.declareCoreRuntime()
    }

    fun emit(stmt: Stmt) = statements.emit(stmt)
    fun emit(expr: Expr): LLVMValueRef = expressions.emit(expr)

    fun fail(location: SourceLocation, message: String): Nothing {
        throw Exception(location.format(message))
    }
}
