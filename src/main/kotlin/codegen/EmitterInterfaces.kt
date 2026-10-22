package codegen

import nl.endevelopment.ast.Expr
import nl.endevelopment.ast.Stmt
import org.bytedeco.llvm.LLVM.LLVMValueRef

internal interface StatementEmitter {
    fun emit(stmt: Stmt)
}

internal interface ExpressionEmitter {
    fun emit(expr: Expr): LLVMValueRef
}
