package nl.endevelopment.parser

import nl.endevelopment.ast.ASTNode
import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type


class ASTBuilder : SlangBaseVisitor<ASTNode>() {

    override fun visitProgram(ctx: SlangParser.ProgramContext): ASTNode {
        val statements = ctx.statement().map { visit(it) as Stmt }
        return Program(statements)
    }

    override fun visitLetStmt(ctx: SlangParser.LetStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val type = when (ctx.type().text) {
            "Int" -> Type.INT
            "String" -> Type.STRING
            "Bool" -> Type.BOOL
            else -> throw RuntimeException("Unknown type: ${ctx.type().text}")
        }
        val expr = visit(ctx.expr()) as Expr
        return Stmt.LetStmt(name, type, expr)
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

    override fun visitParenExpr(ctx: SlangParser.ParenExprContext): ASTNode {
        return visit(ctx.expr())
    }

    override fun visitNumberExpr(ctx: SlangParser.NumberExprContext): ASTNode {
        return Expr.Number(ctx.NUMBER().text.toInt())
    }

    override fun visitVariableExpr(ctx: SlangParser.VariableExprContext): ASTNode {
        return Expr.Variable(ctx.IDENT().text)
    }
}
