import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size > 1) {
        println("Usage: klox [script]")
        exitProcess(64)
    } else if (args.size == 1) {
        runFile(args[0])
    } else {
        runPrompt()
    }
}

fun runFile(path: String) {
    val byteArray = Files.readAllBytes(Paths.get(path))
    val fileAsString = String(byteArray, Charset.defaultCharset())
    run(fileAsString)

    if (hadError) {
        exitProcess(65)
    }

    if (hadRuntimeError) {
        exitProcess(70)
    }
}

fun runPrompt() {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)

    while(true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)
        hadError = false
    }
}

fun run(source: String) {
    val tokens = Scanner(source).scanTokens()
    val statements = Parser(tokens).parse()

    // detect syntax error
    if (hadError) {
        return
    }

    Interpreter().interpret(statements)
//    val astPrinter = ast.AstPrinter()
//    println(astPrinter.print(expression))
}