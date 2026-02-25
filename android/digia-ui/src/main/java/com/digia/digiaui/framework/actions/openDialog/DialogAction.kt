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
import kotlinx.coroutines.launch
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
            "waitForResult" to waitForResult,
            "onResult" to onResult?.toJson()
        )

    companion object {
        fun fromJson(json: JsonLike): ShowDialogAction {
            return ShowDialogAction(
                viewData = ExprOr.fromJson<JsonLike>(json["viewData"]),
                barrierDismissible = ExprOr.fromJson<Boolean>(json["barrierDismissible"]),
                barrierColor = ExprOr.fromJson<String>(json["barrierColor"]),
                waitForResult = json["waitForResult"] as? Boolean ?: false,
                onResult = (json["onResult"] as? JsonLike)?.let { ActionFlow.fromJson(it) }
            )
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
        // Evaluate viewData to get component/view ID and arguments
        val viewData = action.viewData?.evaluate<JsonLike>(scopeContext)
        if (viewData == null) {
            android.util.Log.e("ShowDialog", "viewData is null")
            return null
        }

        val componentId = viewData["id"] as? String
        if (componentId.isNullOrEmpty()) {
            android.util.Log.e("ShowDialog", "componentId is empty")
            return null
        }

        val args = viewData["args"] as? JsonLike

        // Evaluate dialog properties
        val barrierDismissible = action.barrierDismissible?.evaluate(scopeContext) ?: true
        val barrierColorStr = action.barrierColor?.evaluate<String>(scopeContext)

        // Get the dialog manager from DigiaUIManager
        val dialogManager = com.digia.digiaui.init.DigiaUIManager.getInstance().dialogManager

        // Show the dialog
        dialogManager?.show(
            componentId = componentId,
            args = args,
            barrierDismissible = barrierDismissible,
            barrierColor = barrierColorStr ?.let { token -> resourceColor(token, resourcesProvider) },
            onDismiss = { result ->
                // Handle result if waitForResult is true
                if (action.waitForResult && action.onResult != null) {
                    // Execute onResult callback with the result
//                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
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
//                    }
                }
            }
        )

        return null
    }

}
