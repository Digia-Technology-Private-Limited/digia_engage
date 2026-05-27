package com.digia.engage.framework.actions.navigation

import android.content.Context
import com.digia.engage.framework.UIResources
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionId
import com.digia.engage.framework.actions.base.ActionProcessor
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.navigation.NavigationManager
import com.digia.engage.framework.state.StateContext
import com.digia.engage.framework.utils.JsonLike

/**
 * PopPage Action
 *
 * Pops the current page from the navigation stack. Returns to the previous page.
 */
data class PopPageAction(
        override var actionId: ActionId? = null,
        override var disableActionIf: ExprOr<Boolean>? = null,
        val maybe: ExprOr<Boolean>? = null,
        val result: ExprOr<Any>? = null
) : Action {
    override val actionType = ActionType.NAVIGATE_BACK

    override fun toJson(): JsonLike {
        return mapOf(
                "type" to actionType.value,
                "maybe" to maybe?.toJson(),
                "result" to result?.toJson()
        )
    }

    companion object {
        fun fromJson(json: JsonLike): PopPageAction {
            return PopPageAction(
                    maybe = json["maybe"]?.let { ExprOr.fromValue<Boolean>(it) },
                    result = json["result"]?.let { ExprOr.fromValue<Any>(it) }
            )
        }
    }
}

/** PopPage Action Processor */
class PopPageProcessor : ActionProcessor<PopPageAction>() {
    override suspend fun execute(
            context: Context,
            action: PopPageAction,
            scopeContext: ScopeContext?,
            stateContext: StateContext?,
            resourcesProvider: UIResources?,
            id: String
    ): Any? {
        // Evaluate maybe flag
        val maybe = action.maybe?.evaluate<Boolean>(scopeContext) ?: true

        // Evaluate result if provided
        val result = action.result?.evaluate<Any>(scopeContext)

        println("PopPageAction: Pop with result: $result, maybe: $maybe")

        // Store pop request in navigation manager
        NavigationManager.pop(result, maybe)

        return null
    }
}
