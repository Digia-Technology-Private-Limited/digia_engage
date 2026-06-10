package com.digia.engage.framework.actions.fireevent

import android.content.Context
import com.digia.engage.framework.UIResources
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionId
import com.digia.engage.framework.actions.base.ActionProcessor
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.state.StateContext
import com.digia.engage.framework.utils.JsonLike

/** Minimal analytic event data class (framework/analytics was removed). */
data class AnalyticEvent(val name: String, val payload: Map<String, Any?>? = null) {
    companion object {
        fun fromJson(json: JsonLike): AnalyticEvent {
            val name = json["name"] as? String ?: ""
            val payload = json["payload"] as? Map<String, Any?>
            return AnalyticEvent(name = name, payload = payload)
        }
    }

    fun toJson(): JsonLike = mapOf("name" to name, "payload" to payload)
}

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
                (eventData as? Map<*, *>)?.let { map ->
                    @Suppress("UNCHECKED_CAST")
                    AnalyticEvent.fromJson(map as JsonLike)
                }
            }
            return FireEventAction(events = events)
        }
    }
}

class FireEventProcessor : ActionProcessor<FireEventAction>() {
    override suspend fun execute(
        context: Context,
        action: FireEventAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourceProvider: UIResources?,
        id: String
    ): Any? {
        // Fire events are handled at the Engage SDK level via DigiaCEPDelegate.
        // No-op here in the framework layer.
        return null
    }
}
