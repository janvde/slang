package nl.endevelopment.parser

import nl.endevelopment.ast.ASTNode
import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Param
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type


class ASTBuilder : SlangBaseVisitor<ASTNode>() {

    override fun visitProgram(ctx: SlangParser.ProgramContext): ASTNode {
        val statements = ctx.topLevelStatement().map { visit(it) as Stmt }
        return Program(statements)
    }

    override fun visitLetStmt(ctx: SlangParser.LetStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val type = when (ctx.type().text) {
            "Int" -> Type.INT
            "Float" -> Type.FLOAT
            "String" -> Type.STRING
            "Bool" -> Type.BOOL
            "Void" -> Type.VOID
            "List" -> Type.LIST
            else -> throw RuntimeException("Unknown type: ${ctx.type().text}")
        }
        val expr = visit(ctx.expr()) as Expr
        return Stmt.LetStmt(name, type, expr)
    }

    override fun visitVarStmt(ctx: SlangParser.VarStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val type = when (ctx.type().text) {
            "Int" -> Type.INT
            "Float" -> Type.FLOAT
            "String" -> Type.STRING
            "Bool" -> Type.BOOL
            "Void" -> Type.VOID
            "List" -> Type.LIST
            else -> throw RuntimeException("Unknown type: ${ctx.type().text}")
        }
        val expr = visit(ctx.expr()) as Expr
        return Stmt.VarStmt(name, type, expr)
    }

    override fun visitAssignStmt(ctx: SlangParser.AssignStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val expr = visit(ctx.expr()) as Expr
        return Stmt.AssignStmt(name, expr)
    }

    override fun visitPrintStmt(ctx: SlangParser.PrintStmtContext): ASTNode {
        val expr = visit(ctx.expr()) as Expr
        return Stmt.PrintStmt(expr)
    }

    override fun visitIfStmt(ctx: SlangParser.IfStmtContext): ASTNode {
        val condition = visit(ctx.expr()) as Expr
        val thenBranch = ctx.block(0).statement().map { visit(it) as Stmt }
        val elseBranch = if (ctx.block().size > 1) {
            ctx.block(1).statement().map { visit(it) as Stmt }
        } else {
            null
        }
        return Stmt.IfStmt(condition, thenBranch, elseBranch)
    }

    override fun visitFunctionDef(ctx: SlangParser.FunctionDefContext): ASTNode {
        val name = ctx.IDENT().text
        val params = ctx.paramList()?.param()?.map {
            val paramName = it.IDENT().text
            val paramType = when (it.type().text) {
                "Int" -> Type.INT
                "Float" -> Type.FLOAT
                "String" -> Type.STRING
                "Bool" -> Type.BOOL
                "Void" -> Type.VOID
                "List" -> Type.LIST
                else -> throw RuntimeException("Unknown type: ${it.type().text}")
            }
            Param(paramName, paramType)
        } ?: emptyList()
        val returnType = when (ctx.type().text) {
            "Int" -> Type.INT
            "Float" -> Type.FLOAT
            "String" -> Type.STRING
            "Bool" -> Type.BOOL
            "Void" -> Type.VOID
            "List" -> Type.LIST
            else -> throw RuntimeException("Unknown type: ${ctx.type().text}")
        }
        val body = ctx.block().statement().map { visit(it) as Stmt }
        return Stmt.FunctionDef(name, params, returnType, body)
    }

    override fun visitReturnStmt(ctx: SlangParser.ReturnStmtContext): ASTNode {
        val expr = ctx.expr()?.let { visit(it) as Expr }
        return Stmt.ReturnStmt(expr)
    }

    override fun visitCallStmt(ctx: SlangParser.CallStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return Stmt.ExprStmt(Expr.Call(name, args))
    }

    override fun visitMulDivExpr(ctx: SlangParser.MulDivExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        val operator = ctx.op.text
        return Expr.BinaryOp(left, operator, right)
    }

    override fun visitAddSubExpr(ctx: SlangParser.AddSubExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        val operator = ctx.op.text
        return Expr.BinaryOp(left, operator, right)
    }

    override fun visitComparisonExpr(ctx: SlangParser.ComparisonExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        val operator = ctx.op.text
        return Expr.BinaryOp(left, operator, right)
    }

    override fun visitEqualityExpr(ctx: SlangParser.EqualityExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        val operator = ctx.op.text
        return Expr.BinaryOp(left, operator, right)
    }

    override fun visitParenExpr(ctx: SlangParser.ParenExprContext): ASTNode {
        return visit(ctx.expr())
    }

    override fun visitCallExpr(ctx: SlangParser.CallExprContext): ASTNode {
        val name = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return Expr.Call(name, args)
    }

    override fun visitListExpr(ctx: SlangParser.ListExprContext): ASTNode {
        val elements = ctx.listLiteral().expr().map { visit(it) as Expr }
        return Expr.ListLiteral(elements)
    }

    override fun visitIndexExpr(ctx: SlangParser.IndexExprContext): ASTNode {
        val target = visit(ctx.expr(0)) as Expr
        val index = visit(ctx.expr(1)) as Expr
        return Expr.Index(target, index)
    }

    override fun visitStringExpr(ctx: SlangParser.StringExprContext): ASTNode {
        val rawText = ctx.STRING_LITERAL().text
        // Remove surrounding quotes
        val withoutQuotes = rawText.substring(1, rawText.length - 1)
        // Process escape sequences
        val processed = processEscapeSequences(withoutQuotes)
        return Expr.StringLiteral(processed)
    }

    private fun processEscapeSequences(str: String): String {
        return str.replace("\\n", "\n")
                  .replace("\\t", "\t")
                  .replace("\\r", "\r")
                  .replace("\\\"", "\"")
                  .replace("\\\\", "\\")
    }

    override fun visitFloatExpr(ctx: SlangParser.FloatExprContext): ASTNode {
        return Expr.FloatLiteral(ctx.FLOAT().text.toFloat())
    }

    override fun visitNumberExpr(ctx: SlangParser.NumberExprContext): ASTNode {
        return Expr.Number(ctx.NUMBER().text.toInt())
    }

    override fun visitBoolTrueExpr(ctx: SlangParser.BoolTrueExprContext): ASTNode {
        return Expr.BoolLiteral(true)
    }

    override fun visitBoolFalseExpr(ctx: SlangParser.BoolFalseExprContext): ASTNode {
        return Expr.BoolLiteral(false)
    }

    override fun visitVariableExpr(ctx: SlangParser.VariableExprContext): ASTNode {
        return Expr.Variable(ctx.IDENT().text)
    }
}
