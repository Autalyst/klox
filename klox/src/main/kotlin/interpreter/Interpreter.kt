package interpreter

import LoxCallable
import LoxClass
import LoxFunction
import LoxInstance
import OutputHandler
import parser.Token
import parser.TokenType.*
import ast.Expr
import ast.Stmt

class Interpreter : Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    val globals = Environment()
    private var environment = globals

    private val locals: MutableMap<Expr, Int> = HashMap()

    init {
        globals.define("clock", object: LoxCallable {
            override fun arity(): Int {
                return 0
            }

            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
               return System.currentTimeMillis() / 1000.0
            }

            override fun toString(): String {
                return "<native fn>"
            }
        })
    }

    fun interpret(statements: List<Stmt?>) {
        try {
            statements.forEach(this::execute)
        } catch (error: RuntimeError) {
            OutputHandler.runtimeError(error)
        }
    }

    fun executeBlock(
        statements: List<Stmt?>,
        environment: Environment
    ) {
        val previous = this.environment
        try {
            this.environment = environment
            statements.forEach(this::execute)
        } finally {
            this.environment = previous
        }
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    override fun visitAssignExpr(expr: Expr.Assign): Any? {
        val value = evaluate(expr.value)

        val distance = locals[expr]
        if (distance != null) {
            environment.assignAt(distance, expr.name, value)
        } else {
            globals.assign(expr.name, value)
        }

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
                    left is String || right is String -> stringify(left) + stringify(right)
                    else -> null // unreachable
                }
            }
            else -> null // unreachable
        }
    }

    override fun visitCallExpr(expr: Expr.Call): Any? {
        val callee = evaluate(expr.callee)

        val arguments = expr.arguments.map { evaluate(it) }

        return when (callee) {
            is LoxCallable -> {
                if (arguments.size != callee.arity()) {
                    throw RuntimeError(expr.paren, "Expected ${callee.arity()} arguments but got ${arguments.size}.")
                }
                callee.call(this, arguments)
            }
            else -> throw RuntimeError(expr.paren, "Can only call functions and classes.")
        }
    }

    override fun visitGetExpr(expr: Expr.Get): Any? {
        val instance: Any? = evaluate(expr.instance)
        if (instance is LoxInstance) {
            return instance.get(expr.name)
        }

        throw RuntimeError(expr.name, "Only instances have properties")
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? {
        return evaluate(expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal): Any? {
        return expr.value
    }

    override fun visitLogicalExpr(expr: Expr.Logical): Any? {
        val left = evaluate(expr.left)

        if (expr.operator.type == OR) {
            if (isTruthy(left)) {
                return left
            }
        } else {
            if (!isTruthy(left)) {
                return left
            }
        }

        return evaluate(expr.right)
    }

    override fun visitSetExpr(expr: Expr.Set): Any? {
        val instance = evaluate(expr.instance)

        if (instance !is LoxInstance) {
            throw RuntimeError(expr.name, "Only instances have fields.")
        }

        val value = evaluate(expr.value)
        instance.set(expr.name, value)
        return value
    }

    override fun visitThisExpr(expr: Expr.This): Any? {
        return lookUpVariable(expr.keyword, expr)
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
        return lookUpVariable(expr.name, expr)
    }

    private fun lookUpVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return distance?.let { environment.getAt(distance, name.lexeme) } ?: globals.get(name)
    }

    private fun evaluate(expr: Expr): Any? {
        return expr.accept(this)
    }

    private fun execute(stmt: Stmt?) {
        stmt?.accept(this)
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        executeBlock(stmt.statements, Environment(environment))
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val superclass = stmt.superclass?.let {
            val superclass = evaluate(it)
            if (superclass !is LoxClass) {
                throw RuntimeError(stmt.superclass.name, "Superclass must be a class")
            }
            superclass
        }

        environment.define(stmt.name.lexeme, null)

        val methods = stmt.methods.associate {
            it.name.lexeme to LoxFunction(it, environment, it.name.lexeme == "init")
        }

        val klass = LoxClass(stmt.name.lexeme, superclass, methods)
        environment.assign(stmt.name, klass)
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        evaluate(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val function = LoxFunction(stmt, environment, false)
        environment.define(stmt.name.lexeme, function)
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch)
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch)
        }
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        val value = evaluate(stmt.expression)
        println(stringify(value))
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        val value = stmt.value?.let(this::evaluate)

        // I agree with the book that while using an exception for control flow
        // is bad form. The alternative in a recursive interpreter like this
        // would be hard to implement, confusing to read and follow, and hard
        // to reason about. Some of the features of the exception are disabled to
        // try and lower the overhead of this.
        throw Return(value)
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        val value: Any? = stmt.initializer?.let(this::evaluate)
        environment.define(stmt.name.lexeme, value)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
    }

    private fun isTruthy(value: Any?): Boolean {
        return when(value) {
            is Boolean -> value
            null -> false
            else -> true
        }
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
    class Return(val value: Any?): RuntimeException(null, null, false, false)
}
