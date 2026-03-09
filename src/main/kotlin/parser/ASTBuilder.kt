package nl.endevelopment.parser

import nl.endevelopment.ast.ASTNode
import nl.endevelopment.ast.ClassField
import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.MethodDef
import nl.endevelopment.ast.Param
import nl.endevelopment.ast.Program
import nl.endevelopment.ast.SourceLocation
import nl.endevelopment.ast.Stmt
import nl.endevelopment.semantic.Type
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

class ASTBuilder : SlangBaseVisitor<ASTNode>() {

    private fun location(ctx: ParserRuleContext): SourceLocation {
        val token = ctx.start
        return SourceLocation(token.line, token.charPositionInLine + 1)
    }

    private fun location(token: Token): SourceLocation {
        return SourceLocation(token.line, token.charPositionInLine + 1)
    }

    private fun parseType(typeCtx: SlangParser.TypeContext): Type {
        return parseTypeText(typeCtx.text)
    }

    private fun parseTypeText(typeText: String): Type {
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

    override fun visitProgram(ctx: SlangParser.ProgramContext): ASTNode {
        val statements = ctx.topLevelStatement().map { visit(it) as Stmt }
        return Program(statements)
    }

    override fun visitClassDef(ctx: SlangParser.ClassDefContext): ASTNode {
        val name = ctx.IDENT().text
        val fields = ctx.classFieldList()?.classField()?.map { fieldCtx ->
            val mutable = fieldCtx.getChild(0).text == "var"
            ClassField(
                name = fieldCtx.IDENT().text,
                type = parseType(fieldCtx.type()),
                mutable = mutable,
                location = location(fieldCtx)
            )
        } ?: emptyList()

        val methods = ctx.classBody()?.methodDef()?.map { methodCtx ->
            buildMethodDef(methodCtx)
        } ?: emptyList()

        return Stmt.ClassDef(name, fields, methods, location(ctx))
    }

    override fun visitLetStmt(ctx: SlangParser.LetStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val type = parseType(ctx.type())
        val expr = visit(ctx.expr()) as Expr
        return Stmt.LetStmt(name, type, expr, location(ctx))
    }

    override fun visitVarStmt(ctx: SlangParser.VarStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val type = parseType(ctx.type())
        val expr = visit(ctx.expr()) as Expr
        return Stmt.VarStmt(name, type, expr, location(ctx))
    }

    override fun visitAssignStmt(ctx: SlangParser.AssignStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val expr = visit(ctx.expr()) as Expr
        return Stmt.AssignStmt(name, expr, location(ctx))
    }

    override fun visitMemberAssignStmt(ctx: SlangParser.MemberAssignStmtContext): ASTNode {
        val target = visit(ctx.expr(0)) as Expr
        val member = ctx.IDENT().text
        val value = visit(ctx.expr(1)) as Expr
        return Stmt.MemberAssignStmt(target, member, value, location(ctx))
    }

    override fun visitPrintStmt(ctx: SlangParser.PrintStmtContext): ASTNode {
        val expr = visit(ctx.expr()) as Expr
        return Stmt.PrintStmt(expr, location(ctx))
    }

    override fun visitIfStmt(ctx: SlangParser.IfStmtContext): ASTNode {
        val condition = visit(ctx.expr()) as Expr
        val thenBranch = ctx.block(0).statement().map { visit(it) as Stmt }
        val elseBranch = if (ctx.block().size > 1) {
            ctx.block(1).statement().map { visit(it) as Stmt }
        } else {
            null
        }
        return Stmt.IfStmt(condition, thenBranch, elseBranch, location(ctx))
    }

    override fun visitWhileStmt(ctx: SlangParser.WhileStmtContext): ASTNode {
        val condition = visit(ctx.expr()) as Expr
        val body = ctx.block().statement().map { visit(it) as Stmt }
        return Stmt.WhileStmt(condition, body, location(ctx))
    }

    override fun visitForStmt(ctx: SlangParser.ForStmtContext): ASTNode {
        val init = ctx.forInit()?.let { buildForInit(it) }
        val condition = ctx.expr()?.let { visit(it) as Expr }
        val update = ctx.forUpdate()?.let { buildForUpdate(it) }
        val body = ctx.block().statement().map { visit(it) as Stmt }
        return Stmt.ForStmt(init, condition, update, body, location(ctx))
    }

    override fun visitBreakStmt(ctx: SlangParser.BreakStmtContext): ASTNode {
        return Stmt.BreakStmt(location(ctx))
    }

    override fun visitContinueStmt(ctx: SlangParser.ContinueStmtContext): ASTNode {
        return Stmt.ContinueStmt(location(ctx))
    }

    override fun visitFunctionDef(ctx: SlangParser.FunctionDefContext): ASTNode {
        val name = ctx.IDENT().text
        val params = ctx.paramList()?.param()?.map {
            val paramName = it.IDENT().text
            val paramType = parseType(it.type())
            Param(paramName, paramType, location(it))
        } ?: emptyList()
        val returnType = parseType(ctx.type())
        val body = ctx.block().statement().map { visit(it) as Stmt }
        return Stmt.FunctionDef(name, params, returnType, body, location(ctx))
    }

    override fun visitMethodDef(ctx: SlangParser.MethodDefContext): ASTNode {
        return buildMethodDef(ctx)
    }

    override fun visitReturnStmt(ctx: SlangParser.ReturnStmtContext): ASTNode {
        val expr = ctx.expr()?.let { visit(it) as Expr }
        return Stmt.ReturnStmt(expr, location(ctx))
    }

    override fun visitCallStmt(ctx: SlangParser.CallStmtContext): ASTNode {
        val name = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return Stmt.ExprStmt(Expr.Call(name, args, location(ctx)), location(ctx))
    }

    override fun visitMemberCallStmt(ctx: SlangParser.MemberCallStmtContext): ASTNode {
        val receiver = visit(ctx.expr()) as Expr
        val method = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return Stmt.ExprStmt(Expr.MemberCall(receiver, method, args, location(ctx)), location(ctx))
    }

    private fun buildForInit(ctx: SlangParser.ForInitContext): Stmt {
        val loc = location(ctx)
        ctx.forVarDecl()?.let { decl ->
            val name = decl.IDENT().text
            val type = parseType(decl.type())
            val expr = visit(decl.expr()) as Expr
            return if (decl.getChild(0).text == "let") {
                Stmt.LetStmt(name, type, expr, loc)
            } else {
                Stmt.VarStmt(name, type, expr, loc)
            }
        }
        ctx.forAssign()?.let { assign ->
            return Stmt.AssignStmt(assign.IDENT().text, visit(assign.expr()) as Expr, loc)
        }
        ctx.forCall()?.let { call ->
            val args = call.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
            return Stmt.ExprStmt(Expr.Call(call.IDENT().text, args, loc), loc)
        }
        throw RuntimeException("Invalid for-loop initializer.")
    }

    private fun buildForUpdate(ctx: SlangParser.ForUpdateContext): Stmt {
        val loc = location(ctx)
        ctx.forAssign()?.let { assign ->
            return Stmt.AssignStmt(assign.IDENT().text, visit(assign.expr()) as Expr, loc)
        }
        ctx.forCall()?.let { call ->
            val args = call.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
            return Stmt.ExprStmt(Expr.Call(call.IDENT().text, args, loc), loc)
        }
        throw RuntimeException("Invalid for-loop updater.")
    }

    override fun visitMulDivExpr(ctx: SlangParser.MulDivExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        return Expr.BinaryOp(left, ctx.op.text, right, location(ctx))
    }

    override fun visitAddSubExpr(ctx: SlangParser.AddSubExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        return Expr.BinaryOp(left, ctx.op.text, right, location(ctx))
    }

    override fun visitComparisonExpr(ctx: SlangParser.ComparisonExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        return Expr.BinaryOp(left, ctx.op.text, right, location(ctx))
    }

    override fun visitEqualityExpr(ctx: SlangParser.EqualityExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        return Expr.BinaryOp(left, ctx.op.text, right, location(ctx))
    }

    override fun visitNotExpr(ctx: SlangParser.NotExprContext): ASTNode {
        val operand = visit(ctx.expr()) as Expr
        return Expr.UnaryOp("!", operand, location(ctx))
    }

    override fun visitAndExpr(ctx: SlangParser.AndExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        return Expr.BinaryOp(left, "&&", right, location(ctx))
    }

    override fun visitOrExpr(ctx: SlangParser.OrExprContext): ASTNode {
        val left = visit(ctx.expr(0)) as Expr
        val right = visit(ctx.expr(1)) as Expr
        return Expr.BinaryOp(left, "||", right, location(ctx))
    }

    override fun visitParenExpr(ctx: SlangParser.ParenExprContext): ASTNode {
        return visit(ctx.expr())
    }

    override fun visitCallExpr(ctx: SlangParser.CallExprContext): ASTNode {
        val name = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return Expr.Call(name, args, location(ctx))
    }

    override fun visitMemberCallExpr(ctx: SlangParser.MemberCallExprContext): ASTNode {
        val receiver = visit(ctx.expr()) as Expr
        val method = ctx.IDENT().text
        val args = ctx.argList()?.expr()?.map { visit(it) as Expr } ?: emptyList()
        return Expr.MemberCall(receiver, method, args, location(ctx))
    }

    override fun visitMemberAccessExpr(ctx: SlangParser.MemberAccessExprContext): ASTNode {
        val receiver = visit(ctx.expr()) as Expr
        return Expr.MemberAccess(receiver, ctx.IDENT().text, location(ctx))
    }

    override fun visitListExpr(ctx: SlangParser.ListExprContext): ASTNode {
        val elements = ctx.listLiteral().expr().map { visit(it) as Expr }
        return Expr.ListLiteral(elements, location(ctx))
    }

    override fun visitIndexExpr(ctx: SlangParser.IndexExprContext): ASTNode {
        val target = visit(ctx.expr(0)) as Expr
        val index = visit(ctx.expr(1)) as Expr
        return Expr.Index(target, index, location(ctx))
    }

    override fun visitStringExpr(ctx: SlangParser.StringExprContext): ASTNode {
        val rawText = ctx.STRING_LITERAL().text
        val withoutQuotes = rawText.substring(1, rawText.length - 1)
        val processed = processEscapeSequences(withoutQuotes)
        return Expr.StringLiteral(processed, location(ctx))
    }

    private fun processEscapeSequences(str: String): String {
        return str.replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    override fun visitFloatExpr(ctx: SlangParser.FloatExprContext): ASTNode {
        return Expr.FloatLiteral(ctx.FLOAT().text.toFloat(), location(ctx))
    }

    override fun visitNumberExpr(ctx: SlangParser.NumberExprContext): ASTNode {
        return Expr.Number(ctx.NUMBER().text.toInt(), location(ctx))
    }

    override fun visitBoolTrueExpr(ctx: SlangParser.BoolTrueExprContext): ASTNode {
        return Expr.BoolLiteral(true, location(ctx))
    }

    override fun visitBoolFalseExpr(ctx: SlangParser.BoolFalseExprContext): ASTNode {
        return Expr.BoolLiteral(false, location(ctx))
    }

    override fun visitThisExpr(ctx: SlangParser.ThisExprContext): ASTNode {
        return Expr.This(location(ctx))
    }

    override fun visitVariableExpr(ctx: SlangParser.VariableExprContext): ASTNode {
        return Expr.Variable(ctx.IDENT().text, location(ctx))
    }

    private fun buildMethodDef(ctx: SlangParser.MethodDefContext): MethodDef {
        val name = ctx.IDENT().text
        val params = ctx.paramList()?.param()?.map {
            val paramName = it.IDENT().text
            val paramType = parseType(it.type())
            Param(paramName, paramType, location(it))
        } ?: emptyList()
        val returnType = parseType(ctx.type())
        val body = ctx.block().statement().map { visit(it) as Stmt }
        return MethodDef(name, params, returnType, body, location(ctx))
    }
}
