var hasError = false

object OutputHandler {
    fun error(lineNumber: Int, message: String) {
        report(lineNumber, "", message)
    }

    fun error(token: Token, message: String) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message)
        } else {
            report(token.line, " at '${token.lexeme}'", message)
        }
    }

    fun report(lineNumber: Int, where: String, message: String) {
        System.err.println("[line $lineNumber] Error$where: $message")
        hasError = true
    }
}