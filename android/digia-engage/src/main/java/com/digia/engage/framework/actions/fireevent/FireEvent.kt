package com.digia.engage.framework.actions.fireevent

import android.content.Context
import com.digia.engage.framework.UIResources
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionId
import com.digia.engage.framework.actions.base.ActionProcessor
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.analytics.AnalyticEvent
import com.digia.engage.framework.analytics.AnalyticsHandler
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.state.StateContext
import com.digia.engage.framework.utils.JsonLike

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