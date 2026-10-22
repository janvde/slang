package nl.endevelopment.parser.builder

import nl.endevelopment.ast.Expr
import nl.endevelopment.parser.SlangParser

class ExpressionAstBuilder(
    private val locations: SourceLocationFactory
) {
    fun buildMulDivExpr(ctx: SlangParser.MulDivExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val left = visitExpr(ctx.expr(0))
        val right = visitExpr(ctx.expr(1))
        return Expr.BinaryOp(left, ctx.op.text, right, locations.from(ctx))
    }

    fun buildAddSubExpr(ctx: SlangParser.AddSubExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val left = visitExpr(ctx.expr(0))
        val right = visitExpr(ctx.expr(1))
        return Expr.BinaryOp(left, ctx.op.text, right, locations.from(ctx))
    }

    fun buildComparisonExpr(ctx: SlangParser.ComparisonExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val left = visitExpr(ctx.expr(0))
        val right = visitExpr(ctx.expr(1))
        return Expr.BinaryOp(left, ctx.op.text, right, locations.from(ctx))
    }

    fun buildEqualityExpr(ctx: SlangParser.EqualityExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val left = visitExpr(ctx.expr(0))
        val right = visitExpr(ctx.expr(1))
        return Expr.BinaryOp(left, ctx.op.text, right, locations.from(ctx))
    }

    fun buildNotExpr(ctx: SlangParser.NotExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val operand = visitExpr(ctx.expr())
        return Expr.UnaryOp("!", operand, locations.from(ctx))
    }

    fun buildAndExpr(ctx: SlangParser.AndExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val left = visitExpr(ctx.expr(0))
        val right = visitExpr(ctx.expr(1))
        return Expr.BinaryOp(left, "&&", right, locations.from(ctx))
    }

    fun buildOrExpr(ctx: SlangParser.OrExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val left = visitExpr(ctx.expr(0))
        val right = visitExpr(ctx.expr(1))
        return Expr.BinaryOp(left, "||", right, locations.from(ctx))
    }

    fun buildCallExpr(ctx: SlangParser.CallExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val name = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map(visitExpr) ?: emptyList()
        return Expr.Call(name, args, locations.from(ctx))
    }

    fun buildMemberCallExpr(ctx: SlangParser.MemberCallExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val receiver = visitExpr(ctx.expr())
        val method = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map(visitExpr) ?: emptyList()
        return Expr.MemberCall(receiver, method, args, locations.from(ctx))
    }

    fun buildMemberAccessExpr(ctx: SlangParser.MemberAccessExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val receiver = visitExpr(ctx.expr())
        return Expr.MemberAccess(receiver, ctx.IDENT().text, locations.from(ctx))
    }

    fun buildListExpr(ctx: SlangParser.ListExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val elements = ctx.listLiteral().expr().map(visitExpr)
        return Expr.ListLiteral(elements, locations.from(ctx))
    }

    fun buildIndexExpr(ctx: SlangParser.IndexExprContext, visitExpr: (SlangParser.ExprContext) -> Expr): Expr {
        val target = visitExpr(ctx.expr(0))
        val index = visitExpr(ctx.expr(1))
        return Expr.Index(target, index, locations.from(ctx))
    }

    fun buildStringExpr(ctx: SlangParser.StringExprContext): Expr {
        val rawText = ctx.STRING_LITERAL().text
        val withoutQuotes = rawText.substring(1, rawText.length - 1)
        val processed = processEscapeSequences(withoutQuotes)
        return Expr.StringLiteral(processed, locations.from(ctx))
    }

    private fun processEscapeSequences(str: String): String {
        return str.replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    fun buildFloatExpr(ctx: SlangParser.FloatExprContext): Expr {
        return Expr.FloatLiteral(ctx.FLOAT().text.toFloat(), locations.from(ctx))
    }

    fun buildNumberExpr(ctx: SlangParser.NumberExprContext): Expr {
        return Expr.Number(ctx.NUMBER().text.toInt(), locations.from(ctx))
    }

    fun buildBoolTrueExpr(ctx: SlangParser.BoolTrueExprContext): Expr {
        return Expr.BoolLiteral(true, locations.from(ctx))
    }

    fun buildBoolFalseExpr(ctx: SlangParser.BoolFalseExprContext): Expr {
        return Expr.BoolLiteral(false, locations.from(ctx))
    }

    fun buildThisExpr(ctx: SlangParser.ThisExprContext): Expr {
        return Expr.This(locations.from(ctx))
    }

    fun buildVariableExpr(ctx: SlangParser.VariableExprContext): Expr {
        return Expr.Variable(ctx.IDENT().text, locations.from(ctx))
    }
}
