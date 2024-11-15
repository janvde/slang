package nl.endevelopment.parser

import nl.endevelopment.ast.ASTNode
import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.lexer.Token
import nl.endevelopment.lexer.TokenType
import nl.endevelopment.semantic.Type
import nl.endevelopment.utils.Utils


/**
 * The Parser consumes the list of tokens to build an Abstract Syntax Tree (AST).
 */
class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): ASTNode {
        // Simplified parsing logic
        Utils.log("Parsing started.")
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            statements.add(parseStatement())
        }
        Utils.log("Parsing finished.")
        return Program(statements)
    }

    private fun parseStatement(): Stmt {
        return when (peek().type) {
            TokenType.KEYWORD -> {
                when (peek().value) {
                    "let" -> parseLetStmt()
                    "print" -> parsePrintStmt()
                    else -> throw Exception("Unexpected keyword: ${peek().value}")
                }
            }
            else -> throw Exception("Unexpected token: ${peek().value}")
        }
    }

    /**
     * Parses a 'let' statement with type annotation.
     *
     * Syntax: let <identifier> : <type> = <expression>;
     *
     * @return The parsed LetStmt AST node.
     */
    private fun parseLetStmt(): Stmt.LetStmt {
        consume(TokenType.KEYWORD, "Expected 'let'.")
        val nameToken = consume(TokenType.IDENTIFIER, "Expected variable name.")
        val name = nameToken.value

        consume(TokenType.COLON, "Expected ':' after variable name.")

        val typeToken = consume(TokenType.IDENTIFIER, "Expected type after ':'.")
        val type = parseType(typeToken.value)

        consume(TokenType.OPERATOR, "Expected '=' after type.")
        val expr = parseExpression()

        consume(TokenType.SEPARATOR, "Expected ';' after expression.")

        return Stmt.LetStmt(name, type, expr)
    }

    private fun parsePrintStmt(): Stmt.PrintStmt {
        consume(TokenType.KEYWORD, "Expected 'print'.")
        val expr = parseExpression()
        consume(TokenType.SEPARATOR, "Expected ';' after expression.")
        return Stmt.PrintStmt(expr)
    }

    /**
     * Parses an expression with support for binary operations.
     *
     * Implements recursive descent parsing with operator precedence.
     *
     * @return The parsed expression as an Expr AST node.
     */
    private fun parseExpression(): Expr {
        return parseBinaryOp(0)
    }

    /**
     * Parses binary operations based on operator precedence.
     *
     * @param precedence The current precedence level.
     * @return The parsed expression as an Expr AST node.
     */
    private fun parseBinaryOp(precedence: Int): Expr {
        var left = parsePrimary()

        while (true) {
            val currentPrecedence = getPrecedence(peek())
            if (currentPrecedence < precedence) break

            val operatorToken = advance()
            val operator = operatorToken.value
            val nextPrecedence = currentPrecedence + 1
            val right = parseBinaryOp(nextPrecedence)

            left = Expr.BinaryOp(left, operator, right)
        }

        return left
    }

    /**
     * Parses primary expressions: numbers and variables.
     *
     * @return The parsed primary expression as an Expr AST node.
     */
    private fun parsePrimary(): Expr {
        return when (peek().type) {
            TokenType.NUMBER -> {
                val numberToken = advance()
                Expr.Number(numberToken.value.toInt())
            }
            TokenType.IDENTIFIER -> {
                val identifierToken = advance()
                Expr.Variable(identifierToken.value)
            }
            TokenType.LEFT_PAREN -> {
                consume(TokenType.LEFT_PAREN, "Expected '('.")
                val expr = parseExpression()
                consume(TokenType.RIGHT_PAREN, "Expected ')' after expression.")
                expr
            }
            else -> throw Exception("Unexpected token in expression: ${peek().value}")
        }
    }

    /**
     * Parses a type string into the corresponding Type enum.
     *
     * @param typeName The name of the type as a string.
     * @return The corresponding Type enum value.
     */
    private fun parseType(typeName: String): Type {
        return when (typeName) {
            "int" -> Type.INT
            "float" -> Type.FLOAT
            "bool" -> Type.BOOL
            "string" -> Type.STRING
            else -> throw Exception("Unknown type: $typeName")
        }
    }

    /**
     * Determines the precedence of the current operator token.
     *
     * @param token The current token to evaluate.
     * @return The precedence level as an integer.
     */
    private fun getPrecedence(token: Token): Int {
        return when (token.value) {
            ">" -> 1
            "+", "-" -> 2
            "*", "/" -> 3
            else -> -1 // Not an operator
        }
    }

    private fun consume(type: TokenType, errorMessage: String): Token {
        if (check(type)) return advance()
        throw Exception("$errorMessage Found '${peek().value}' instead.")
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]
}