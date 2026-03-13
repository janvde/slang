package codegen

import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.LLVM.LLVMContextRef
import org.bytedeco.llvm.LLVM.LLVMModuleRef

class CodegenContext(
    val llvmContext: LLVMContextRef,
    val module: LLVMModuleRef,
    val builder: LLVMBuilderRef
)
