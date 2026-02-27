package com.digia.digiaui.framework.actions.base

import android.content.Context
import android.content.res.loader.ResourcesProvider
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.state.StateContext

/** Action processor base class */
abstract class ActionProcessor<T : Action> {
    /** Execute the action - now suspend to support async operations like delay */
    abstract suspend fun execute(
        context: Context,
        action: T,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ): Any?
}
