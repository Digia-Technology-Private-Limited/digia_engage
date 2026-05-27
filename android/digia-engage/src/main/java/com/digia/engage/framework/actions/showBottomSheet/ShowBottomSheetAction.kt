package com.digia.engage.framework.actions.showBottomSheet

import android.content.Context
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionFlow
import com.digia.engage.framework.actions.base.ActionId
import com.digia.engage.framework.actions.base.ActionProcessor
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.state.StateContext
import com.digia.engage.framework.utils.JsonLike
import com.digia.engage.framework.utils.NumUtil
import com.digia.engage.utils.asSafe
import kotlinx.coroutines.launch


/**
 * ShowBottomSheet Action
 *
 * Displays a bottom sheet modal with specified content.
 */
data class ShowBottomSheetAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val viewData: ExprOr<JsonLike>?,
    val waitForResult: Boolean = false,
    val style: JsonLike?,
    val onResult: ActionFlow?
) : Action {
    override val actionType = ActionType.SHOW_BOTTOM_SHEET

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "viewData" to viewData?.toJson(),
            "waitForResult" to waitForResult,
            "style" to style,
            "onResult" to onResult?.toJson()
        )

    companion object {
        fun fromJson(json: JsonLike): ShowBottomSheetAction {
            return ShowBottomSheetAction(
                viewData = ExprOr.fromJson<JsonLike>(json["viewData"]),
                waitForResult = json["waitForResult"] as? Boolean ?: false,
                style = toJsonLike(json["style"]),
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


/** Processor for show bottom sheet action */
class ShowBottomSheetProcessor : ActionProcessor<ShowBottomSheetAction>() {
    override suspend fun execute(
        context: Context,
        action: ShowBottomSheetAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: com.digia.engage.framework.UIResources?,
        id: String
    ): Any? {
        // Evaluate viewData to get component/view ID and arguments
        val viewData = asSafe<JsonLike>(action.viewData?.deepEvaluate(scopeContext))
        if (viewData == null) {
            android.util.Log.e("ShowBottomSheet", "viewData is null")
            return null
        }

        val componentId = viewData["id"] as? String
        if (componentId.isNullOrEmpty()) {
            android.util.Log.e("ShowBottomSheet", "componentId is empty")
            return null
        }

        val args = viewData["args"] as? JsonLike
        val style: JsonLike = action.style ?: emptyMap()

        // Extract style properties (support both literal values and ExprOr)
        val bgColorStr = evalStyleString(style["bgColor"], scopeContext)
        val barrierColorStr = evalStyleString(style["barrierColor"], scopeContext)
        val borderColorStr = evalStyleString(style["borderColor"], scopeContext)
        val borderWidth = evalStyleNumber(style["borderWidth"], scopeContext)?.toDouble()?.toFloat()
        val borderRadius = style["borderRadius"]

        val maxHeightRatio = (evalStyleNumber(style["maxHeight"], scopeContext)?.toDouble()) ?: 1.0
        val useSafeArea = (evalStyleBool(style["useSafeArea"], scopeContext) ?: true)

        // Get the bottom sheet manager from DigiaUIManager
        val bottomSheetManager = com.digia.engage.init.DigiaUIManager.getInstance().bottomSheetManager

        // Show the bottom sheet
        bottomSheetManager?.show(
            componentId = componentId,
            args = args,
            backgroundColor = bgColorStr,
            barrierColor = barrierColorStr,
            borderColor = borderColorStr,
            borderWidth = borderWidth,
            borderRadius = borderRadius,
            maxHeightRatio = maxHeightRatio.toFloat(),
            useSafeArea = useSafeArea,
            onDismiss = { result ->
                // Handle result if waitForResult is true
                if (action.waitForResult && action.onResult != null) {
                    // Execute onResult callback with the result
                        val resultContext = com.digia.engage.framework.expr.DefaultScopeContext(
                            variables = mapOf("result" to result),
                            enclosing = scopeContext
                        )
                        com.digia.engage.framework.actions.ActionExecutor().execute(
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

    private fun evalStyleString(value: Any?, scopeContext: ScopeContext?): String? {
        return ExprOr.fromJson<String>(value)?.evaluate(scopeContext)
            ?: value as? String
            ?: value?.toString()
    }

    private fun evalStyleNumber(value: Any?, scopeContext: ScopeContext?): Number? {
        return ExprOr.fromJson<Number>(value)?.evaluate<Number>(scopeContext)
            ?: (value as? Number)
            ?: (value as? String)?.let { NumUtil.toDouble(it) }
    }

    private fun evalStyleBool(value: Any?, scopeContext: ScopeContext?): Boolean? {
        return ExprOr.fromJson<Boolean>(value)?.evaluate<Boolean>(scopeContext)
            ?: (value as? Boolean)
    }
}
