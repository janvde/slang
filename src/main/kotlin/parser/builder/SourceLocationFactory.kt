package nl.endevelopment.parser.builder

import nl.endevelopment.ast.SourceLocation
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

class SourceLocationFactory {
    fun from(ctx: ParserRuleContext): SourceLocation {
        val token = ctx.start
        return SourceLocation(token.line, token.charPositionInLine + 1)
    }

    fun from(token: Token): SourceLocation {
        return SourceLocation(token.line, token.charPositionInLine + 1)
    }
}
