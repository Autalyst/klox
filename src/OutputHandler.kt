var hasError = false

fun error(lineNumber: Int, message: String) {
    report(lineNumber, "", message)
}

fun report(lineNumber: Int, where: String, message: String) {
    System.err.println("[line $lineNumber] Error$where: $message")
    hasError = true
}