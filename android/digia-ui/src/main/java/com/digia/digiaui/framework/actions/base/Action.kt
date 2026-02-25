package com.digia.digiaui.framework.actions.base

import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.utils.asSafe

/** Action types enum - defines all available actions */
enum class ActionType(val value: String) {
    SHOW_TOAST("Action.showToast"),
    SET_STATE("Action.setState"),
    REBUILD_STATE("Action.rebuildState"),
    NAVIGATE_TO_PAGE("Action.navigateToPage"),
    NAVIGATE_BACK("Action.pop"),
    SHOW_DIALOG("Action.openDialog"),
    SHOW_BOTTOM_SHEET("Action.showBottomSheet"),
    HIDE_BOTTOM_SHEET("Action.hideBottomSheet"),
    DISMISS_DIALOG("Action.dismissDialog"),
    CALL_REST_API("Action.callRestApi"),
    OPEN_URL("Action.openUrl"),
    SET_APP_STATE("Action.setAppState"),
    CONTROL_OBJECT("Action.controlObject"),
    SHARE_CONTENT("Action.share"),
    DELAY("Action.delay"),
    COPY_TO_CLIPBOARD("Action.copyToClipboard"),
    POST_MESSAGE("Action.handleDigiaMessage"),
    FIRE_EVENT("Action.fireEvent"),
    EXECUTE_CALLBACK("Action.executeCallback"),
    CALL_EXTERNAL_METHOD("Action.handleDigiaMessage");

    companion object {
        fun fromString(value: String): ActionType {
            return ActionType.entries.firstOrNull {
                it.value.equals(value, ignoreCase = true)
            }?: SHOW_TOAST
//                    ?: throw IllegalArgumentException("Unknown action type: $value")
        }
    }
}

/** Action identifier */
data class ActionId(val id: String)

/** Base interface for all actions */
interface Action {
    val actionType: ActionType
    var actionId: ActionId?
    var disableActionIf: ExprOr<Boolean>?

    fun toJson(): JsonLike
}

/** Action flow - sequence of actions to execute */
/** Action flow - sequence of actions to execute */
data class ActionFlow(
    val actions: List<Action>,
    val inkWell: Boolean = true // Added property
) {
    fun toJson(): JsonLike {
        return mapOf(
            "steps" to actions.map { it.toJson() },
            "inkWell" to inkWell
        )
    }

    companion object {
        fun fromJson(json: JsonLike?): ActionFlow? {
            if(json == null) return null
            // Dart uses 'inkWell' key
            val inkWell = (json["inkWell"] as? Boolean) ?: true

            // Dart looks for 'steps' key
            val actionsList = json["steps"] as? List<*> ?: emptyList<Any>()

            val actions = actionsList.mapNotNull { actionJson ->
                val jsonMap = asSafe<JsonLike>( actionJson) ?: return@mapNotNull null
                com.digia.digiaui.framework.actions.ActionFactory.fromJson(jsonMap)
            }

            if (actions.isEmpty()) return null

            return ActionFlow(actions, inkWell)
        }

        fun empty() = ActionFlow(emptyList())
    }
}
