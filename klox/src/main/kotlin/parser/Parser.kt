package parser

import OutputHandler
import parser.TokenType.*
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
    // declaration → classDecl | funDecl | varDecl | statement
    // funDecl →  "fun" function ;
    private fun declaration(): Stmt? {
        try {
            return when {
                match(CLASS) -> classDeclaration()
                match(FUN) -> function("function")
                match(VAR) -> varDeclaration()
                else -> statement()
            }
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    // classDecl → "class" IDENTIFIER "{" function* "}" ;
    private fun classDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect class name.")
        consume(LEFT_BRACE, "Expect '{' before class body.")

        val methods: MutableList<Stmt.Function> = ArrayList()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method") as Stmt.Function)
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.")
        return Stmt.Class(name, methods)
    }

    // function → IDENTIFIER "(" parameters? ")" block;
    private fun function(kind: String): Stmt {
        val name: Token = consume(IDENTIFIER, "Expect $kind name.")
        consume(LEFT_PAREN, "Expect '(' after $kind name.")

        val parameters = mutableListOf<Token>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters.")
                }

                parameters.add(
                    consume(IDENTIFIER, "Expect parameter name.")
                )
            } while (match(COMMA))
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.")

        consume(LEFT_BRACE, "Expect '{' before $kind body.")
        val body = block()
        return Stmt.Function(name, parameters, body)
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

    // statement → exprStmt | forStmt | ifStmt | printStmt | returnStmt | whileStmt | block
    private fun statement(): Stmt {
        return when {
            match(FOR) -> forStatement()
            match(IF) -> ifStatement()
            match(PRINT) -> printStatement()
            match(RETURN) -> returnStatement()
            match(WHILE) -> whileStatement()
            match(LEFT_BRACE) -> Stmt.Block(block())
            else -> expressionStatement()
        }
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

        val condition: Expr = if (!check(SEMICOLON)) expression() else Expr.Literal(true)
        consume(SEMICOLON, "Expect ';' after loop condition.")

        val increment: Expr? = if(!check(RIGHT_PAREN)) expression() else null
        consume(RIGHT_PAREN, "Expect ')' after for clauses.")

        var body = statement()
        increment?.let {
            body = Stmt.Block(listOf(body, Stmt.Expression(it)))
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
        val elseBranch: Stmt? = if (match(ELSE)) statement() else null

        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(SEMICOLON, "Expect ';' after value.")
        return Stmt.Print(value)
    }

    // returnStmt → "return" expression? ";" ;
    private fun returnStatement(): Stmt {
        val keyword = previous()
        val value: Expr? = if (!check(SEMICOLON)) expression() else null
        consume(SEMICOLON, "Expect ';' after return value.")
        return Stmt.Return(keyword, value)
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

    // assignment → ( call "." )? IDENTIFIER "=" assignment | logic_or ;
    private fun assignment(): Expr {
        val expr: Expr = logicOr()

        if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is Expr.Variable) {
                val name = expr.name
                return Expr.Assign(name, value)
            } else if (expr is Expr.Get) {
                return Expr.Set(expr.instance, expr.name, value)
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

    // call → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
    private fun call(): Expr {
        var expr = primary()

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = arguments(expr);
            } else if (match(DOT)) {
                val name = consume(IDENTIFIER, "Expect property name after '.'.")
                expr = Expr.Get(expr, name)
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
