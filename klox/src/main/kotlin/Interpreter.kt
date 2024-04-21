import TokenType.*
import ast.Expr
import ast.Stmt

class Interpreter: Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    private val environment = Environment()

    fun interpret(statements: List<Stmt?>) {
        try {
            for (statement in statements) {
                if (statement != null) {
                    execute(statement)
                }
            }
        } catch (error: RuntimeError) {
            OutputHandler.runtimeError(error)
        }
    }

    override fun visitAssignExpr(expr: Expr.Assign): Any? {
        val value = evaluate(expr.value)
        environment.assign(expr.name, value)
        return value
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        val asNumbers = fun (predicate: (Double, Double) -> Any?): Any? {
            checkNumberOperands(expr.operator, left, right)
            return predicate(left as Double, right as Double)
        }

        return when (expr.operator.type) {
            BANG_EQUAL -> !isEqual(left, right)
            EQUAL_EQUAL -> isEqual(left, right)
            GREATER -> asNumbers { a, b -> a > b }
            GREATER_EQUAL -> asNumbers { a, b -> a >= b }
            LESS -> asNumbers { a, b -> a < b }
            LESS_EQUAL -> asNumbers { a, b -> a <= b }
            MINUS -> asNumbers { a, b -> a - b }
            SLASH -> asNumbers { a, b -> a / b }
            STAR -> asNumbers { a, b -> a * b }
            PLUS -> {
                when {
                    left is Double && right is Double -> left + right
                    left is String && right is String -> left + right
                    else -> null // unreachable
                }
            }
            else -> null // unreachable
        }
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? {
        return evaluate(expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal): Any? {
        return expr.value
    }

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            BANG -> !isTruthy(right)
            MINUS -> {
                checkNumberOperand(expr.operator, right)
                -(right as Double)
            }
            else -> null
        }
    }

    override fun visitVariableExpr(expr: Expr.Variable): Any? {
        return environment.get(expr.name)
    }

    private fun evaluate(expr: Expr): Any? {
        return expr.accept(this)
    }

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        evaluate(stmt.expression)
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        val value = evaluate(stmt.expression)
        println(stringify(value))
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        var value: Any? = null
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer)
        }

        environment.define(stmt.name.lexeme, value)
    }

    private fun isTruthy(value: Any?): Boolean {
        if (value == null)
        {
            return false
        }

        if (value is Boolean) {
            return value
        }

        return true
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) {
            return true
        }

        if (a == null) {
            return false
        }

        return a == b
    }

    private fun stringify(value: Any?): String {
        if (value == null) {
            return "nil"
        }

        if (value is Double) {
            var text = value.toString()
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length - 2)
            }
            return text
        }

        return value.toString()
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand is Double) {
            return
        }

        throw RuntimeError(operator, "Operand must be a number.")
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) {
            return
        }

        throw RuntimeError(operator, "Operands must both be numbers.")
    }

    class RuntimeError(val token: Token, message: String): RuntimeException(message)
}