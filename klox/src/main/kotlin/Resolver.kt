import ast.Expr
import ast.Stmt
import interpreter.Interpreter
import parser.Token
import java.util.*

class Resolver(
    private val interpreter: Interpreter
): Expr.Visitor<Unit>, Stmt.Visitor<Unit> {
    private enum class FunctionType {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    private enum class ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    private val scopes = Stack<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE

    override fun visitAssignExpr(expr: Expr.Assign) {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
    }

    override fun visitBinaryExpr(expr: Expr.Binary) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitCallExpr(expr: Expr.Call) {
        resolve(expr.callee)
        expr.arguments.forEach(::resolve)
    }

    override fun visitGetExpr(expr: Expr.Get) {
        resolve(expr.instance)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping) {
        resolve(expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal) {
        // no-op
    }

    override fun visitLogicalExpr(expr: Expr.Logical) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitSetExpr(expr: Expr.Set) {
        resolve(expr.value)
        resolve(expr.instance)
    }

    override fun visitSuperExpr(expr: Expr.Super) {
        if (currentClass == ClassType.NONE) {
            OutputHandler.error(expr.keyword, "Can't use 'super' outside of a class.")
        } else if (currentClass != ClassType.SUBCLASS) {
            OutputHandler.error(expr.keyword, "Can't use 'super' in a class with no superclass.")
        }

        resolveLocal(expr, expr.keyword)
    }

    override fun visitThisExpr(expr: Expr.This) {
        if (currentClass == ClassType.NONE) {
            OutputHandler.error(expr.keyword, "Can't use 'this' outside of a class.")
        }

        resolveLocal(expr, expr.keyword)
    }

    override fun visitUnaryExpr(expr: Expr.Unary) {
        resolve(expr.right)
    }

    override fun visitVariableExpr(expr: Expr.Variable) {
        if (scopes.isNotEmpty() && scopes.peek()[expr.name.lexeme] == false) {
            OutputHandler.error(expr.name, "Can't read local variable in its own initializer.")
        }

        resolveLocal(expr, expr.name)
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        scoped {
            resolve(stmt.statements)
        }
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS

        declare(stmt.name)
        define(stmt.name)

        val withOptionalSuperclass = stmt.superclass?.let {
            fun (predicate: () -> Unit) {
                if (it.name.lexeme == stmt.name.lexeme) {
                    OutputHandler.error(stmt.superclass.name, "A class cannot inherit from itself.")
                }
                currentClass = ClassType.SUBCLASS
                resolve(it)
                scopes.peek().put("super", true)

                scoped {
                    scopes.peek()["super"] = true
                    predicate()
                }
            }
        } ?: { x -> x()}

        withOptionalSuperclass {
            scoped {
                scopes.peek()["this"] = true

                stmt.methods.forEach {
                    val declaration = if (it.name.lexeme == "init") {
                        FunctionType.INITIALIZER
                    } else {
                        FunctionType.METHOD
                    }
                    resolveFunction(it, declaration)
                }
            }
        }

        currentClass = enclosingClass
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        resolve(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        declare(stmt.name)
        define(stmt.name)

        resolveFunction(stmt, FunctionType.FUNCTION)
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        resolve(stmt.condition)
        resolve(stmt.thenBranch)
        stmt.elseBranch?.let(::resolve)
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        resolve(stmt.expression)
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if (currentFunction == FunctionType.NONE) {
            OutputHandler.error(stmt.keyword, "Can't return from top-level code.")
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                OutputHandler.error(stmt.keyword, "Can't return a value from an initializer.")
            }

            resolve(stmt.value)
        }
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        declare(stmt.name)
        stmt.initializer?.let(::resolve)
        define(stmt.name)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }

    fun resolve(statements: List<Stmt?>) {
        statements.forEach(::resolve)
    }

    private fun resolve(statement: Stmt?) {
        statement?.accept(this)
    }

    private fun resolve(expression: Expr) {
        expression.accept(this)
    }

    private fun resolveLocal(expression: Expr, name: Token) {
        // starts at innermost scope and works outwards
        for (i in (scopes.size - 1) downTo 0) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expression, (scopes.size - 1) - i)
                return
            }
        }
    }

    private fun resolveFunction(
        function: Stmt.Function,
        type: FunctionType
    ) {
        val enclosingFunction = currentFunction
        currentFunction = type

        scoped {
            function.params.forEach {
                declare(it)
                define(it)
            }
            resolve(function.body)
        }

        currentFunction = enclosingFunction
    }

    private fun scoped(predicate: () -> Unit) {
        scopes.push(HashMap<String, Boolean>())

        predicate()

        scopes.pop()
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) {
            return
        }

        val scope = scopes.peek()
        if (scope.containsKey(name.lexeme)) {
            OutputHandler.error(name, "Already a variable with this name in this scope.")
        }
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) {
            return
        }

        scopes.peek()[name.lexeme] = true
    }
}