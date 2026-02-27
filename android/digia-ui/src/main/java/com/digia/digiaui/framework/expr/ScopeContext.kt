package com.digia.digiaui.framework.expr

import com.digia.digiaexpr.context.ExprContext

/**
 * Scope context for evaluating expressions Contains variables and functions available during
 * rendering
 */
abstract class ScopeContext : ExprContext() {
    abstract fun copyAndExtend(
        newVariables: Map<String, Any?>
    ): ScopeContext
}



