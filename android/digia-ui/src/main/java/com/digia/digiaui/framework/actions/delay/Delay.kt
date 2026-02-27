package com.digia.digiaui.framework.actions.delay

import android.content.Context
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.delay

/**
 * Delay Action
 *
 * Delays execution for a specified duration in milliseconds.
 *
 * @param durationInMs The duration to delay in milliseconds (can be an expression)
 */
data class DelayAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val durationInMs: ExprOr<Int>?
) : Action {
    override val actionType = ActionType.DELAY

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "durationInMs" to durationInMs?.toJson()
        )

    companion object {
        fun fromJson(json: JsonLike): DelayAction {
            return DelayAction(
                durationInMs = ExprOr.fromValue(json["durationInMs"])
            )
        }
    }
}

/** Processor for delay action */
class DelayProcessor : ActionProcessor<DelayAction>() {
    override suspend fun execute(
        context: Context,
        action: DelayAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourceProvider: UIResources?,
        id: String
    ): Any? {
        val durationInMs = action.durationInMs?.evaluate<Int>(scopeContext)

        if (durationInMs != null) {
            // Now properly suspends the action flow
            delay(durationInMs.toLong())
        }
        return null
    }
}