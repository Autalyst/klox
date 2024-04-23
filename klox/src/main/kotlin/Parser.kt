import TokenType.*
import ast.Expr
import ast.Stmt

class Parser(
    private val tokens: List<Token>
) {
    private var current = 0

    // program → declaration* EOF
    fun parse(): List<Stmt?> {
        val statements = mutableListOf<Stmt?>()

        while(!isAtEnd()) {
            statements.add(declaration())
        }

        return statements
    }

    // -- PRODUCTIONS -- //
    // declaration → varDecl | statement
    private fun declaration(): Stmt? {
        try {
            if (match(VAR)) {
                return varDeclaration()
            }

            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    // varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
    private fun varDeclaration(): Stmt {
        val name: Token = consume(IDENTIFIER, "Expect variable name")
        var initializer: Expr? = null
        if (match(EQUAL)) {
            initializer = expression()
        }

        consume(SEMICOLON, "Expect ';' after variable declaration")
        return Stmt.Var(name, initializer)
    }

    // statement → exprStmt | forStmt | ifStmt | printStmt | whileStmt | block
    private fun statement(): Stmt {
        if (match(FOR)) {
            return forStatement()
        }

        if (match(IF)) {
            return ifStatement()
        }

        if (match(PRINT)) {
            return printStatement()
        }

        if (match(WHILE)) {
            return whileStatement()
        }

        if (match(LEFT_BRACE)) {
            return Stmt.Block(block())
        }

        return expressionStatement()
    }

    // forStmt → "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement ;
    private fun forStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'for'.")
        val initializer: Stmt? = if (match(SEMICOLON)) {
            null
        } else if (match(VAR)) {
            varDeclaration()
        } else {
            expressionStatement()
        }

        var condition: Expr? = if (!check(SEMICOLON)) expression() else null
        consume(SEMICOLON, "Expect ';' after loop condition.")

        val increment: Expr? = if(!check(RIGHT_PAREN)) expression() else null
        consume(RIGHT_PAREN, "Expect ')' after for clauses.")

        var body = statement()
        increment?.let {
            body = Stmt.Block(listOf(body, Stmt.Expression(it)))
        }

        if (condition == null) {
            condition = Expr.Literal(true)
        }
        body = Stmt.While(condition, body)

        initializer?.let {
            body = Stmt.Block(listOf(it, body))
        }

        return body
    }

    // ifStmt → "if" "(" expression ")" statement ( "else" statement )? ;
    private fun ifStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ') after 'if' condition.")

        val thenBranch = statement()
        var elseBranch: Stmt? = null
        if (match(ELSE)) {
            elseBranch = statement()
        }

        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(SEMICOLON, "Expect ';' after value.")
        return Stmt.Print(value)
    }

    // whileStmt → "while" "(" expression ")" statement ;
    private fun whileStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after condition.")
        val body = statement()

        return Stmt.While(condition, body)
    }

    private fun block(): List<Stmt?> {
        val statements = mutableListOf<Stmt?>()

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration())
        }

        consume(RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(SEMICOLON, "Expect ';' after expression.")
        return Stmt.Expression(expr)
    }

    // expression → assignment
    private fun expression(): Expr {
        return assignment()
    }

    // assignment → IDENTIFIER "=" assignment | logic_or ;
    private fun assignment(): Expr {
        val expr: Expr = logicOr()

        if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is Expr.Variable) {
                val name = expr.name
                return Expr.Assign(name, value)
            }

            error(equals, "Invalid assignment target.")
        }

        return expr
    }

    // logic_or → logic_and ( "or" logic_and )* ;
    private fun logicOr(): Expr {
        var expr: Expr = logicAnd()

        while (match(OR)) {
            val operator = previous()
            val right = logicAnd()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }

    // logic_and  → equality ( "and" equality )* ;
    private fun logicAnd(): Expr {
        var expr = equality()

        while (match(AND)) {
            val operator = previous()
            val right = equality()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
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

    // unary → ( "!" | "-" ) unary | call ;
    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return call()
    }

    // call → primary ( "(" arguments? ")" )* ;
    private fun call(): Expr {
        var expr = primary()

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = arguments(expr);
            } else {
                break
            }
        }

        return expr;
    }

    // primary → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")"
    private fun primary(): Expr {
        when {
            match(FALSE) -> return Expr.Literal(false)
            match(TRUE) -> return Expr.Literal(true)
            match(NIL) -> return Expr.Literal(null)
            match(NUMBER, STRING) -> return Expr.Literal(previous().literal)
            match(IDENTIFIER) -> return Expr.Variable(previous())
            match(LEFT_PAREN) -> {
                val expr = expression()
                consume(RIGHT_PAREN, "Expect ')' after expression.")
                return Expr.Grouping(expr)
            }
            else -> throw error(peek(), "Expect expression.")
        }
    }

    // arguments → expression ( "," expression )* ;
    private fun arguments(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) {
                    error(peek(), "Can't have more than 255 arguments.")
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        val paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")

        return Expr.Call(callee, paren, arguments)
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