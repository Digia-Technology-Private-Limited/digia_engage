package com.digia.engage.internal

// ── Arithmetic evaluator ──────────────────────────────────────────────────────
//
// Evaluates a simple arithmetic expression of variables, numeric literals and
// the operators + - * /.
//
//   expr    = term ( ('+' | '-') term )*
//   term    = unary ( ('*' | '/') unary )*
//   unary   = ('-')* primary
//   primary = NUM | ID       (identifiers resolve to numbers during tokenize)
//
// Returns null on any error (bad char, undeclared var, non-number type, empty
// value, non-numeric string, div-by-zero); on success formats to ≤4 dp with
// trailing zeros stripped.

internal fun evaluateArithmetic(expr: String, context: VariableContext): String? {
    val tokens = tokenize(expr, context) ?: return null
    val result = parseExpression(tokens) ?: return null
    return formatNumber(result)
}

// ── Token types ───────────────────────────────────────────────────────────────

private sealed interface Token {
    data class Num(val value: Double) : Token
    data class Op(val ch: Char) : Token   // '+' '-' '*' '/'
}

/** Identifiers resolve to numbers here; null on any invalid token or type/value failure. */
private fun tokenize(expr: String, context: VariableContext): List<Token>? {
    val result = mutableListOf<Token>()
    var i = 0
    val s = expr.replace(" ", "")
    while (i < s.length) {
        val ch = s[i]
        when {
            ch in "+-*/" -> {
                result += Token.Op(ch)
                i++
            }
            ch in '0'..'9' || ch == '.' -> {
                val start = i
                while (i < s.length && (s[i] in '0'..'9' || s[i] == '.')) i++
                val v = s.substring(start, i).toDoubleOrNull() ?: return null
                result += Token.Num(v)
            }
            ch in 'a'..'z' -> {
                val start = i
                while (i < s.length && (s[i] in 'a'..'z' || s[i] in '0'..'9' || s[i] == '_')) i++
                val name = s.substring(start, i)
                if (context.types[name] != "number") return null
                val v = context.values[name]?.toDoubleOrNull() ?: return null
                result += Token.Num(v)
            }
            else -> return null
        }
    }
    return result
}

// ── Recursive-descent parser (handles unary minus + BODMAS) ──────────────────

private class Parser(private val tokens: List<Token>) {
    var pos = 0

    fun peek(): Token? = tokens.getOrNull(pos)

    fun consume(): Token = tokens[pos++]

    /** expr = term ( ('+' | '-') term )* */
    fun parseExpression(): Double? {
        var left = parseTerm() ?: return null
        while (true) {
            val op = peek()
            if (op !is Token.Op || op.ch !in "+-") break
            consume()
            val right = parseTerm() ?: return null
            left = if (op.ch == '+') left + right else left - right
        }
        return left
    }

    /** term = unary ( ('*' | '/') unary )* */
    fun parseTerm(): Double? {
        var left = parseUnary() ?: return null
        while (true) {
            val op = peek()
            if (op !is Token.Op || op.ch !in "*/") break
            consume()
            val right = parseUnary() ?: return null
            left = when (op.ch) {
                '*' -> left * right
                '/' -> if (right == 0.0) return null else left / right
                else -> return null
            }
        }
        return left
    }

    /** unary = ('-')* primary */
    fun parseUnary(): Double? {
        var negate = false
        while (peek() == Token.Op('-')) {
            consume()
            negate = !negate
        }
        val v = parsePrimary() ?: return null
        return if (negate) -v else v
    }

    /** primary = NUM  (identifiers were already resolved to Num in tokenize) */
    fun parsePrimary(): Double? {
        val t = peek() ?: return null
        if (t !is Token.Num) return null
        consume()
        return t.value
    }
}

private fun parseExpression(tokens: List<Token>): Double? {
    if (tokens.isEmpty()) return null
    val parser = Parser(tokens)
    val result = parser.parseExpression() ?: return null
    // Make sure we consumed everything
    if (parser.pos != tokens.size) return null
    return result
}

// ── Number formatting ─────────────────────────────────────────────────────────

private fun formatNumber(value: Double): String {
    // Format to 4 decimal places then strip trailing zeros (and dot)
    val formatted = "%.4f".format(value)
    return formatted.trimEnd('0').trimEnd('.')
}
