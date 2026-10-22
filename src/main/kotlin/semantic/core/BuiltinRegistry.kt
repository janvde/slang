package nl.endevelopment.semantic.core

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.semantic.Type

/**
 * Canonical built-in function signatures/rules used across compiler phases.
 */
class BuiltinRegistry {

    data class BuiltinSpec(
        val name: String,
        val arity: Int,
        val inferReturnType: (args: List<Type>, location: SourceLocation, fail: (SourceLocation, String) -> Nothing) -> Type
    )

    private val specsByName: Map<String, BuiltinSpec> = listOf(
        BuiltinSpec("len", 1) { args, location, fail ->
            val argType = args[0]
            if (argType !is Type.LIST && argType != Type.STRING) {
                fail(location, "len expects a List or String argument, got $argType.")
            }
            Type.INT
        },
        BuiltinSpec("substr", 3) { args, location, fail ->
            if (args[0] != Type.STRING || args[1] != Type.INT || args[2] != Type.INT) {
                fail(location, "substr expects (String, Int, Int).")
            }
            Type.STRING
        },
        BuiltinSpec("contains", 2) { args, location, fail ->
            if (args[0] != Type.STRING || args[1] != Type.STRING) {
                fail(location, "contains expects (String, String).")
            }
            Type.BOOL
        },
        BuiltinSpec("to_int", 1) { args, location, fail ->
            if (args[0] != Type.STRING) {
                fail(location, "to_int expects a String argument, got ${args[0]}.")
            }
            Type.INT
        },
        BuiltinSpec("min", 2) { args, location, fail ->
            if (!TypeRules.isNumeric(args[0]) || !TypeRules.isNumeric(args[1])) {
                fail(location, "min expects numeric arguments.")
            }
            if (args[0] == Type.INT && args[1] == Type.INT) Type.INT else Type.FLOAT
        },
        BuiltinSpec("max", 2) { args, location, fail ->
            if (!TypeRules.isNumeric(args[0]) || !TypeRules.isNumeric(args[1])) {
                fail(location, "max expects numeric arguments.")
            }
            if (args[0] == Type.INT && args[1] == Type.INT) Type.INT else Type.FLOAT
        },
        BuiltinSpec("abs", 1) { args, location, fail ->
            if (!TypeRules.isNumeric(args[0])) {
                fail(location, "abs expects a numeric argument.")
            }
            args[0]
        }
    ).associateBy { it.name }

    fun names(): Set<String> = specsByName.keys

    fun isBuiltin(name: String): Boolean = name in specsByName

    fun spec(name: String): BuiltinSpec? = specsByName[name]

    fun requireArity(call: Expr.Call, expected: Int, fail: (SourceLocation, String) -> Nothing) {
        if (call.args.size != expected) {
            fail(call.location, "${call.name} expects $expected argument(s), got ${call.args.size}.")
        }
    }
}
