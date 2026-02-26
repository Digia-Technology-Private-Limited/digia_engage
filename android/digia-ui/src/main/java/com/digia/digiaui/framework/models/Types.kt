package com.digia.digiaui.framework.models

import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.expression.evaluateExpression
import com.digia.digiaui.framework.expression.evaluateNestedExpressions
import com.digia.digiaui.framework.utils.to

/**
 * Expression wrapper - can be either a literal value or an expression string
 * Supports both old format (@{expression}) and new format (Map with "expr" key)
 */
class ExprOr<T : Any> private constructor(
     val value: Any
) {
    /** Determines if the value is an expression */
    val isExpr: Boolean = isExpression(value)

    companion object {
        /**
         * Creates an ExprOr instance from a value
         */
        fun <T : Any> fromValue(value: Any?): ExprOr<T>? {
            if (value == null) return null
            return ExprOr(value)
        }

        /**
         * Creates an ExprOr instance from JSON representation
         */
        fun <T : Any> fromJson(json: Any?): ExprOr<T>? {
            if (json == null) return null

            // Handle both old and new formats
            if (json is Map<*, *>) {
                if (json.containsKey("expr")) {
                    // New format: {"expr": "expression"}
                    return ExprOr(json)
                } else {
                    // Map without 'expr' key - treat as regular value
                    return ExprOr(json)
                }
            }

            // Old format or primitive value
            return ExprOr(json)
        }

        /**
         * Helper method to determine if a value is an expression
         */
        internal fun isExpression(value: Any): Boolean {
            if (value is Map<*, *> && value.containsKey("expr")) {
                // New format: {"expr": "expression"}
                return true
            }
            // Old format: "${expression}"
            if (value is String) {
                return value.startsWith("\${") && value.endsWith("}")
            }
            return false
        }
    }

    /**
     * Evaluates the value, returning a result of type T
     */
     inline fun <reified T : Any>  evaluate(
        scopeContext: ScopeContext?,
        noinline decoder: ((Any) -> T?)? = null
    ): T? {
         try {


             if (isExpr) {
                 val expressionString: String = when {
                     value is Map<*, *> && value.containsKey("expr") -> {
                         // New format: extract expression from map
                         value["expr"] as String
                     }

                     value is String && value.startsWith("@{") && value.endsWith("}") -> {
                         // Old format: extract expression from @{...}
                         value.substring(2, value.length - 1)
                     }

                     else -> value.toString()
                 }

                 // Evaluate the expression using the expression utility
                 return evaluateExpression<T>(expressionString, scopeContext)
             } else {
                 // If it's not an expression, use decoder or cast it to R
                 return if(decoder!=null){
                        decoder.invoke(value)
                    } else {
                        value.to<T>()
                 }
             }
         } catch (e: Exception) {
             e.printStackTrace()
             return null
         }
    }

    /**
     * Evaluates the value deeply, resolving nested expressions.
     * This method performs a deep evaluation of the value, resolving any nested
     * expressions within complex data structures like maps and lists.
     */
    fun deepEvaluate(scopeContext: ScopeContext?): Any? {
        if (isExpr) {
            val valueToEvaluate: Any = when {
                value is Map<*, *> && value.containsKey("expr") -> {
                    // New format: extract expression from map
                    value["expr"] as Any
                }
                else -> {
                    // Old format: use the value directly
                    value
                }
            }


            // For now, return the value as-is
            return evaluateNestedExpressions(valueToEvaluate, scopeContext)
        } else {
            return evaluateNestedExpressions(value, scopeContext)
        }
    }

    /**
     * Converts the ExprOr instance to a JSON-compatible representation
     */
    fun toJson(): Any = value

    override fun toString(): String = "ExprOr($value)"
}
