package nl.endevelopment.parser

import nl.endevelopment.ast.ASTNode
import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.Stmt
import nl.endevelopment.parser.builder.ExpressionAstBuilder
import nl.endevelopment.parser.builder.SourceLocationFactory
import nl.endevelopment.parser.builder.StatementAstBuilder
import nl.endevelopment.parser.builder.TypeSyntaxParser

class ASTBuilder : SlangBaseVisitor<ASTNode>() {
    private val locations = SourceLocationFactory()
    private val types = TypeSyntaxParser()
    private val expressions = ExpressionAstBuilder(locations)
    private val statements = StatementAstBuilder(types, locations)

    override fun visitProgram(ctx: SlangParser.ProgramContext): ASTNode {
        val topLevel = ctx.topLevelStatement().map { visit(it) as Stmt }
        return Program(topLevel)
    }

    override fun visitClassDef(ctx: SlangParser.ClassDefContext): ASTNode {
        return statements.buildClassDef(ctx, ::buildMethodDef)
    }

    override fun visitLetStmt(ctx: SlangParser.LetStmtContext): ASTNode {
        return statements.buildLetStmt(ctx, ::toExpr)
    }

    override fun visitVarStmt(ctx: SlangParser.VarStmtContext): ASTNode {
        return statements.buildVarStmt(ctx, ::toExpr)
    }

    override fun visitAssignStmt(ctx: SlangParser.AssignStmtContext): ASTNode {
        return statements.buildAssignStmt(ctx, ::toExpr)
    }

    override fun visitMemberAssignStmt(ctx: SlangParser.MemberAssignStmtContext): ASTNode {
        return statements.buildMemberAssignStmt(ctx, ::toExpr)
    }

    override fun visitPrintStmt(ctx: SlangParser.PrintStmtContext): ASTNode {
        return statements.buildPrintStmt(ctx, ::toExpr)
    }

    override fun visitIfStmt(ctx: SlangParser.IfStmtContext): ASTNode {
        return statements.buildIfStmt(ctx, ::toExpr, ::toStmt)
    }

    override fun visitWhileStmt(ctx: SlangParser.WhileStmtContext): ASTNode {
        return statements.buildWhileStmt(ctx, ::toExpr, ::toStmt)
    }

    override fun visitForStmt(ctx: SlangParser.ForStmtContext): ASTNode {
        return statements.buildForStmt(ctx, ::toExpr, ::toStmt)
    }

    override fun visitBreakStmt(ctx: SlangParser.BreakStmtContext): ASTNode {
        return statements.buildBreakStmt(ctx)
    }

    override fun visitContinueStmt(ctx: SlangParser.ContinueStmtContext): ASTNode {
        return statements.buildContinueStmt(ctx)
    }

    override fun visitFunctionDef(ctx: SlangParser.FunctionDefContext): ASTNode {
        return statements.buildFunctionDef(ctx, ::toStmt)
    }

    override fun visitMethodDef(ctx: SlangParser.MethodDefContext): ASTNode {
        return buildMethodDef(ctx)
    }

    override fun visitReturnStmt(ctx: SlangParser.ReturnStmtContext): ASTNode {
        return statements.buildReturnStmt(ctx, ::toExpr)
    }

    override fun visitCallStmt(ctx: SlangParser.CallStmtContext): ASTNode {
        return statements.buildCallStmt(ctx, ::toExpr)
    }

    override fun visitMemberCallStmt(ctx: SlangParser.MemberCallStmtContext): ASTNode {
        return statements.buildMemberCallStmt(ctx, ::toExpr)
    }

    override fun visitMulDivExpr(ctx: SlangParser.MulDivExprContext): ASTNode {
        return expressions.buildMulDivExpr(ctx, ::toExpr)
    }

    override fun visitAddSubExpr(ctx: SlangParser.AddSubExprContext): ASTNode {
        return expressions.buildAddSubExpr(ctx, ::toExpr)
    }

    override fun visitComparisonExpr(ctx: SlangParser.ComparisonExprContext): ASTNode {
        return expressions.buildComparisonExpr(ctx, ::toExpr)
    }

    override fun visitEqualityExpr(ctx: SlangParser.EqualityExprContext): ASTNode {
        return expressions.buildEqualityExpr(ctx, ::toExpr)
    }

    override fun visitNotExpr(ctx: SlangParser.NotExprContext): ASTNode {
        return expressions.buildNotExpr(ctx, ::toExpr)
    }

    override fun visitAndExpr(ctx: SlangParser.AndExprContext): ASTNode {
        return expressions.buildAndExpr(ctx, ::toExpr)
    }

    override fun visitOrExpr(ctx: SlangParser.OrExprContext): ASTNode {
        return expressions.buildOrExpr(ctx, ::toExpr)
    }

    override fun visitParenExpr(ctx: SlangParser.ParenExprContext): ASTNode {
        return visit(ctx.expr())
    }

    override fun visitCallExpr(ctx: SlangParser.CallExprContext): ASTNode {
        return expressions.buildCallExpr(ctx, ::toExpr)
    }

    override fun visitMemberCallExpr(ctx: SlangParser.MemberCallExprContext): ASTNode {
        return expressions.buildMemberCallExpr(ctx, ::toExpr)
    }

    override fun visitMemberAccessExpr(ctx: SlangParser.MemberAccessExprContext): ASTNode {
        return expressions.buildMemberAccessExpr(ctx, ::toExpr)
    }

    override fun visitListExpr(ctx: SlangParser.ListExprContext): ASTNode {
        return expressions.buildListExpr(ctx, ::toExpr)
    }

    override fun visitIndexExpr(ctx: SlangParser.IndexExprContext): ASTNode {
        return expressions.buildIndexExpr(ctx, ::toExpr)
    }

    override fun visitStringExpr(ctx: SlangParser.StringExprContext): ASTNode {
        return expressions.buildStringExpr(ctx)
    }

    override fun visitFloatExpr(ctx: SlangParser.FloatExprContext): ASTNode {
        return expressions.buildFloatExpr(ctx)
    }

    override fun visitNumberExpr(ctx: SlangParser.NumberExprContext): ASTNode {
        return expressions.buildNumberExpr(ctx)
    }

    override fun visitBoolTrueExpr(ctx: SlangParser.BoolTrueExprContext): ASTNode {
        return expressions.buildBoolTrueExpr(ctx)
    }

    override fun visitBoolFalseExpr(ctx: SlangParser.BoolFalseExprContext): ASTNode {
        return expressions.buildBoolFalseExpr(ctx)
    }

    override fun visitThisExpr(ctx: SlangParser.ThisExprContext): ASTNode {
        return expressions.buildThisExpr(ctx)
    }

    override fun visitVariableExpr(ctx: SlangParser.VariableExprContext): ASTNode {
        return expressions.buildVariableExpr(ctx)
    }

    private fun toExpr(ctx: SlangParser.ExprContext): Expr = visit(ctx) as Expr

    private fun toStmt(ctx: SlangParser.StatementContext): Stmt = visit(ctx) as Stmt

    private fun buildMethodDef(ctx: SlangParser.MethodDefContext): MethodDef {
        return statements.buildMethodDef(ctx, ::toStmt)
    }
}
