package com.digia.digiaui.framework.expression

import com.digia.digiaexpr.Expression
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.utils.to

/// Evaluates a string expression and converts the result to the specified type.
///
/// This function assumes that the input has already been validated and is a
/// valid string expression. It evaluates the expression using the provided
/// context and attempts to convert the result to type T.
///
/// [expression] The string expression to evaluate.
/// [scopeContext] The context for expression evaluation.
/// Returns the evaluated result converted to type T, or null if conversion fails.
inline fun <reified T : Any> evaluateExpression(
    expression: String,
    scopeContext: ScopeContext?
): T? {
    return Expression.eval(expression, scopeContext)?.to<T>()
}

inline fun <reified T : Any> evaluate(
    expression: Any?,
    scopeContext: ScopeContext? = null,
    noinline decoder: ((Any?) -> T?)? = null
): T? {
    try {


        if (expression == null) return null

        if (!hasExpression(expression)) {
            return decoder?.invoke(expression) ?: expression.to<T>()
        }

        return Expression.eval(expression as String, scopeContext)?.to<T>()
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun hasExpression(expression: Any?): Boolean {
    return expression is String && Expression.hasExpression(expression)
}


fun evaluateNestedExpressions(data: Any?, context: ScopeContext?): Any? {
    if (data == null) return null

    // Evaluate primitive types directly
    if (data is String || data is Number || data is Boolean) {
        return evaluate<Any>(data, scopeContext = context)
    }

    // Recursively evaluate Map entries
    if (data is Map<*, *>) {
        return data.map { (key, value) ->
            val evaluatedKey = evaluate<String>(key, scopeContext = context) ?: key
            val evaluatedValue = evaluateNestedExpressions(value, context)
            evaluatedKey to evaluatedValue
        }.toMap()
    }

    // Recursively evaluate List elements
    if (data is List<*>) {
        return data.map { evaluateNestedExpressions(it, context) }
    }

    // Return unchanged for unsupported types
    return data
}
