package com.digia.engage.framework.actions.dismissDialog

import android.content.Context
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionId
import com.digia.engage.framework.actions.base.ActionProcessor
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.state.StateContext
import com.digia.engage.framework.utils.JsonLike
import com.digia.engage.framework.UIResources


/**
 * DismissDialog Action
 *
 * Dismisses the currently displayed dialog.
 */
data class DismissDialogAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null
) : Action {
    override val actionType = ActionType.DISMISS_DIALOG

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value
        )

    companion object {
        fun fromJson(json: JsonLike): DismissDialogAction {
            return DismissDialogAction()
        }
    }
}

class DismissDialogProcessor : ActionProcessor<DismissDialogAction>() {
    override suspend fun execute(
        context: Context,
        action: DismissDialogAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ): Any? {
        return null
    }
}