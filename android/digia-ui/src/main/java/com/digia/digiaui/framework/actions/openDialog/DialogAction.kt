package com.digia.digiaui.framework.actions.openDialog

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.utils.asSafe
import resourceColor

/**
 * ShowDialog Action
 *
 * Displays a dialog modal with specified content.
 */
data class ShowDialogAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val viewData: ExprOr<JsonLike>?,
    val barrierDismissible: ExprOr<Boolean>?,
    val barrierColor: ExprOr<String>?,
    val style: JsonLike?,
    val waitForResult: Boolean = false,
    val onResult: ActionFlow?
) : Action {
    override val actionType = ActionType.SHOW_DIALOG

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "viewData" to viewData?.toJson(),
            "barrierDismissible" to barrierDismissible?.toJson(),
            "barrierColor" to barrierColor?.toJson(),
            "style" to style,
            "waitForResult" to waitForResult,
            "onResult" to onResult?.toJson()
        )

    companion object {
        fun fromJson(json: JsonLike): ShowDialogAction {
            return ShowDialogAction(
                viewData = ExprOr.fromJson<JsonLike>(json["viewData"]),
                barrierDismissible = ExprOr.fromJson<Boolean>(json["barrierDismissible"]),
                barrierColor = ExprOr.fromJson<String>(json["barrierColor"]),
                style = toJsonLike(json["style"]),
                waitForResult = json["waitForResult"] as? Boolean ?: false,
                onResult = toJsonLike(json["onResult"])?.let { ActionFlow.fromJson(it) }
            )
        }

        private fun toJsonLike(value: Any?): JsonLike? {
            val map = value as? Map<*, *> ?: return null
            return map.entries
                .mapNotNull { (k, v) -> (k as? String)?.let { it to v } }
                .toMap()
        }
    }
}

/** Processor for show dialog action */
class ShowDialogProcessor : ActionProcessor<ShowDialogAction>() {
    override suspend fun execute(
        context: Context,
        action: ShowDialogAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ): Any? {
        val viewData = asSafe<JsonLike>(action.viewData?.deepEvaluate(scopeContext)) ?: return null
        val componentId = viewData["id"] as? String
        if (componentId.isNullOrEmpty()) return null

        val args = viewData["args"] as? JsonLike

        val barrierDismissible = action.barrierDismissible?.evaluate(scopeContext) ?: true
        val style: JsonLike = action.style ?: emptyMap()
        val barrierColorStr =
            ExprOr.fromJson<String>(style["barrierColor"])?.evaluate(scopeContext)
                ?: action.barrierColor?.evaluate<String>(scopeContext)

        val dialogManager = com.digia.digiaui.init.DigiaUIManager.getInstance().dialogManager
            ?: return null

        dialogManager.show(
            componentId = componentId,
            args = args,
            barrierDismissible = barrierDismissible,
            barrierColor = barrierColorStr ?.let { token -> resourceColor(token, resourcesProvider) },
            onDismiss = { result ->
                if (action.waitForResult && action.onResult != null) {
                        val resultContext = com.digia.digiaui.framework.expr.DefaultScopeContext(
                            variables = mapOf("result" to result),
                            enclosing = scopeContext
                        )
                        com.digia.digiaui.framework.actions.ActionExecutor().execute(
                            context = context,
                            actionFlow = action.onResult,
                            scopeContext = resultContext,
                            stateContext = stateContext,
                            resourcesProvider = resourcesProvider
                        )
                }
            }
        )

        return null
    }

}
