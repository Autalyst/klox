import TokenType.*

class Scanner(
    private val source: String
) {
    private val tokens: MutableList<Token> = ArrayList()

    private var start = 0
    private var current = 0
    private var line = 1

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }

        tokens.add(Token(EOF, "", null, line))
        return tokens
    }

    private fun scanToken() {
        val c = advance()

        when(c) {
            '(' -> addToken(LEFT_PAREN)
            ')' -> addToken(RIGHT_PAREN)
            '{' -> addToken(LEFT_BRACE)
            '}' -> addToken(RIGHT_BRACE)
            ',' -> addToken(COMMA)
            '.' -> addToken(DOT)
            '-' -> addToken(MINUS)
            '+' -> addToken(PLUS)
            ';' -> addToken(SEMICOLON)
            '*' -> addToken(STAR)
            '!' -> addToken(
                if (match('=')) BANG_EQUAL
                else BANG
            )
            '=' -> addToken(
                if (match('=')) EQUAL_EQUAL
                else EQUAL
            )
            '<' -> addToken(
                if (match('=')) LESS_EQUAL
                else LESS
            )
            '>' -> addToken(
                if (match('=')) GREATER_EQUAL
                else GREATER
            )
            '/' -> {
                if (match('/')) {
                    // comment goes to end of line, discard line
                    while (peek() != '\n' && !isAtEnd()) {
                        advance()
                    }
                } else {
                    addToken(SLASH)
                }
            }
            ' ',
            '\r',
            '\t' -> {}
            '\n' -> line++
            '"' -> string()
            else -> {
                if (isDigit(c)) {
                    number()
                } else if (isAlpha(c)) {
                    identifier()
                } else {
                    OutputHandler.error(line, "Unexpected character: $c")
                }
            }
        }
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') {
                line++
            }
            advance()
        }

        if (isAtEnd()) {
            OutputHandler.error(line, "Unterminated string encountered")
            return
        }

        advance() // the closing double quote of the string

        // trim the surrounding quotes
        val value = source.substring(start + 1, current - 1)
        addToken(STRING, value)
    }

    private fun number() {
        while(isDigit(peek())) {
            advance()
        }

        // look for fractional part
        if (peek() == '.' && isDigit(peekNext())) {
            // consume the "."
            advance()

            while(isDigit(peek())) {
                advance()
            }
        }

        addToken(NUMBER, source.substring(start, current).toDouble())
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) {
            advance()
        }

        val text = source.substring(start, current)
        val type: TokenType = keywords[text] ?: IDENTIFIER

        addToken(type)
    }

    private fun isAlphaNumeric(c: Char): Boolean {
        return isAlpha(c) || isDigit(c)
    }

    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    private fun isAlpha(c: Char): Boolean {
        return c in 'a'..'z' ||
            c in 'A'..'Z' ||
            c == '_'
    }

    private fun isAtEnd(): Boolean {
        return current >= source.length
    }

    private fun advance(): Char {
        return source[current++]
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) {
            return false
        }

        if (source[current] != expected) {
            return false
        }

        current++
        return true
    }

    private fun peek(): Char {
        if (isAtEnd()) {
            return '\u0000'
        }

        return source[current]
    }

    private fun peekNext(): Char {
        if (current + 1 >= source.length) {
            return '\u0000'
        }

        return source[current + 1]
    }

    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    companion object {
        private val keywords: Map<String, TokenType> = hashMapOf(
            Pair("and", AND),
            Pair("class", CLASS),
            Pair("else", ELSE),
            Pair("false", FALSE),
            Pair("for", FOR),
            Pair("fun", FUN),
            Pair("if", IF),
            Pair("nil", NIL),
            Pair("or", OR),
            Pair("print", PRINT),
            Pair("return", RETURN),
            Pair("super", SUPER),
            Pair("this", THIS),
            Pair("true", TRUE),
            Pair("var", VAR),
            Pair("while", WHILE)
        )
    }
}