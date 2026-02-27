package com.digia.digiaui.framework.actions.executeCallBack

import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Argument update for callback execution
 */
data class ArgUpdate(
    val argName: String,
    val argValue: ExprOr<Any>?
) {
    fun toJson(): JsonLike {
        return mapOf(
            "argName" to argName,
            "argValue" to argValue?.toJson()
        )
    }

    companion object {
        fun fromJson(json: JsonLike): ArgUpdate {
            return ArgUpdate(
                argName = json["argName"] as? String ?: "",
                argValue = json["argValue"]?.let { ExprOr.fromJson(it) }
            )
        }
    }
}

/**
 * Action to execute a callback (ActionFlow) with updated arguments
 * 
 * This action allows executing a callback action with modified parameters.
 * The actionName should contain an ActionFlow (either as JSON string or Map),
 * and argUpdates specify parameter overrides.
 */
data class ExecuteCallBackAction(
    val actionName: ExprOr<Any>?,
    val argUpdates: List<ArgUpdate> = emptyList(),
    override val actionType: ActionType = ActionType.EXECUTE_CALLBACK,
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null
) : Action {
    
    override fun toJson(): JsonLike {
        return mapOf(
            "actionType" to actionType.value,
            "actionName" to actionName?.toJson(),
            "argUpdates" to argUpdates.map { it.toJson() },
            "disableActionIf" to disableActionIf?.toJson()
        )
    }

    companion object {
        fun fromJson(json: JsonLike): ExecuteCallBackAction {
            // Support backward compatibility with 'page' key (similar to Flutter)
            val actionName = json["actionName"] ?: json["page"]
            
            val argUpdatesList = (json["argUpdates"] as? List<*>)?.mapNotNull {
                (it as? JsonLike)?.let { ArgUpdate.fromJson(it) }
            } ?: emptyList()

            return ExecuteCallBackAction(
                actionName = actionName?.let { ExprOr.fromJson(it) },
                argUpdates = argUpdatesList,
             )
        }
    }
}