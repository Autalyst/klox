package interpreter

import parser.Token

class Environment(
    val enclosing: Environment? = null
) {
    private val values = HashMap<String, Any?>()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun ancestor(distance: Int): Environment {
        var environment: Environment = this
        for (i in 0..<distance) {
            environment = environment.enclosing!!
        }

        return environment
    }

    fun get(name: Token): Any? {
        if (values.containsKey(name.lexeme)) {
            return values[name.lexeme]
        }

        if (enclosing != null) {
            return enclosing.get(name)
        }

        throw Interpreter.RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }

    fun getAt(distance: Int, name: String): Any? {
        return ancestor(distance).values[name]
    }

    fun assign(name: Token, value: Any?) {
        if (values.containsKey(name.lexeme)) {
            values[name.lexeme] = value
            return
        }

        if (enclosing != null) {
            enclosing.assign(name, value)
            return
        }

        throw Interpreter.RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance).values[name.lexeme] = value
    }
}