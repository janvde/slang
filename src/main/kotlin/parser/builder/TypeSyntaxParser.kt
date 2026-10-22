package nl.endevelopment.parser.builder

import nl.endevelopment.parser.SlangParser
import nl.endevelopment.semantic.Type

class TypeSyntaxParser {
    fun parseType(typeCtx: SlangParser.TypeContext): Type {
        return parseTypeText(typeCtx.text)
    }

    fun parseTypeText(typeText: String): Type {
        if (!typeText.contains("[")) {
            return Type.fromName(typeText)
        }

        val base = typeText.substringBefore("[")
        val argsText = typeText.substringAfter("[").removeSuffix("]")
        val args = splitTopLevelTypeArgs(argsText).map { parseTypeText(it) }

        return when (base) {
            "List" -> {
                if (args.size != 1) {
                    throw RuntimeException("List type expects exactly one type argument.")
                }
                Type.LIST(args[0])
            }

            else -> throw RuntimeException("Generic type '$base' is not supported.")
        }
    }

    private fun splitTopLevelTypeArgs(argsText: String): List<String> {
        if (argsText.isBlank()) {
            return emptyList()
        }

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0

        argsText.forEach { ch ->
            when (ch) {
                '[' -> {
                    depth++
                    current.append(ch)
                }

                ']' -> {
                    depth--
                    current.append(ch)
                }

                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString().trim())
                        current.clear()
                    } else {
                        current.append(ch)
                    }
                }

                else -> current.append(ch)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }

        return result
    }
}
