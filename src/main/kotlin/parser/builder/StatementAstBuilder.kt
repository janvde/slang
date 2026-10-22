package nl.endevelopment.parser.builder

import nl.endevelopment.ast.ClassField
import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.Param
import nl.endevelopment.ast.Stmt
import nl.endevelopment.parser.SlangParser

class StatementAstBuilder(
    private val types: TypeSyntaxParser,
    private val locations: SourceLocationFactory
) {
    fun buildClassDef(
        ctx: SlangParser.ClassDefContext,
        buildMethodDef: (SlangParser.MethodDefContext) -> MethodDef
    ): Stmt.ClassDef {
        val name = ctx.IDENT().text
        val fields = ctx.classFieldList()?.classField()?.map { fieldCtx ->
            val mutable = fieldCtx.getChild(0).text == "var"
            ClassField(
                name = fieldCtx.IDENT().text,
                type = types.parseType(fieldCtx.type()),
                mutable = mutable,
                location = locations.from(fieldCtx)
            )
        } ?: emptyList()

        val methods = ctx.classBody()?.methodDef()?.map(buildMethodDef) ?: emptyList()

        return Stmt.ClassDef(name, fields, methods, locations.from(ctx))
    }

    fun buildLetStmt(ctx: SlangParser.LetStmtContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt.LetStmt {
        val name = ctx.IDENT().text
        val type = types.parseType(ctx.type())
        val expr = visitExpr(ctx.expr())
        return Stmt.LetStmt(name, type, expr, locations.from(ctx))
    }

    fun buildVarStmt(ctx: SlangParser.VarStmtContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt.VarStmt {
        val name = ctx.IDENT().text
        val type = types.parseType(ctx.type())
        val expr = visitExpr(ctx.expr())
        return Stmt.VarStmt(name, type, expr, locations.from(ctx))
    }

    fun buildAssignStmt(ctx: SlangParser.AssignStmtContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt.AssignStmt {
        val name = ctx.IDENT().text
        val expr = visitExpr(ctx.expr())
        return Stmt.AssignStmt(name, expr, locations.from(ctx))
    }

    fun buildMemberAssignStmt(
        ctx: SlangParser.MemberAssignStmtContext,
        visitExpr: (SlangParser.ExprContext) -> Expr
    ): Stmt.MemberAssignStmt {
        val target = visitExpr(ctx.expr(0))
        val member = ctx.IDENT().text
        val value = visitExpr(ctx.expr(1))
        return Stmt.MemberAssignStmt(target, member, value, locations.from(ctx))
    }

    fun buildPrintStmt(ctx: SlangParser.PrintStmtContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt.PrintStmt {
        val expr = visitExpr(ctx.expr())
        return Stmt.PrintStmt(expr, locations.from(ctx))
    }

    fun buildIfStmt(
        ctx: SlangParser.IfStmtContext,
        visitExpr: (SlangParser.ExprContext) -> Expr,
        visitStmt: (SlangParser.StatementContext) -> Stmt
    ): Stmt.IfStmt {
        val condition = visitExpr(ctx.expr())
        val thenBranch = ctx.block(0).statement().map(visitStmt)
        val elseBranch = if (ctx.block().size > 1) {
            ctx.block(1).statement().map(visitStmt)
        } else {
            null
        }
        return Stmt.IfStmt(condition, thenBranch, elseBranch, locations.from(ctx))
    }

    fun buildWhileStmt(
        ctx: SlangParser.WhileStmtContext,
        visitExpr: (SlangParser.ExprContext) -> Expr,
        visitStmt: (SlangParser.StatementContext) -> Stmt
    ): Stmt.WhileStmt {
        val condition = visitExpr(ctx.expr())
        val body = ctx.block().statement().map(visitStmt)
        return Stmt.WhileStmt(condition, body, locations.from(ctx))
    }

    fun buildForStmt(
        ctx: SlangParser.ForStmtContext,
        visitExpr: (SlangParser.ExprContext) -> Expr,
        visitStmt: (SlangParser.StatementContext) -> Stmt
    ): Stmt.ForStmt {
        val init = ctx.forInit()?.let { buildForInit(it, visitExpr) }
        val condition = ctx.expr()?.let(visitExpr)
        val update = ctx.forUpdate()?.let { buildForUpdate(it, visitExpr) }
        val body = ctx.block().statement().map(visitStmt)
        return Stmt.ForStmt(init, condition, update, body, locations.from(ctx))
    }

    fun buildBreakStmt(ctx: SlangParser.BreakStmtContext): Stmt.BreakStmt {
        return Stmt.BreakStmt(locations.from(ctx))
    }

    fun buildContinueStmt(ctx: SlangParser.ContinueStmtContext): Stmt.ContinueStmt {
        return Stmt.ContinueStmt(locations.from(ctx))
    }

    fun buildFunctionDef(
        ctx: SlangParser.FunctionDefContext,
        visitStmt: (SlangParser.StatementContext) -> Stmt
    ): Stmt.FunctionDef {
        val name = ctx.IDENT().text
        val params = ctx.paramList()?.param()?.map {
            val paramName = it.IDENT().text
            val paramType = types.parseType(it.type())
            Param(paramName, paramType, locations.from(it))
        } ?: emptyList()
        val returnType = types.parseType(ctx.type())
        val body = ctx.block().statement().map(visitStmt)
        return Stmt.FunctionDef(name, params, returnType, body, locations.from(ctx))
    }

    fun buildMethodDef(
        ctx: SlangParser.MethodDefContext,
        visitStmt: (SlangParser.StatementContext) -> Stmt
    ): MethodDef {
        val name = ctx.IDENT().text
        val params = ctx.paramList()?.param()?.map {
            val paramName = it.IDENT().text
            val paramType = types.parseType(it.type())
            Param(paramName, paramType, locations.from(it))
        } ?: emptyList()
        val returnType = types.parseType(ctx.type())
        val body = ctx.block().statement().map(visitStmt)
        return MethodDef(name, params, returnType, body, locations.from(ctx))
    }

    fun buildReturnStmt(ctx: SlangParser.ReturnStmtContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt.ReturnStmt {
        val expr = ctx.expr()?.let(visitExpr)
        return Stmt.ReturnStmt(expr, locations.from(ctx))
    }

    fun buildCallStmt(ctx: SlangParser.CallStmtContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt.ExprStmt {
        val name = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map(visitExpr) ?: emptyList()
        val loc = locations.from(ctx)
        return Stmt.ExprStmt(Expr.Call(name, args, loc), loc)
    }

    fun buildMemberCallStmt(ctx: SlangParser.MemberCallStmtContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt.ExprStmt {
        val receiver = visitExpr(ctx.expr())
        val method = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map(visitExpr) ?: emptyList()
        val loc = locations.from(ctx)
        return Stmt.ExprStmt(Expr.MemberCall(receiver, method, args, loc), loc)
    }

    private fun buildForInit(ctx: SlangParser.ForInitContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt {
        val loc = locations.from(ctx)
        ctx.forVarDecl()?.let { decl ->
            val name = decl.IDENT().text
            val type = types.parseType(decl.type())
            val expr = visitExpr(decl.expr())
            return if (decl.getChild(0).text == "let") {
                Stmt.LetStmt(name, type, expr, loc)
            } else {
                Stmt.VarStmt(name, type, expr, loc)
            }
        }
        ctx.forAssign()?.let { assign ->
            return Stmt.AssignStmt(assign.IDENT().text, visitExpr(assign.expr()), loc)
        }
        ctx.forCall()?.let { call ->
            val args = call.argList()?.expr()?.map(visitExpr) ?: emptyList()
            return Stmt.ExprStmt(Expr.Call(call.IDENT().text, args, loc), loc)
        }
        throw RuntimeException("Invalid for-loop initializer.")
    }

    private fun buildForUpdate(ctx: SlangParser.ForUpdateContext, visitExpr: (SlangParser.ExprContext) -> Expr): Stmt {
        val loc = locations.from(ctx)
        ctx.forAssign()?.let { assign ->
            return Stmt.AssignStmt(assign.IDENT().text, visitExpr(assign.expr()), loc)
        }
        ctx.forCall()?.let { call ->
            val args = call.argList()?.expr()?.map(visitExpr) ?: emptyList()
            return Stmt.ExprStmt(Expr.Call(call.IDENT().text, args, loc), loc)
        }
        throw RuntimeException("Invalid for-loop updater.")
    }
}
