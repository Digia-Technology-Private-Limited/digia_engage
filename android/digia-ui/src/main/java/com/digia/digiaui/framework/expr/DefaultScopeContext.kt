package com.digia.digiaui.framework.expr

import com.digia.digiaexpr.context.ExprContext


/// A basic implementation of ExprContext that can be used as-is or extended.
open class DefaultScopeContext(
    override val name: String = "",
    variables: Map<String, Any?>,
    enclosing: ExprContext? = null
) : ScopeContext() {
    private var _enclosing: ExprContext? = enclosing
    private val _variables: MutableMap<String, Any?> = variables.toMutableMap()

    override var enclosing: ExprContext?
        get() = _enclosing
        set(value) {
            _enclosing = value
        }

    override fun getValue(key: String): Pair<Boolean, Any?> {
        if (_variables.containsKey(key)) {
            return Pair(
                true,
                _variables[key]
            )
        }
        return _enclosing?.getValue(key) ?: Pair(false, null)
    }

    override fun copyAndExtend(newVariables: Map<String, Any?>): ScopeContext {
        return DefaultScopeContext(
            name = name,
            variables = _variables + newVariables,
            enclosing = _enclosing
        )
    }
}