package nl.endevelopment.semantic

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt

class TypeChecker {
    private data class VariableInfo(val type: Type, val immutable: Boolean, val location: SourceLocation)
    private data class FunctionInfo(val returnType: Type, val paramTypes: List<Type>, val location: SourceLocation)
    private data class MethodInfo(val returnType: Type, val paramTypes: List<Type>, val location: SourceLocation)
    private data class ClassFieldInfo(val type: Type, val mutable: Boolean, val location: SourceLocation)
    private data class ClassInfo(
        val declarationLocation: SourceLocation,
        val fields: Map<String, ClassFieldInfo>,
        val methods: Map<String, MethodInfo>
    )

    private val scopes = mutableListOf<MutableMap<String, VariableInfo>>()
    private val functions = mutableMapOf<String, FunctionInfo>()
    private val classes = mutableMapOf<String, ClassInfo>()
    private var currentReturnType: Type? = null
    private var currentClassName: String? = null
    private var loopDepth = 0

    private val builtInFunctions = setOf("len", "substr", "contains", "to_int", "min", "max", "abs")

    fun check(program: Program) {
        scopes.clear()
        functions.clear()
        classes.clear()
        currentReturnType = null
        currentClassName = null
        loopDepth = 0

        enterScope()
        try {
            program.statements.filterIsInstance<Stmt.ClassDef>().forEach { declareClass(it) }
            program.statements.filterIsInstance<Stmt.FunctionDef>().forEach { declareFunction(it) }
            program.statements.forEach { checkStatement(it) }
        } finally {
            exitScope()
        }
    }

    private fun declareClass(stmt: Stmt.ClassDef) {
        val existingClass = classes[stmt.name]
        if (existingClass != null) {
            failWithRelated(
                stmt.location,
                "Class '${stmt.name}' is already defined.",
                existingClass.declarationLocation
            )
        }

        val existingFunction = functions[stmt.name]
        if (existingFunction != null) {
            failWithRelated(
                stmt.location,
                "Class '${stmt.name}' conflicts with function '${stmt.name}'.",
                existingFunction.location
            )
        }

        val fields = linkedMapOf<String, ClassFieldInfo>()
        stmt.fields.forEach { field ->
            val existingField = fields[field.name]
            if (existingField != null) {
                failWithRelated(
                    field.location,
                    "Field '${field.name}' is already defined in class '${stmt.name}'.",
                    existingField.location
                )
            }
            fields[field.name] = ClassFieldInfo(field.type, field.mutable, field.location)
        }

        val methods = linkedMapOf<String, MethodInfo>()
        stmt.methods.forEach { method ->
            val existingMethod = methods[method.name]
            if (existingMethod != null) {
                failWithRelated(
                    method.location,
                    "Method '${method.name}' is already defined in class '${stmt.name}'.",
                    existingMethod.location
                )
            }
            methods[method.name] = MethodInfo(method.returnType, method.params.map { it.type }, method.location)
        }

        classes[stmt.name] = ClassInfo(
            declarationLocation = stmt.location,
            fields = fields,
            methods = methods
        )
    }

    private fun declareFunction(stmt: Stmt.FunctionDef) {
        if (stmt.name in builtInFunctions) {
            fail(stmt.location, "Cannot redefine built-in function '${stmt.name}'.")
        }

        val existingClass = classes[stmt.name]
        if (existingClass != null) {
            failWithRelated(
                stmt.location,
                "Function '${stmt.name}' conflicts with class '${stmt.name}'.",
                existingClass.declarationLocation
            )
        }

        val existingFunction = functions[stmt.name]
        if (existingFunction != null) {
            failWithRelated(
                stmt.location,
                "Function '${stmt.name}' is already defined.",
                existingFunction.location
            )
        }

        functions[stmt.name] = FunctionInfo(stmt.returnType, stmt.params.map { it.type }, stmt.location)
    }

    private fun checkStatement(stmt: Stmt) {
        when (stmt) {
            is Stmt.LetStmt -> {
                ensureNotDefinedInCurrentScope(stmt.name, stmt.location)
                val exprType = inferExprType(stmt.expr, stmt.type)
                ensureAssignable(stmt.type, exprType, stmt.location)
                define(stmt.name, stmt.type, immutable = true, stmt.location)
            }

            is Stmt.VarStmt -> {
                ensureNotDefinedInCurrentScope(stmt.name, stmt.location)
                val exprType = inferExprType(stmt.expr, stmt.type)
                ensureAssignable(stmt.type, exprType, stmt.location)
                define(stmt.name, stmt.type, immutable = false, stmt.location)
            }

            is Stmt.AssignStmt -> {
                val variable = lookup(stmt.name)
                    ?: fail(stmt.location, "Undefined variable '${stmt.name}'.")
                if (variable.immutable) {
                    fail(stmt.location, "Cannot reassign immutable variable '${stmt.name}'.")
                }
                val exprType = inferExprType(stmt.expr, variable.type)
                ensureAssignable(variable.type, exprType, stmt.location)
            }

            is Stmt.MemberAssignStmt -> {
                val targetType = inferExprType(stmt.target)
                val className = (targetType as? Type.CLASS)?.name
                    ?: fail(stmt.target.location, "Field assignment target must be a class instance.")
                val classInfo = classes[className]
                    ?: fail(stmt.target.location, "Unknown class '$className'.")
                val fieldInfo = classInfo.fields[stmt.member]
                    ?: fail(stmt.location, "Class '$className' has no field '${stmt.member}'.")
                if (!fieldInfo.mutable) {
                    fail(stmt.location, "Cannot assign to immutable field '${stmt.member}' in class '$className'.")
                }
                val valueType = inferExprType(stmt.expr, fieldInfo.type)
                ensureAssignable(fieldInfo.type, valueType, stmt.location)
            }

            is Stmt.PrintStmt -> {
                val exprType = inferExprType(stmt.expr)
                if (exprType is Type.CLASS) {
                    fail(stmt.expr.location, "Printing class instances is not supported in v1.")
                }
            }

            is Stmt.IfStmt -> {
                val conditionType = inferExprType(stmt.condition)
                if (conditionType != Type.BOOL) {
                    fail(stmt.condition.location, "If condition must be Bool, got ${formatType(conditionType)}.")
                }

                enterScope()
                try {
                    stmt.thenBranch.forEach { checkStatement(it) }
                } finally {
                    exitScope()
                }

                stmt.elseBranch?.let { elseBranch ->
                    enterScope()
                    try {
                        elseBranch.forEach { checkStatement(it) }
                    } finally {
                        exitScope()
                    }
                }
            }

            is Stmt.WhileStmt -> {
                val conditionType = inferExprType(stmt.condition)
                if (conditionType != Type.BOOL) {
                    fail(stmt.condition.location, "While condition must be Bool, got ${formatType(conditionType)}.")
                }
                loopDepth++
                enterScope()
                try {
                    stmt.body.forEach { checkStatement(it) }
                } finally {
                    exitScope()
                    loopDepth--
                }
            }

            is Stmt.ForStmt -> {
                enterScope()
                try {
                    stmt.init?.let { checkStatement(it) }

                    stmt.condition?.let { cond ->
                        val conditionType = inferExprType(cond)
                        if (conditionType != Type.BOOL) {
                            fail(cond.location, "For-loop condition must be Bool, got ${formatType(conditionType)}.")
                        }
                    }

                    loopDepth++
                    try {
                        stmt.body.forEach { checkStatement(it) }
                        stmt.update?.let { checkForUpdate(it) }
                    } finally {
                        loopDepth--
                    }
                } finally {
                    exitScope()
                }
            }

            is Stmt.BreakStmt -> {
                if (loopDepth <= 0) {
                    fail(stmt.location, "'break' is only allowed inside a loop.")
                }
            }

            is Stmt.ContinueStmt -> {
                if (loopDepth <= 0) {
                    fail(stmt.location, "'continue' is only allowed inside a loop.")
                }
            }

            is Stmt.FunctionDef -> checkFunctionBody(stmt)

            is Stmt.ReturnStmt -> {
                val expected = currentReturnType
                    ?: fail(stmt.location, "Return statement is only allowed inside a function or method.")
                val actual = stmt.expr?.let { inferExprType(it, expected) } ?: Type.VOID
                ensureAssignable(expected, actual, stmt.location)
            }

            is Stmt.ExprStmt -> {
                inferExprType(stmt.expr)
            }

            is Stmt.ClassDef -> checkClassBody(stmt)
        }
    }

    private fun checkClassBody(stmt: Stmt.ClassDef) {
        val classInfo = classes[stmt.name]
            ?: fail(stmt.location, "Unknown class '${stmt.name}'.")

        val previousClassName = currentClassName
        val previousReturnType = currentReturnType
        currentClassName = stmt.name

        try {
            stmt.methods.forEach { method ->
                val methodInfo = classInfo.methods[method.name]
                    ?: fail(method.location, "Unknown method '${method.name}' in class '${stmt.name}'.")

                currentReturnType = methodInfo.returnType
                enterScope()
                try {
                    define("this", Type.CLASS(stmt.name), immutable = true, method.location)
                    method.params.forEach { param ->
                        ensureNotDefinedInCurrentScope(param.name, param.location)
                        define(param.name, param.type, immutable = false, param.location)
                    }
                    method.body.forEach { checkStatement(it) }
                } finally {
                    exitScope()
                }
            }
        } finally {
            currentClassName = previousClassName
            currentReturnType = previousReturnType
        }
    }

    private fun checkFunctionBody(stmt: Stmt.FunctionDef) {
        val info = functions[stmt.name] ?: fail(stmt.location, "Unknown function '${stmt.name}'.")
        val previousReturnType = currentReturnType
        val previousClassName = currentClassName
        currentClassName = null
        currentReturnType = info.returnType

        enterScope()
        try {
            stmt.params.forEach { param ->
                ensureNotDefinedInCurrentScope(param.name, param.location)
                define(param.name, param.type, immutable = false, param.location)
            }
            stmt.body.forEach { checkStatement(it) }
        } finally {
            exitScope()
            currentReturnType = previousReturnType
            currentClassName = previousClassName
        }
    }

    private fun checkForUpdate(stmt: Stmt) {
        when (stmt) {
            is Stmt.AssignStmt,
            is Stmt.MemberAssignStmt -> checkStatement(stmt)

            is Stmt.ExprStmt -> {
                if (stmt.expr !is Expr.Call && stmt.expr !is Expr.MemberCall) {
                    fail(stmt.location, "For-loop update must be an assignment or call expression.")
                }
                inferExprType(stmt.expr)
            }

            else -> fail(stmt.location, "For-loop update must be an assignment or call expression.")
        }
    }

    private fun inferExprType(expr: Expr, expectedType: Type? = null): Type {
        return when (expr) {
            is Expr.Number -> Type.INT
            is Expr.FloatLiteral -> Type.FLOAT
            is Expr.BoolLiteral -> Type.BOOL
            is Expr.StringLiteral -> Type.STRING
            is Expr.This -> {
                val className = currentClassName
                    ?: fail(expr.location, "'this' is only allowed inside class methods.")
                Type.CLASS(className)
            }

            is Expr.Variable -> lookup(expr.name)?.type
                ?: fail(expr.location, "Undefined variable '${expr.name}'.")

            is Expr.UnaryOp -> {
                val operandType = inferExprType(expr.operand)
                when (expr.operator) {
                    "!" -> {
                        if (operandType != Type.BOOL) {
                            fail(
                                expr.location,
                                "NOT operator requires Bool operand, got ${formatType(operandType)}."
                            )
                        }
                        Type.BOOL
                    }

                    else -> fail(expr.location, "Unknown unary operator '${expr.operator}'.")
                }
            }

            is Expr.BinaryOp -> {
                if (expr.operator == "&&" || expr.operator == "||") {
                    val left = inferExprType(expr.left)
                    val right = inferExprType(expr.right)
                    if (left != Type.BOOL || right != Type.BOOL) {
                        fail(expr.location, "Logical operators require Bool operands.")
                    }
                    return Type.BOOL
                }

                val leftType = inferExprType(expr.left)
                val rightType = inferExprType(expr.right)
                when (expr.operator) {
                    "+" -> when {
                        leftType == Type.STRING && rightType == Type.STRING -> Type.STRING
                        leftType == Type.INT && rightType == Type.INT -> Type.INT
                        isNumeric(leftType) && isNumeric(rightType) -> Type.FLOAT
                        else -> fail(
                            expr.location,
                            "Unsupported operand types for '+': ${leftType.nameForOperator()} and ${rightType.nameForOperator()}."
                        )
                    }

                    "-", "*", "/" -> when {
                        leftType == Type.INT && rightType == Type.INT -> Type.INT
                        isNumeric(leftType) && isNumeric(rightType) -> Type.FLOAT
                        else -> fail(
                            expr.location,
                            "Unsupported operand types for '${expr.operator}': ${formatType(leftType)} and ${formatType(rightType)}."
                        )
                    }

                    "%" -> {
                        if (leftType != Type.INT || rightType != Type.INT) {
                            fail(expr.location, "Modulo requires Int operands.")
                        }
                        Type.INT
                    }

                    ">", "<", ">=", "<=" -> {
                        if (!isNumeric(leftType) || !isNumeric(rightType)) {
                            fail(expr.location, "Comparison '${expr.operator}' requires numeric operands.")
                        }
                        Type.BOOL
                    }

                    "==", "!=" -> {
                        if (leftType != rightType && !(isNumeric(leftType) && isNumeric(rightType))) {
                            fail(expr.location, "Equality '${expr.operator}' requires compatible operand types.")
                        }
                        Type.BOOL
                    }

                    else -> fail(expr.location, "Unknown operator '${expr.operator}'.")
                }
            }

            is Expr.Call -> inferCallType(expr)

            is Expr.MemberAccess -> {
                val receiverType = inferExprType(expr.receiver)
                val className = (receiverType as? Type.CLASS)?.name
                    ?: fail(expr.receiver.location, "Member access requires a class instance receiver.")
                val classInfo = classes[className]
                    ?: fail(expr.location, "Unknown class '$className'.")
                val fieldInfo = classInfo.fields[expr.member]
                    ?: fail(expr.location, "Class '$className' has no field '${expr.member}'.")
                fieldInfo.type
            }

            is Expr.MemberCall -> {
                val receiverType = inferExprType(expr.receiver)
                val className = (receiverType as? Type.CLASS)?.name
                    ?: fail(expr.receiver.location, "Method call requires a class instance receiver.")
                val classInfo = classes[className]
                    ?: fail(expr.location, "Unknown class '$className'.")
                val methodInfo = classInfo.methods[expr.method]
                    ?: fail(expr.location, "Class '$className' has no method '${expr.method}'.")

                if (expr.args.size != methodInfo.paramTypes.size) {
                    fail(
                        expr.location,
                        "Method '${expr.method}' expects ${methodInfo.paramTypes.size} arguments, got ${expr.args.size}."
                    )
                }

                expr.args.forEachIndexed { index, arg ->
                    val argType = inferExprType(arg, methodInfo.paramTypes[index])
                    ensureAssignable(methodInfo.paramTypes[index], argType, arg.location)
                }

                methodInfo.returnType
            }

            is Expr.ListLiteral -> inferListLiteralType(expr, expectedType)

            is Expr.Index -> {
                val targetType = inferExprType(expr.target)
                val listType = targetType as? Type.LIST
                    ?: fail(expr.target.location, "Indexing is only supported on List values.")
                val indexType = inferExprType(expr.index)
                if (indexType != Type.INT) {
                    fail(expr.index.location, "List index must be Int, got ${formatType(indexType)}.")
                }
                listType.elementType
                    ?: fail(
                        expr.location,
                        "Cannot infer element type when indexing an untyped/empty list expression."
                    )
            }
        }
    }

    private fun inferListLiteralType(expr: Expr.ListLiteral, expectedType: Type?): Type {
        val expectedElementType = (expectedType as? Type.LIST)?.elementType

        if (expr.elements.isEmpty()) {
            return Type.LIST(expectedElementType)
        }

        if (expectedElementType != null) {
            expr.elements.forEachIndexed { index, element ->
                val actualElementType = inferExprType(element, expectedElementType)
                ensureAssignable(expectedElementType, actualElementType, element.location)
                if (!isTypeCompatibleForList(expectedElementType, actualElementType)) {
                    fail(
                        element.location,
                        "List element at index $index must be ${formatType(expectedElementType)}, got ${formatType(actualElementType)}."
                    )
                }
            }
            return Type.LIST(expectedElementType)
        }

        var inferredElementType = inferExprType(expr.elements.first())
        expr.elements.drop(1).forEachIndexed { offset, element ->
            val index = offset + 1
            val elementType = inferExprType(element)
            inferredElementType = mergeListElementTypes(inferredElementType, elementType, index, element.location)
        }

        return Type.LIST(inferredElementType)
    }

    private fun mergeListElementTypes(current: Type, next: Type, index: Int, location: SourceLocation): Type {
        if (current == next) {
            return current
        }
        if (isNumeric(current) && isNumeric(next)) {
            return Type.FLOAT
        }
        fail(
            location,
            "List element at index $index has type ${formatType(next)}, expected ${formatType(current)}."
        )
    }

    private fun inferCallType(expr: Expr.Call): Type {
        if (expr.name in builtInFunctions) {
            return inferBuiltinCallType(expr)
        }

        classes[expr.name]?.let { classInfo ->
            if (expr.args.size != classInfo.fields.size) {
                fail(
                    expr.location,
                    "Constructor '${expr.name}' expects ${classInfo.fields.size} arguments, got ${expr.args.size}."
                )
            }

            val fieldInfos = classInfo.fields.values.toList()
            expr.args.forEachIndexed { index, arg ->
                val argType = inferExprType(arg, fieldInfos[index].type)
                ensureAssignable(fieldInfos[index].type, argType, arg.location)
            }
            return Type.CLASS(expr.name)
        }

        val fn = functions[expr.name]
            ?: fail(expr.location, "Undefined function '${expr.name}'.")
        if (expr.args.size != fn.paramTypes.size) {
            fail(expr.location, "Function '${expr.name}' expects ${fn.paramTypes.size} arguments, got ${expr.args.size}.")
        }

        expr.args.forEachIndexed { index, arg ->
            val argType = inferExprType(arg, fn.paramTypes[index])
            ensureAssignable(fn.paramTypes[index], argType, arg.location)
        }
        return fn.returnType
    }

    private fun inferBuiltinCallType(expr: Expr.Call): Type {
        return when (expr.name) {
            "len" -> {
                requireArity(expr, 1)
                val argType = inferExprType(expr.args[0])
                if (argType !is Type.LIST && argType != Type.STRING) {
                    fail(expr.args[0].location, "len expects a List or String argument, got ${formatType(argType)}.")
                }
                Type.INT
            }

            "substr" -> {
                requireArity(expr, 3)
                val sourceType = inferExprType(expr.args[0])
                val startType = inferExprType(expr.args[1])
                val lengthType = inferExprType(expr.args[2])
                if (sourceType != Type.STRING) {
                    fail(expr.args[0].location, "substr expects argument 1 to be String, got ${formatType(sourceType)}.")
                }
                if (startType != Type.INT || lengthType != Type.INT) {
                    fail(expr.location, "substr expects (String, Int, Int).")
                }
                Type.STRING
            }

            "contains" -> {
                requireArity(expr, 2)
                val haystackType = inferExprType(expr.args[0])
                val needleType = inferExprType(expr.args[1])
                if (haystackType != Type.STRING || needleType != Type.STRING) {
                    fail(expr.location, "contains expects (String, String).")
                }
                Type.BOOL
            }

            "to_int" -> {
                requireArity(expr, 1)
                val argType = inferExprType(expr.args[0])
                if (argType != Type.STRING) {
                    fail(expr.args[0].location, "to_int expects a String argument, got ${formatType(argType)}.")
                }
                Type.INT
            }

            "min", "max" -> {
                requireArity(expr, 2)
                val leftType = inferExprType(expr.args[0])
                val rightType = inferExprType(expr.args[1])
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    fail(expr.location, "${expr.name} expects numeric arguments.")
                }
                if (leftType == Type.INT && rightType == Type.INT) Type.INT else Type.FLOAT
            }

            "abs" -> {
                requireArity(expr, 1)
                val argType = inferExprType(expr.args[0])
                if (!isNumeric(argType)) {
                    fail(expr.args[0].location, "abs expects a numeric argument.")
                }
                argType
            }

            else -> fail(expr.location, "Unknown built-in function '${expr.name}'.")
        }
    }

    private fun requireArity(expr: Expr.Call, expectedArity: Int) {
        if (expr.args.size != expectedArity) {
            fail(expr.location, "${expr.name} expects $expectedArity argument(s), got ${expr.args.size}.")
        }
    }

    private fun ensureAssignable(expected: Type, actual: Type, location: SourceLocation) {
        if (expected == actual) {
            return
        }

        if (expected == Type.FLOAT && actual == Type.INT) {
            return
        }

        if (expected is Type.LIST && actual is Type.LIST) {
            if (expected.elementType == null || actual.elementType == null) {
                return
            }
            if (expected.elementType == actual.elementType) {
                return
            }
            fail(
                location,
                "Type mismatch: expected ${formatType(expected)}, got ${formatType(actual)}."
            )
        }

        fail(location, "Type mismatch: expected ${formatType(expected)}, got ${formatType(actual)}.")
    }

    private fun isTypeCompatibleForList(expectedElement: Type, actualElement: Type): Boolean {
        if (expectedElement == actualElement) {
            return true
        }
        return expectedElement == Type.FLOAT && actualElement == Type.INT
    }

    private fun isNumeric(type: Type): Boolean = type == Type.INT || type == Type.FLOAT

    private fun ensureNotDefinedInCurrentScope(name: String, location: SourceLocation) {
        val existing = lookupCurrentScope(name)
        if (existing != null) {
            failWithRelated(
                location,
                "Variable '$name' is already defined in this scope.",
                existing.location
            )
        }
    }

    private fun enterScope() {
        scopes.add(mutableMapOf())
    }

    private fun exitScope() {
        if (scopes.isNotEmpty()) {
            scopes.removeAt(scopes.size - 1)
        }
    }

    private fun define(name: String, type: Type, immutable: Boolean, location: SourceLocation) {
        scopes.last()[name] = VariableInfo(type, immutable, location)
    }

    private fun lookup(name: String): VariableInfo? {
        for (scope in scopes.asReversed()) {
            scope[name]?.let { return it }
        }
        return null
    }

    private fun lookupCurrentScope(name: String): VariableInfo? {
        return scopes.lastOrNull()?.get(name)
    }

    private fun failWithRelated(location: SourceLocation, message: String, related: SourceLocation): Nothing {
        val details = if (related.line > 0 && related.column > 0) {
            "$message Note: previous declaration is at line ${related.line}, col ${related.column}."
        } else {
            message
        }
        fail(location, details)
    }

    private fun formatType(type: Type): String = type.toString()

    private fun Type.nameForOperator(): String {
        return when (this) {
            Type.INT -> "INT"
            Type.FLOAT -> "FLOAT"
            Type.BOOL -> "BOOL"
            Type.STRING -> "STRING"
            is Type.LIST -> "LIST"
            Type.VOID -> "VOID"
            is Type.CLASS -> name
        }
    }

    private fun fail(location: SourceLocation, message: String): Nothing {
        throw RuntimeException(location.format(message))
    }
}
