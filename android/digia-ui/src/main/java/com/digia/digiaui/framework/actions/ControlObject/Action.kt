package com.digia.digiaui.framework.actions.ControlObject

import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Action for controlling data type objects through method commands.
 * 
 * This action evaluates a data type instance and invokes a method on it
 * using the MethodBindingRegistry, passing evaluated arguments.
 */
data class ControlObjectAction(
    override var actionId: ActionId? = null,
    override var disableActionIf: ExprOr<Boolean>? = null,
    val dataType: ExprOr<Any>?,
    val method: String,
    val args: Map<String, ExprOr<Any>?>? = null
) : Action {
    override val actionType = ActionType.CONTROL_OBJECT

    override fun toJson(): JsonLike = mapOf(
        "type" to actionType.value,
        "dataType" to dataType?.toJson(),
        "method" to method,
        "args" to args?.mapValues { it.value?.toJson() }
    )

    companion object {
        fun fromJson(json: JsonLike): ControlObjectAction {
            val argsJson = json["args"] as? Map<*, *>
            val args = argsJson?.mapNotNull { (key, value) ->
                val k = key as? String ?: return@mapNotNull null
                k to ExprOr.fromJson<Any>(value)
            }?.toMap()

            return ControlObjectAction(
                dataType = ExprOr.fromJson<Any>(json["dataType"]),
                method = json["method"] as? String ?: "",
                args = args
            )
        }
    }
}