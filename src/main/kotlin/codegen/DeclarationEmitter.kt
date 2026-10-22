package codegen

import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

internal class DeclarationEmitter(private val state: CodegenContext) {
    fun emitProgramDeclarations(program: Program) {
        val classDefs = program.statements.filterIsInstance<Stmt.ClassDef>()
        declareClassTypes(classDefs)
        program.statements.filterIsInstance<Stmt.FunctionDef>().forEach { declareFunction(it) }
        classDefs.forEach { classDef ->
            classDef.methods.forEach { declareMethod(classDef.name, it) }
        }
    }

    private fun declareClassTypes(classDefs: List<Stmt.ClassDef>) {
        with(state) {
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
                            ptr.put(index.toLong(), types.llvmTypeFor(field.type))
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
    }

    private fun declareMethod(className: String, method: MethodDef) {
        with(state) {
            val classInfo = classes[className]
                ?: throw Exception("Unknown class '$className'.")
            if (classInfo.methods.containsKey(method.name)) {
                throw Exception("Method '$className.${method.name}' already declared.")
            }

            val returnType = types.llvmTypeFor(method.returnType)
            val paramTypes = mutableListOf<Type>()
            paramTypes.add(Type.CLASS(className))
            paramTypes.addAll(method.params.map { it.type })

            val llvmParamTypes = if (paramTypes.isEmpty()) {
                PointerPointer<LLVMTypeRef>(0)
            } else {
                PointerPointer<LLVMTypeRef>(paramTypes.size.toLong()).also { ptr ->
                    paramTypes.forEachIndexed { index, type ->
                        ptr.put(index.toLong(), types.llvmTypeFor(type))
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
    }

    private fun declareFunction(stmt: Stmt.FunctionDef) {
        with(state) {
            if (functions.containsKey(stmt.name)) {
                throw Exception("Function '${stmt.name}' already declared.")
            }
            if (stmt.name in builtInFunctions) {
                throw Exception("Cannot redefine built-in function '${stmt.name}'.")
            }
            if (classes.containsKey(stmt.name)) {
                throw Exception("Function '${stmt.name}' conflicts with class '${stmt.name}'.")
            }

            val returnType = types.llvmTypeFor(stmt.returnType)
            val paramTypes = stmt.params.map { types.llvmTypeFor(it.type) }

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
    }

    fun generateMethodBody(className: String, method: MethodDef) {
        with(state) {
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
            val selfPtr = LLVMBuildAlloca(builder, types.llvmTypeFor(Type.CLASS(className)), "this")
            LLVMBuildStore(builder, selfValue, selfPtr)
            codegenSymbolTable["this"] = SymbolInfo(Type.CLASS(className), selfPtr, immutable = true)

            method.params.forEachIndexed { index, param ->
                val paramValue = LLVMGetParam(info.llvmFunc, index + 1)
                val paramPtr = LLVMBuildAlloca(builder, types.llvmTypeFor(param.type), param.name)
                LLVMBuildStore(builder, paramValue, paramPtr)
                codegenSymbolTable[param.name] = SymbolInfo(param.type, paramPtr, immutable = false)
            }

            method.body.forEach { statement ->
                if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                    state.emit(statement)
                }
            }

            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                buildDefaultReturn(method.returnType)
            }

            currentFunctionReturnType = previousReturnType
            currentClassName = previousClassName
            codegenSymbolTable = previousSymbols
        }
    }

    fun generateFunctionBody(stmt: Stmt.FunctionDef) {
        with(state) {
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
                val paramPtr = LLVMBuildAlloca(builder, types.llvmTypeFor(param.type), param.name)
                LLVMBuildStore(builder, paramValue, paramPtr)
                codegenSymbolTable[param.name] = SymbolInfo(param.type, paramPtr, immutable = false)
            }

            stmt.body.forEach { statement ->
                if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                    state.emit(statement)
                }
            }

            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                buildDefaultReturn(stmt.returnType)
            }

            currentFunctionReturnType = previousReturnType
            currentClassName = previousClassName
            codegenSymbolTable = previousSymbols
        }
    }

    private fun buildDefaultReturn(type: Type) {
        with(state) {
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
    }
}
