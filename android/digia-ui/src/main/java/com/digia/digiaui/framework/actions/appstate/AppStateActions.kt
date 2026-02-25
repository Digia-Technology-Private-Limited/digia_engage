package com.digia.digiaui.framework.actions.appstate

import android.content.Context
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.appstate.DUIAppState
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.utils.JsonLike



/**
 * SetAppState Action
 *
 * Updates global application state.
 * @param updates Map of state variable names to their new values (can be expressions)
 */
data class SetAppStateAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val updates: Map<String, ExprOr<Any>?>
) : Action {
    override val actionType = ActionType.SET_APP_STATE

    override fun toJson(): JsonLike {
        return mapOf(
            "type" to actionType.value,
            "updates" to updates.mapValues { it.value?.toJson() }
        )
    }

    companion object {
        fun fromJson(json: JsonLike): SetAppStateAction? {

            val updatesList = json["updates"] as? List<*> ?: return null

            val updatesMap = updatesList.mapNotNull { update ->
                val map = update as? Map<*, *> ?: return@mapNotNull null
                val key = (map["stateName"] ?: map["key"]) as? String ?: return@mapNotNull null
                val value = (map["newValue"] ?: map["value"])?.let { ExprOr.fromValue<Any>(it) }
                key to value
            }.toMap()

            return SetAppStateAction(updates = updatesMap)
        }
    }
}

/**
 * SetAppState Action Processor
 */
class SetAppStateProcessor : ActionProcessor<SetAppStateAction>() {
    override suspend fun execute(
        context: Context,
        action: SetAppStateAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ): Any? {
        try {
            for ((key, exprOrValue) in action.updates) {
                // Evaluate the expression to get the actual value
                val value = exprOrValue?.evaluate<Any>(scopeContext)

                // Update the global DUIAppState singleton
                DUIAppState.instance.update(key, value)
                println("SetAppStateAction: Set $key = $value")
            }
            return true
        } catch(e: Exception) {
            println("Error executing SetAppStateAction: ${e.message}")
            return false
        }
    }
}