package com.digia.engage.framework.actions.hideBottomSheet

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
import kotlinx.coroutines.launch


/**
 * HideBottomSheet Action
 *
 * Hides the currently displayed bottom sheet.
 */
data class HideBottomSheetAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null
) : Action {
    override val actionType = ActionType.HIDE_BOTTOM_SHEET

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value
        )

    companion object {
        fun fromJson(json: JsonLike): HideBottomSheetAction {
            return HideBottomSheetAction()
        }
    }
}

class HideBottomSheetProcessor : ActionProcessor<HideBottomSheetAction>() {
    override suspend fun execute(
        context: Context,
        action: HideBottomSheetAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ): Any? {
        val bottomSheetManager = com.digia.engage.init.DigiaUIManager.getInstance().bottomSheetManager
        bottomSheetManager?.dismiss()
        return null
    }
}