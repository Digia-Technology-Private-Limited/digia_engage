package com.digia.engage.internal.ui

import android.content.Context
import com.digia.engage.framework.UIResources
import com.digia.engage.framework.actions.ActionProcessorFactory
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionProcessor
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.state.StateContext
import com.digia.engage.internal.DigiaInstance

/**
 * Action factory scoped to a rendered nudge. The default `HideBottomSheet` /
 * `DismissDialog` processors target the DSL-driven `DigiaUIManager` bottom sheet /
 * dialog managers, which the nudge overlay does not use. Here we intercept those two
 * action types and route them to the nudge overlay's own dismissal. Every other action
 * type (including `Action.openUrl` for deep_link / open_url buttons) delegates to the
 * default factory unchanged.
 */
internal class NudgeActionProcessorFactory : ActionProcessorFactory() {
    override fun getProcessor(action: Action, registry: MethodBindingRegistry): ActionProcessor<*> =
        when (action.actionType) {
            ActionType.HIDE_BOTTOM_SHEET, ActionType.DISMISS_DIALOG -> NudgeDismissProcessor()
            else -> super.getProcessor(action, registry)
        }
}

private class NudgeDismissProcessor : ActionProcessor<Action>() {
    override suspend fun execute(
        context: Context,
        action: Action,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {
        DigiaInstance.markNudgeDismissed()
        return null
    }
}
