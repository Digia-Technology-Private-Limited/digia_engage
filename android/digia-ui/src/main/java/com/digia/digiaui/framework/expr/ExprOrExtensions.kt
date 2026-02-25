//package com.digia.digiaui.framework.expr
//
//import com.digia.digiaui.framework.models.ExprOr
//
///**
// * Extension function to evaluate ExprOr<T> values
// * Handles both literal values and expressions
// * This is a convenience wrapper around the evaluate method
// */
//inline fun <reified T : Any> ExprOr<T>?.evaluate(scopeContext: ScopeContext?): T? {
//    if (this == null) return null
//    return this.evaluate<T>(scopeContext)
//}
