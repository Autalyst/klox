import ast.Stmt
import interpreter.Environment
import interpreter.Interpreter
import parser.Token

interface LoxCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}

class LoxFunction(
    private val declaration: Stmt.Function,
    private val closure: Environment,
) : LoxCallable {
    override fun arity(): Int {
        return declaration.params.size
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        declaration.params.forEachIndexed {
            index, param -> environment.define(param.lexeme, arguments[index])
        }

        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnThrowable: Interpreter.Return) {
            return returnThrowable.value
        }

        return null
    }

    override fun toString(): String {
        return "<fn ${declaration.name.lexeme}>"
    }
}

class LoxClass(
    val name: String,
    val methods: Map<String, LoxFunction>
): LoxCallable {
    override fun arity(): Int {
        return 0
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        return LoxInstance(this)
    }

    fun findMethod(name: String): LoxFunction? {
        return methods.getOrDefault(name, null)
    }

    override fun toString(): String {
        return name
    }
}

class LoxInstance(
    private val klass: LoxClass,
    private val fields: MutableMap<String, Any?> = HashMap()
) {
    fun get(name: Token): Any? {
        if (fields.containsKey(name.lexeme)) {
            return fields[name.lexeme]
        }

        return klass.findMethod(name.lexeme) ?: throw Interpreter.RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString(): String {
        return "${klass.name} instance"
    }
}