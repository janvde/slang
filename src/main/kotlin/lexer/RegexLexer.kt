package nl.endevelopment.lexer

import nl.endevelopment.utils.Utils

/**
 * Represents a lexical token with a type, value, line, and column.
 * The RegexLexer tokenizes the raw source code into a list of Token objects.
 */
data class Token(val type: TokenType, val value: String, val line: Int, val column: Int)

/**
 * Enum representing different types of tokens.
 */
enum class TokenType {
    KEYWORD,
    IDENTIFIER,
    NUMBER,
    OPERATOR,
    SEPARATOR,
    LEFT_PAREN,
    RIGHT_PAREN,
    COLON,
    EOF,
    UNKNOWN
}

/**
 * Lexer class responsible for converting source code into a list of tokens using regex.
 *
 * @param input The raw source code as a string.
 */
class RegexLexer(private val input: String) {
    // Current position in the input string
    private var position: Int = 0
    // Current line number (starting at 1)
    private var line: Int = 1
    // Current column number (starting at 1)
    private var column: Int = 1

    // List to store the generated tokens
    private val tokens: MutableList<Token> = mutableListOf()

    /**
     * Tokenizes the input string and returns a list of tokens.
     *
     * @return A list of Token objects representing the lexical tokens.
     */
    fun tokenize(): List<Token> {
        Utils.log("Tokenization started.")
        while (!isAtEnd()) {
            skipWhitespaceAndNewlines()
            if (isAtEnd()) break

            val currentChar = peek()

            // Attempt to match each token specification in order
            var matched = false
            for ((pattern, type) in tokenSpecs) {
                val regex = Regex("^$pattern")
                val remainingInput = input.substring(position)
                val matchResult = regex.find(remainingInput)
                if (matchResult != null) {
                    val value = matchResult.value
                    tokens.add(Token(type, value, line, column))
                    advanceBy(value.length)
                    matched = true
                    break
                }
            }

            if (!matched) {
                // Handle unknown tokens
                tokens.add(Token(TokenType.UNKNOWN, currentChar.toString(), line, column))
                advance()
            }
        }

        // Append EOF token
        tokens.add(Token(TokenType.EOF, "", line, column))
        Utils.log("Tokenization finished. ${tokens.size} tokens generated.")
        return tokens
    }

    /**
     * Advances the current position by one character and updates line and column counters.
     */
    private fun advance() {
        if (!isAtEnd()) {
            if (input[position] == '\n') {
                line++
                column = 1
            } else {
                column++
            }
            position++
        }
    }

    /**
     * Advances the current position by 'count' characters, handling newlines and column updates.
     *
     * @param count The number of characters to advance.
     */
    private fun advanceBy(count: Int) {
        for (i in 0 until count) {
            advance()
        }
    }

    /**
     * Skips over any whitespace and newline characters, updating line and column counters accordingly.
     */
    private fun skipWhitespaceAndNewlines() {
        while (!isAtEnd()) {
            val currentChar = peek()
            if (currentChar == ' ' || currentChar == '\t' || currentChar == '\r') {
                advance()
            } else if (currentChar == '\n') {
                advance()
            } else {
                break
            }
        }
    }

    /**
     * Checks if the lexer has reached the end of the input.
     *
     * @return True if at the end, false otherwise.
     */
    private fun isAtEnd(): Boolean = position >= input.length

    /**
     * Peeks at the current character without consuming it.
     *
     * @return The current character.
     */
    private fun peek(): Char = input[position]

    /**
     * Defines the list of token specifications with their regex patterns and corresponding TokenType.
     *
     * The order of specifications is important to ensure correct token matching.
     */
    private val tokenSpecs: List<Pair<String, TokenType>> = listOf(
        // Keywords
        Pair("let\\b", TokenType.KEYWORD),
        Pair("print\\b", TokenType.KEYWORD),

        // Identifiers (variables, etc.)
        Pair("[a-zA-Z_][a-zA-Z0-9_]*", TokenType.IDENTIFIER),

        // Numbers (integers)
        Pair("\\d+", TokenType.NUMBER),

        // Operators
        Pair("\\+", TokenType.OPERATOR),
        Pair("-", TokenType.OPERATOR),
        Pair("\\*", TokenType.OPERATOR),
        Pair("/", TokenType.OPERATOR),
        Pair("=", TokenType.OPERATOR),
        Pair(">", TokenType.OPERATOR),

        // Separators
        Pair(";", TokenType.SEPARATOR),

        // Parentheses
        Pair("\\(", TokenType.LEFT_PAREN),
        Pair("\\)", TokenType.RIGHT_PAREN),

        // Colon
        Pair(":", TokenType.COLON)
    )
}