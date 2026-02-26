package com.digia.digiaui.framework.actions.setState

import android.content.Context
import android.content.res.loader.ResourcesProvider
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.StateScopeContext
import com.digia.digiaui.framework.utils.JsonLike

/**
 * StateUpdate represents a single state variable update
 */
data class StateUpdate(
    val stateName: String,
    val newValue: ExprOr<Any>?
) {
    fun toJson(): JsonLike {
        return mapOf(
            "stateName" to stateName,
            "newValue" to newValue?.toJson()
        )
    }

    companion object {
        fun fromJson(json: JsonLike): StateUpdate {
            return StateUpdate(
                stateName = json["stateName"] as? String ?: "",
                newValue = json["newValue"]?.let { ExprOr.fromValue<Any>(it) }
            )
        }
    }
}

/**
 * SetState Action
 *
 * Updates state values in the specified StateContext.
 * Can update multiple state variables at once.
 */
data class SetStateAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val stateContextName: String = "page",
    val updates: List<StateUpdate> = emptyList(),
    val rebuild: ExprOr<Boolean>? = null
) : Action {
    override val actionType = ActionType.SET_STATE

    override fun toJson(): JsonLike {
        return mapOf(
            "type" to actionType.value,
            "stateContextName" to stateContextName,
            "updates" to updates.map { it.toJson() },
            "rebuild" to rebuild?.toJson()
        )
    }

    companion object {
        fun fromJson(json: JsonLike): SetStateAction {
            val updatesList = (json["updates"] as? List<*>)?.mapNotNull { update ->
                (update as? Map<*, *>)?.let { map ->
                    @Suppress("UNCHECKED_CAST")
                    StateUpdate.fromJson(map as JsonLike)
                }
            } ?: emptyList()

            return SetStateAction(
                stateContextName = json["stateContextName"] as? String ?: "page",
                updates = updatesList,
                rebuild = json["rebuild"]?.let { ExprOr.fromValue<Boolean>(it) }
            )
        }
    }
}

/** SetState Action Processor */
class SetStateProcessor : ActionProcessor<SetStateAction>() {
    override suspend fun execute(
        context: Context,
        action: SetStateAction,
        scopeContext: ScopeContext?,
        stateContext: com.digia.digiaui.framework.state.StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ): Any? {
        if (stateContext == null) {
            println("Warning: SetStateAction executed without a StateContext")
            return null
        }

        // Determine if we should notify after each update
        val shouldRebuild = action.rebuild?.evaluate<Boolean>(scopeContext) ?: true

        // Update each state variable from the updates list
        action.updates.forEach { update ->
            val value = update.newValue?.evaluate<Any>(scopeContext)
            stateContext.set(update.stateName, value, notify = shouldRebuild)
        }

        return null
    }
}
