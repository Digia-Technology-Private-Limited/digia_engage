package com.digia.engage.framework.actions

import com.digia.engage.framework.actions.delay.DelayAction
import com.digia.engage.framework.actions.fireevent.FireEventAction
import com.digia.engage.framework.actions.executeCallBack.ExecuteCallBackAction
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.actions.openUrl.OpenUrlAction
import com.digia.engage.framework.actions.setState.SetStateAction
import com.digia.engage.framework.actions.showToast.ShowToastAction
import com.digia.engage.framework.actions.hideBottomSheet.HideBottomSheetAction
import com.digia.engage.framework.actions.dismissDialog.DismissDialogAction
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.utils.JsonLike


class ActionFactory {
    companion object {
        fun fromJson(json: JsonLike): Action? {
            // Extract action type
            val typeStr = json["type"] as? String ?: return null
            val actionType = try {
                ActionType.fromString(typeStr)
            } catch (e: IllegalArgumentException) {
                return null // Skip unknown action types
            }

            // Extract disable condition
            val disableActionIf = ExprOr.fromValue<Boolean>(json["disableActionIf"])

            // Extract action-specific data
            val actionData = (json["data"] as? JsonLike) ?: emptyMap()

            // Create action based on type
            val action: Action? = when (actionType) {
                ActionType.SHOW_TOAST -> ShowToastAction.fromJson(actionData)
                ActionType.SET_STATE -> SetStateAction.fromJson(actionData)
                ActionType.OPEN_URL -> OpenUrlAction.fromJson(actionData)
                ActionType.HIDE_BOTTOM_SHEET -> HideBottomSheetAction.fromJson(actionData)
                ActionType.DISMISS_DIALOG -> DismissDialogAction.fromJson(actionData)
                ActionType.DELAY -> DelayAction.fromJson(actionData)
                ActionType.FIRE_EVENT -> FireEventAction.fromJson(actionData)
                ActionType.EXECUTE_CALLBACK -> ExecuteCallBackAction.fromJson(actionData)
                // Other action types are not used in Engage SDK
                else -> null
            }

            // Set disableActionIf on the created action
            return action?.also {
                it.disableActionIf = disableActionIf
            }
        }
    }
}
