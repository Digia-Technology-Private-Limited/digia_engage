package com.digia.digiaui.framework.state

import androidx.compose.runtime.Composable
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext

/**
 * Expression scope context that integrates with StateContext.
 * 
 * This provides access to state values in expressions using NON-REACTIVE reads.
 * This is correct for:
 * - Action expressions (e.g., onClick handlers)
 * - Validation rules
 * - Data transformations
 * - Any non-UI logic
 * 
 * For reactive state in widgets, widgets should directly call:
 * `stateContext.observe(key)` in their @Composable Render method.
 */
class StateScopeContext(
    private val state: StateContext,
    variables: Map<String, Any?> = emptyMap(),
    enclosing: ScopeContext? = null
) : DefaultScopeContext(
    name = state.namespace ?: "",
    variables = variables,
    enclosing = enclosing
) {

    /**
     * Non-reactive value lookup.
     * Used for expressions, actions, validation.
     * NEVER triggers recomposition.
     */
    override fun getValue(key: String): Pair<Boolean, Any?> {

        // `state` or namespace returns snapshot (plain data)
        if (key == "state" || key == name) {
            return true to state.snapshot()
        }

        // Direct non-reactive lookup
        if (state.containsKey(key)) {
            return true to state.get(key)
        }

        return super.getValue(key)
    }

}

