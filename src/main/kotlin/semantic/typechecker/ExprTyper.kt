package nl.endevelopment.semantic.typechecker

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.semantic.Type
import nl.endevelopment.semantic.core.BuiltinRegistry
import nl.endevelopment.semantic.core.CallResolver
import nl.endevelopment.semantic.core.TypeRules

internal class ExprTyper(
    private val state: TypeCheckState,
    private val builtins: BuiltinRegistry,
    private val callResolver: CallResolver
) {
    fun inferExprType(expr: Expr, expectedType: Type? = null): Type {
        return when (expr) {
            is Expr.Number -> Type.INT
            is Expr.FloatLiteral -> Type.FLOAT
            is Expr.BoolLiteral -> Type.BOOL
            is Expr.StringLiteral -> Type.STRING
            is Expr.This -> {
                val className = state.currentClassName
                    ?: DiagnosticFactory.fail(expr.location, "'this' is only allowed inside class methods.")
                Type.CLASS(className)
            }

            is Expr.Variable -> state.scopes.lookup(expr.name)?.type
                ?: DiagnosticFactory.fail(expr.location, "Undefined variable '${expr.name}'.")

            is Expr.UnaryOp -> {
                val operandType = inferExprType(expr.operand)
                when (expr.operator) {
                    "!" -> {
                        if (operandType != Type.BOOL) {
                            DiagnosticFactory.fail(
                                expr.location,
                                "NOT operator requires Bool operand, got ${DiagnosticFactory.formatType(operandType)}."
                            )
                        }
                        Type.BOOL
                    }

                    else -> DiagnosticFactory.fail(expr.location, "Unknown unary operator '${expr.operator}'.")
                }
            }

            is Expr.BinaryOp -> {
                if (expr.operator == "&&" || expr.operator == "||") {
                    val left = inferExprType(expr.left)
                    val right = inferExprType(expr.right)
                    if (left != Type.BOOL || right != Type.BOOL) {
                        DiagnosticFactory.fail(expr.location, "Logical operators require Bool operands.")
                    }
                    return Type.BOOL
                }

                val leftType = inferExprType(expr.left)
                val rightType = inferExprType(expr.right)
                when (expr.operator) {
                    "+" -> when {
                        leftType == Type.STRING && rightType == Type.STRING -> Type.STRING
                        leftType == Type.INT && rightType == Type.INT -> Type.INT
                        TypeRules.isNumeric(leftType) && TypeRules.isNumeric(rightType) -> Type.FLOAT
                        else -> DiagnosticFactory.fail(
                            expr.location,
                            "Unsupported operand types for '+': ${DiagnosticFactory.nameForOperator(leftType)} and ${DiagnosticFactory.nameForOperator(rightType)}."
                        )
                    }

                    "-", "*", "/" -> when {
                        leftType == Type.INT && rightType == Type.INT -> Type.INT
                        TypeRules.isNumeric(leftType) && TypeRules.isNumeric(rightType) -> Type.FLOAT
                        else -> DiagnosticFactory.fail(
                            expr.location,
                            "Unsupported operand types for '${expr.operator}': ${DiagnosticFactory.formatType(leftType)} and ${DiagnosticFactory.formatType(rightType)}."
                        )
                    }

                    "%" -> {
                        if (leftType != Type.INT || rightType != Type.INT) {
                            DiagnosticFactory.fail(expr.location, "Modulo requires Int operands.")
                        }
                        Type.INT
                    }

                    ">", "<", ">=", "<=" -> {
                        if (!TypeRules.isNumeric(leftType) || !TypeRules.isNumeric(rightType)) {
                            DiagnosticFactory.fail(expr.location, "Comparison '${expr.operator}' requires numeric operands.")
                        }
                        Type.BOOL
                    }

                    "==", "!=" -> {
                        if (leftType != rightType && !(TypeRules.isNumeric(leftType) && TypeRules.isNumeric(rightType))) {
                            DiagnosticFactory.fail(expr.location, "Equality '${expr.operator}' requires compatible operand types.")
                        }
                        Type.BOOL
                    }

                    else -> DiagnosticFactory.fail(expr.location, "Unknown operator '${expr.operator}'.")
                }
            }

            is Expr.Call -> inferCallType(expr)

            is Expr.MemberAccess -> {
                val receiverType = inferExprType(expr.receiver)
                val className = (receiverType as? Type.CLASS)?.name
                    ?: DiagnosticFactory.fail(expr.receiver.location, "Member access requires a class instance receiver.")
                val classInfo = state.classes[className]
                    ?: DiagnosticFactory.fail(expr.location, "Unknown class '$className'.")
                val fieldInfo = classInfo.fields[expr.member]
                    ?: DiagnosticFactory.fail(expr.location, "Class '$className' has no field '${expr.member}'.")
                fieldInfo.type
            }

            is Expr.MemberCall -> {
                val receiverType = inferExprType(expr.receiver)
                val className = (receiverType as? Type.CLASS)?.name
                    ?: DiagnosticFactory.fail(expr.receiver.location, "Method call requires a class instance receiver.")
                val classInfo = state.classes[className]
                    ?: DiagnosticFactory.fail(expr.location, "Unknown class '$className'.")
                val methodInfo = classInfo.methods[expr.method]
                    ?: DiagnosticFactory.fail(expr.location, "Class '$className' has no method '${expr.method}'.")

                if (expr.args.size != methodInfo.paramTypes.size) {
                    DiagnosticFactory.fail(
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
                    ?: DiagnosticFactory.fail(expr.target.location, "Indexing is only supported on List values.")
                val indexType = inferExprType(expr.index)
                if (indexType != Type.INT) {
                    DiagnosticFactory.fail(expr.index.location, "List index must be Int, got ${DiagnosticFactory.formatType(indexType)}.")
                }
                listType.elementType
                    ?: DiagnosticFactory.fail(
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
                if (!TypeRules.isTypeCompatibleForList(expectedElementType, actualElementType)) {
                    DiagnosticFactory.fail(
                        element.location,
                        "List element at index $index must be ${DiagnosticFactory.formatType(expectedElementType)}, got ${DiagnosticFactory.formatType(actualElementType)}."
                    )
                }
            }
            return Type.LIST(expectedElementType)
        }

        var inferredElementType = inferExprType(expr.elements.first())
        expr.elements.drop(1).forEachIndexed { offset, element ->
            val index = offset + 1
            val elementType = inferExprType(element)
            inferredElementType = TypeRules.mergeListElementTypes(
                current = inferredElementType,
                next = elementType,
                index = index,
                location = element.location,
                formatType = DiagnosticFactory::formatType,
                fail = DiagnosticFactory::fail
            )
        }

        return Type.LIST(inferredElementType)
    }

    private fun inferCallType(expr: Expr.Call): Type {
        val target = callResolver.resolve(expr.name, state.programIndex, expr.location, DiagnosticFactory::fail)
        return when (target) {
            is CallResolver.CallTarget.Builtin -> inferBuiltinCallType(expr)
            is CallResolver.CallTarget.Constructor -> {
                if (expr.args.size != target.paramTypes.size) {
                    DiagnosticFactory.fail(
                        expr.location,
                        "Constructor '${target.className}' expects ${target.paramTypes.size} arguments, got ${expr.args.size}."
                    )
                }
                expr.args.forEachIndexed { index, arg ->
                    val argType = inferExprType(arg, target.paramTypes[index])
                    ensureAssignable(target.paramTypes[index], argType, arg.location)
                }
                Type.CLASS(target.className)
            }

            is CallResolver.CallTarget.Function -> {
                if (expr.args.size != target.paramTypes.size) {
                    DiagnosticFactory.fail(
                        expr.location,
                        "Function '${target.name}' expects ${target.paramTypes.size} arguments, got ${expr.args.size}."
                    )
                }

                expr.args.forEachIndexed { index, arg ->
                    val argType = inferExprType(arg, target.paramTypes[index])
                    ensureAssignable(target.paramTypes[index], argType, arg.location)
                }
                target.returnType
            }
        }
    }

    private fun inferBuiltinCallType(expr: Expr.Call): Type {
        return when (expr.name) {
            "len" -> {
                builtins.requireArity(expr, 1, DiagnosticFactory::fail)
                val argType = inferExprType(expr.args[0])
                if (argType !is Type.LIST && argType != Type.STRING) {
                    DiagnosticFactory.fail(expr.args[0].location, "len expects a List or String argument, got ${DiagnosticFactory.formatType(argType)}.")
                }
                Type.INT
            }

            "substr" -> {
                builtins.requireArity(expr, 3, DiagnosticFactory::fail)
                val sourceType = inferExprType(expr.args[0])
                val startType = inferExprType(expr.args[1])
                val lengthType = inferExprType(expr.args[2])
                if (sourceType != Type.STRING) {
                    DiagnosticFactory.fail(expr.args[0].location, "substr expects argument 1 to be String, got ${DiagnosticFactory.formatType(sourceType)}.")
                }
                if (startType != Type.INT || lengthType != Type.INT) {
                    DiagnosticFactory.fail(expr.location, "substr expects (String, Int, Int).")
                }
                Type.STRING
            }

            "contains" -> {
                builtins.requireArity(expr, 2, DiagnosticFactory::fail)
                val haystackType = inferExprType(expr.args[0])
                val needleType = inferExprType(expr.args[1])
                if (haystackType != Type.STRING || needleType != Type.STRING) {
                    DiagnosticFactory.fail(expr.location, "contains expects (String, String).")
                }
                Type.BOOL
            }

            "to_int" -> {
                builtins.requireArity(expr, 1, DiagnosticFactory::fail)
                val argType = inferExprType(expr.args[0])
                if (argType != Type.STRING) {
                    DiagnosticFactory.fail(expr.args[0].location, "to_int expects a String argument, got ${DiagnosticFactory.formatType(argType)}.")
                }
                Type.INT
            }

            "min", "max" -> {
                builtins.requireArity(expr, 2, DiagnosticFactory::fail)
                val leftType = inferExprType(expr.args[0])
                val rightType = inferExprType(expr.args[1])
                if (!TypeRules.isNumeric(leftType) || !TypeRules.isNumeric(rightType)) {
                    DiagnosticFactory.fail(expr.location, "${expr.name} expects numeric arguments.")
                }
                if (leftType == Type.INT && rightType == Type.INT) Type.INT else Type.FLOAT
            }

            "abs" -> {
                builtins.requireArity(expr, 1, DiagnosticFactory::fail)
                val argType = inferExprType(expr.args[0])
                if (!TypeRules.isNumeric(argType)) {
                    DiagnosticFactory.fail(expr.args[0].location, "abs expects a numeric argument.")
                }
                argType
            }

            else -> DiagnosticFactory.fail(expr.location, "Unknown built-in function '${expr.name}'.")
        }
    }

    fun ensureAssignable(expected: Type, actual: Type, location: SourceLocation) {
        TypeRules.ensureAssignable(
            expected = expected,
            actual = actual,
            location = location,
            formatType = DiagnosticFactory::formatType,
            fail = DiagnosticFactory::fail
        )
    }
}
