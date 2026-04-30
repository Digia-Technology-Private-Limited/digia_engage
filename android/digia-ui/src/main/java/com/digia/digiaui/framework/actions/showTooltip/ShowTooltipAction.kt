package com.digia.digiaui.framework.actions.showTooltip

import android.content.Context
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.tooltip.TooltipPosition
import com.digia.digiaui.framework.tooltip.toTooltipPosition
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.utils.asSafe

/**
 * Action: SHOW_TOOLTIP
 *
 * JSON shape (inside "data" block):
 * {
 *   "componentId": "tooltip_bubble",
 *   "targetKey": "discount_badge",   // matches Modifier.coachmarkLabel("...")
 *   "args": { "text": "20% OFF!" },
 *   "position": "above",             // above | below | left | right | auto
 *   "waitForResult": false,
 *   "onFinish": { … }
 * }
 */
data class ShowTooltipAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val componentId: String,
    val targetKey: String? = null,
    val args: JsonLike? = null,
    val position: TooltipPosition = TooltipPosition.AUTO,
    /** Hex color for the arrow caret, e.g. "#FFFFFF". Should match the DUI bubble background. */
    val arrowColorHex: String = "#FFFFFF",
    val waitForResult: Boolean = false,
    val onFinish: ActionFlow? = null,
) : Action {

    override val actionType = ActionType.SHOW_TOOLTIP

    override fun toJson(): JsonLike = mapOf(
        "type"          to actionType.value,
        "componentId"   to componentId,
        "targetKey"     to targetKey,
        "args"          to args,
        "position"      to position.name.lowercase(),
        "arrowColor"    to arrowColorHex,
        "waitForResult" to waitForResult,
        "onFinish"      to onFinish?.toJson(),
    )

    companion object {
        fun fromJson(json: JsonLike) = ShowTooltipAction(
            componentId   = json["componentId"] as? String ?: "",
            targetKey     = json["targetKey"] as? String,
            args          = asSafe<JsonLike>(json["args"]),
            position      = (json["position"] as? String).toTooltipPosition(),
            arrowColorHex = json["arrowColor"] as? String ?: "#FFFFFF",
            waitForResult = json["waitForResult"] as? Boolean ?: false,
            onFinish      = asSafe<JsonLike>(json["onFinish"])?.let { ActionFlow.fromJson(it) },
        )
    }
}

class ShowTooltipProcessor : ActionProcessor<ShowTooltipAction>() {
    override suspend fun execute(
        context: Context,
        action: ShowTooltipAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {
        val manager = DigiaUIManager.getInstance().tooltipManager
        if (manager == null) {
            android.util.Log.e("ShowTooltip", "tooltipManager not initialized")
            return null
        }
        manager.show(
            componentId   = action.componentId,
            args          = action.args,
            targetKey     = action.targetKey,
            position      = action.position,
            arrowColorHex = action.arrowColorHex,
            onDismiss   = { result ->
                if (action.waitForResult && action.onFinish != null) {
                    val ctx = com.digia.digiaui.framework.expr.DefaultScopeContext(
                        variables = mapOf("result" to result),
                        enclosing = scopeContext,
                    )
                    ActionExecutor().execute(
                        context           = context,
                        actionFlow        = action.onFinish,
                        scopeContext      = ctx,
                        stateContext      = stateContext,
                        resourcesProvider = resourcesProvider,
                    )
                }
            },
        )
        return null
    }
}

// ─── DISMISS ─────────────────────────────────────────────────────────────────

data class DismissTooltipAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
) : Action {
    override val actionType = ActionType.DISMISS_TOOLTIP
    override fun toJson(): JsonLike = mapOf("type" to actionType.value)
    companion object { fun fromJson(@Suppress("UNUSED_PARAMETER") json: JsonLike) = DismissTooltipAction() }
}

class DismissTooltipProcessor : ActionProcessor<DismissTooltipAction>() {
    override suspend fun execute(
        context: Context,
        action: DismissTooltipAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {
        DigiaUIManager.getInstance().tooltipManager?.dismiss()
        return null
    }
}
