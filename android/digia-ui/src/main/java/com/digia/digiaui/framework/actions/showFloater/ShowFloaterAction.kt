package com.digia.digiaui.framework.actions.showFloater

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
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.utils.asSafe

/**
 * Action: SHOW_FLOATER
 *
 * JSON shape (inside "data" block):
 * {
 *   "componentId": "floater_card",
 *   "args": { "cta": "Claim offer" },
 *   "anchorX": 0.8,
 *   "anchorY": 0.5,
 *   "draggable": true,
 *   "waitForResult": false,
 *   "onFinish": { … }
 * }
 */
data class ShowFloaterAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val componentId: String,
    val args: JsonLike? = null,
    val anchorX: Float = 0.8f,
    val anchorY: Float = 0.5f,
    val draggable: Boolean = true,
    val waitForResult: Boolean = false,
    val onFinish: ActionFlow? = null,
) : Action {

    override val actionType = ActionType.SHOW_FLOATER

    override fun toJson(): JsonLike = mapOf(
        "type"          to actionType.value,
        "componentId"   to componentId,
        "args"          to args,
        "anchorX"       to anchorX,
        "anchorY"       to anchorY,
        "draggable"     to draggable,
        "waitForResult" to waitForResult,
        "onFinish"      to onFinish?.toJson(),
    )

    companion object {
        fun fromJson(json: JsonLike) = ShowFloaterAction(
            componentId   = json["componentId"] as? String ?: "",
            args          = asSafe<JsonLike>(json["args"]),
            anchorX       = (json["anchorX"] as? Number)?.toFloat() ?: 0.8f,
            anchorY       = (json["anchorY"] as? Number)?.toFloat() ?: 0.5f,
            draggable     = json["draggable"] as? Boolean ?: true,
            waitForResult = json["waitForResult"] as? Boolean ?: false,
            onFinish      = asSafe<JsonLike>(json["onFinish"])?.let { ActionFlow.fromJson(it) },
        )
    }
}

class ShowFloaterProcessor : ActionProcessor<ShowFloaterAction>() {
    override suspend fun execute(
        context: Context,
        action: ShowFloaterAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {
        val manager = DigiaUIManager.getInstance().floaterManager
        if (manager == null) {
            android.util.Log.e("ShowFloater", "floaterManager not initialized")
            return null
        }
        manager.show(
            componentId = action.componentId,
            args        = action.args,
            anchorX     = action.anchorX,
            anchorY     = action.anchorY,
            draggable   = action.draggable,
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

data class DismissFloaterAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
) : Action {
    override val actionType = ActionType.DISMISS_FLOATER
    override fun toJson(): JsonLike = mapOf("type" to actionType.value)
    companion object { fun fromJson(@Suppress("UNUSED_PARAMETER") json: JsonLike) = DismissFloaterAction() }
}

class DismissFloaterProcessor : ActionProcessor<DismissFloaterAction>() {
    override suspend fun execute(
        context: Context,
        action: DismissFloaterAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {
        DigiaUIManager.getInstance().floaterManager?.dismiss()
        return null
    }
}
