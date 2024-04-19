import TokenType.*

class Parser(
    private val tokens: List<Token>
) {
    private var current = 0

    fun parse(): Expr? {
        return try {
            expression()
        } catch (error: ParseError) {
            null
        }
    }

    // -- PRODUCTIONS -- //

    // expression → equality
    private fun expression(): Expr {
        return equality()
    }

    // equality → comparison ( ( "!=" | "==" ) comparison )*
    private fun equality(): Expr {
        var expr = comparison()

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )*
    private fun comparison(): Expr {
        var expr = term()

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val operator = previous()
            val right = term()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    // term → factor ( ( "-" | "+" ) factor )*
    private fun term(): Expr {
        var expr = factor()

        while (match(MINUS, PLUS)) {
            val operator = previous()
            val right = factor()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    // factor → unary ( ( "/" | "*" ) unary )*
    private fun factor(): Expr {
        var expr = unary()

        while (match(SLASH, STAR)) {
            val operator = previous()
            val right = unary()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    // unary → ( "!" | "-" ) unary | primary
    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return primary()
    }

    // primary → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")"
    private fun primary(): Expr {
        when {
            match(FALSE) -> return Expr.Literal(false)
            match(TRUE) -> return Expr.Literal(true)
            match(NIL) -> return Expr.Literal(null)
            match(NUMBER, STRING) -> return Expr.Literal(previous().literal)
            match(LEFT_PAREN) -> {
                val expr = expression()
                consume(RIGHT_PAREN, "Expect ')' after expression.")
                return Expr.Grouping(expr)
            }
            else -> throw error(peek(), "Expect expression.")
        }
    }

    // -- utils -- //
    private fun match(vararg types: TokenType): Boolean {
        for(type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }

        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) {
            return advance()
        }

        throw error(peek(), message)
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) {
            return false
        }

        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) {
            current++
        }

        return previous()
    }

    private fun isAtEnd(): Boolean {
        return peek().type == EOF
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }

    private fun error(token: Token, message: String): ParseError {
        OutputHandler.error(token, message)
        return ParseError()
    }

    class ParseError: RuntimeException()

    private fun synchronize() {
        advance()

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return
            }

            when (peek().type) {
                CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN -> return
                else -> advance()
            }
        }
    }
}