package com.digia.digiaui.framework.actions.dismissDialog

import android.content.Context
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.UIResources
import kotlinx.coroutines.launch


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
        val dialogManager = com.digia.digiaui.init.DigiaUIManager.getInstance().dialogManager
        dialogManager?.dismiss()
        return null
    }
}