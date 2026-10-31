package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.semantic.Type
import nl.endevelopment.semantic.core.CallResolver
import nl.endevelopment.semantic.core.TypeRules

/** Resolves expression types against the current codegen symbols without emitting LLVM instructions. */
internal class ExpressionTypeResolver(private val state: CodegenContext) {
    fun inferExprType(expr: Expr): Type {
        with(state) {
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
                            TypeRules.isNumeric(leftType) && TypeRules.isNumeric(rightType) -> Type.FLOAT
                            else -> throw Exception("Unsupported operand types for '+': $leftType and $rightType.")
                        }
                    }

                    "-", "*", "/" -> {
                        val leftType = inferExprType(expr.left)
                        val rightType = inferExprType(expr.right)
                        when {
                            leftType == Type.INT && rightType == Type.INT -> Type.INT
                            TypeRules.isNumeric(leftType) && TypeRules.isNumeric(rightType) -> Type.FLOAT
                            else -> throw Exception("Unsupported operand types for '${expr.operator}': $leftType and $rightType.")
                        }
                    }

                    "%" -> Type.INT
                    else -> Type.INT
                }
            }
        }
    }

    private fun inferListLiteralType(expr: Expr.ListLiteral): Type {
        with(state) {
            if (expr.elements.isEmpty()) {
                return Type.LIST(Type.INT)
            }

            var elementType = inferExprType(expr.elements.first())
            expr.elements.drop(1).forEach { element ->
                val nextType = inferExprType(element)
                if (elementType == nextType) {
                    return@forEach
                }
                if (TypeRules.isNumeric(elementType) && TypeRules.isNumeric(nextType)) {
                    elementType = Type.FLOAT
                    return@forEach
                }
                throw Exception(
                    "List literal element types must be homogeneous. Found $elementType and $nextType."
                )
            }
            return Type.LIST(elementType)
        }
    }

    private fun inferBuiltInCallType(expr: Expr.Call): Type {
        with(state) {
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
                    if (!TypeRules.isNumeric(left) || !TypeRules.isNumeric(right)) {
                        throw Exception("${expr.name} expects numeric arguments.")
                    }
                    if (left == Type.INT && right == Type.INT) Type.INT else Type.FLOAT
                }

                "abs" -> {
                    if (expr.args.size != 1) {
                        throw Exception("abs expects 1 argument, got ${expr.args.size}.")
                    }
                    val argType = inferExprType(expr.args[0])
                    if (!TypeRules.isNumeric(argType)) {
                        throw Exception("abs expects a numeric argument.")
                    }
                    argType
                }
                else -> throw Exception("Unknown built-in function '${expr.name}'.")
            }
        }
    }
}
