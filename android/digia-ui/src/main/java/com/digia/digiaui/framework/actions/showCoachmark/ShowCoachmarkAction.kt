package com.digia.digiaui.framework.actions.showCoachmark

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.coachmark.CoachmarkRequest
import com.digia.digiaui.framework.coachmark.CoachmarkStep
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.utils.asSafe

/**
 * Action: SHOW_COACHMARK
 *
 * JSON shape (inside the "data" block):
 * {
 *   "steps": [
 *     {
 *       "targetKey": "checkout_btn",   // matches Modifier.coachmarkLabel("checkout_btn")
 *       "componentId": "coachmark_step1",
 *       "args": { "title": "Checkout", "description": "Tap here to pay" },
 *       "spotlightPadding": 16,
 *       "spotlightRadius": 8
 *     },
 *     { … }
 *   ],
 *   "dimColor": "#B4000000",
 *   "waitForResult": false,
 *   "onFinish": { … }   // optional ActionFlow
 * }
 */
data class ShowCoachmarkAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val steps: List<JsonLike>,
    val dimColor: String? = null,
    val waitForResult: Boolean = false,
    val onFinish: ActionFlow? = null,
) : Action {

    override val actionType = ActionType.SHOW_COACHMARK

    override fun toJson(): JsonLike = mapOf(
        "type"          to actionType.value,
        "steps"         to steps,
        "dimColor"      to dimColor,
        "waitForResult" to waitForResult,
        "onFinish"      to onFinish?.toJson(),
    )

    companion object {
        fun fromJson(json: JsonLike): ShowCoachmarkAction {
            @Suppress("UNCHECKED_CAST")
            val stepsList = (json["steps"] as? List<*>)
                ?.mapNotNull { asSafe<JsonLike>(it) }
                ?: emptyList()

            return ShowCoachmarkAction(
                steps         = stepsList,
                dimColor      = json["dimColor"] as? String,
                waitForResult = json["waitForResult"] as? Boolean ?: false,
                onFinish      = asSafe<JsonLike>(json["onFinish"])
                    ?.let { ActionFlow.fromJson(it) },
            )
        }
    }
}

/**
 * Processor for [ShowCoachmarkAction].
 * Mirrors [ShowBottomSheetProcessor] — resolves the manager from [DigiaUIManager]
 * and calls [CoachmarkManager.show].
 */
class ShowCoachmarkProcessor : ActionProcessor<ShowCoachmarkAction>() {

    override suspend fun execute(
        context: Context,
        action: ShowCoachmarkAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {

        val coachmarkManager = DigiaUIManager.getInstance().coachmarkManager
        if (coachmarkManager == null) {
            android.util.Log.e("ShowCoachmark", "coachmarkManager not initialized")
            return null
        }

        if (action.steps.isEmpty()) {
            android.util.Log.e("ShowCoachmark", "steps list is empty")
            return null
        }

        val steps = action.steps.map { CoachmarkStep.fromJson(it) }

        val dimColor = parseDimColor(action.dimColor)

        coachmarkManager.show(
            CoachmarkRequest(
                steps    = steps,
                dimColor = dimColor,
                onDismiss = { result ->
                    if (action.waitForResult && action.onFinish != null) {
                        val resultContext = com.digia.digiaui.framework.expr.DefaultScopeContext(
                            variables = mapOf("result" to result),
                            enclosing = scopeContext,
                        )
                        ActionExecutor().execute(
                            context           = context,
                            actionFlow        = action.onFinish,
                            scopeContext      = resultContext,
                            stateContext      = stateContext,
                            resourcesProvider = resourcesProvider,
                        )
                    }
                },
            )
        )

        return null
    }

    private fun parseDimColor(hex: String?): Color {
        if (hex == null) return Color(0xB4000000)
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            Color(0xB4000000)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DISMISS_COACHMARK action  (mirrors DismissDialogAction)
// ─────────────────────────────────────────────────────────────────────────────

data class DismissCoachmarkAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
) : Action {
    override val actionType = ActionType.DISMISS_COACHMARK

    override fun toJson(): JsonLike = mapOf("type" to actionType.value)

    companion object {
        fun fromJson(@Suppress("UNUSED_PARAMETER") json: JsonLike) = DismissCoachmarkAction()
    }
}

class DismissCoachmarkProcessor : ActionProcessor<DismissCoachmarkAction>() {
    override suspend fun execute(
        context: Context,
        action: DismissCoachmarkAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String,
    ): Any? {
        DigiaUIManager.getInstance().coachmarkManager?.dismiss()
        return null
    }
}
