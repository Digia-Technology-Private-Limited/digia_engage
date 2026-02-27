package com.digia.digiaui.framework.actions.fireevent

import android.content.Context
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.analytics.AnalyticEvent
import com.digia.digiaui.framework.analytics.AnalyticsHandler
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Fire Event Action
 *
 * Sends analytics events to the registered analytics handler.
 * Events can contain expressions that will be evaluated before sending.
 *
 * @param events List of analytic events to fire
 */
data class FireEventAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val events: List<AnalyticEvent>
) : Action {
    override val actionType = ActionType.FIRE_EVENT

    override fun toJson(): JsonLike =
        mapOf(
            "type" to actionType.value,
            "events" to events.map { it.toJson() }
        )

    companion object {
        fun fromJson(json: JsonLike): FireEventAction {
            val eventsData = json["events"] as? List<*> ?: emptyList<Any>()
            val events = eventsData.mapNotNull { eventData ->
                (eventData as? JsonLike)?.let { AnalyticEvent.fromJson(it) }
            }
            return FireEventAction(events = events)
        }
    }
}

/** Processor for fire event action */
class FireEventProcessor : ActionProcessor<FireEventAction>() {
    override suspend fun execute(
        context: Context,
        action: FireEventAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourceProvider: UIResources?,
        id: String
    ): Any? {
        AnalyticsHandler.execute(
            context = context,
            events = action.events,
            scopeContext = scopeContext
        )
        return null
    }
}