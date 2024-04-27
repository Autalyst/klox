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
    private val isInitializer: Boolean,
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
            if (isInitializer) {
                return closure.getAt(0, "this")
            }
            return returnThrowable.value
        }

        if (isInitializer) {
            return closure.getAt(0, "this")
        }

        return null
    }

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment, isInitializer)
    }

    override fun toString(): String {
        return "<fn ${declaration.name.lexeme}>"
    }
}

class LoxClass(
    val name: String,
    private val methods: Map<String, LoxFunction>
): LoxCallable {
    override fun arity(): Int {
        val initializer = findMethod("init") ?: return 0

        return initializer.arity()
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val instance = LoxInstance(this)
        val initializer = findMethod("init")
        initializer
            ?.bind(instance)
            ?.call(interpreter, arguments)

        return instance
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

        val method = klass.findMethod(name.lexeme)
        if (method != null) {
            return method.bind(this)
        }

        throw Interpreter.RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString(): String {
        return "${klass.name} instance"
    }
}