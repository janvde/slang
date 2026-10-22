package nl.endevelopment.interpreter.runtime

import nl.endevelopment.interpreter.Value

class ReturnSignal(val value: Value) : RuntimeException(null, null, false, false)
class BreakSignal : RuntimeException(null, null, false, false)
class ContinueSignal : RuntimeException(null, null, false, false)
