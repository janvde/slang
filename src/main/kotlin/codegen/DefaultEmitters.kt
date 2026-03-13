package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Stmt
import org.bytedeco.llvm.LLVM.LLVMValueRef

class DefaultStatementEmitter(
    private val codeGenerator: CodeGenerator
) : StatementEmitter {
    override fun emit(stmt: Stmt) {
        codeGenerator.emitStatement(stmt)
    }
}

class DefaultExpressionEmitter(
    private val codeGenerator: CodeGenerator
) : ExpressionEmitter {
    override fun emit(expr: Expr): LLVMValueRef {
        return codeGenerator.emitExpression(expr)
    }
}
