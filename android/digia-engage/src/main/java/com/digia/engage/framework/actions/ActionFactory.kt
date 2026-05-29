package com.digia.engage.framework.actions

import com.digia.engage.framework.actions.share.ShareAction
import com.digia.engage.framework.actions.delay.DelayAction
import com.digia.engage.framework.actions.CopyToClipboard.CopyToClipboardAction
import com.digia.engage.framework.actions.postmessage.PostMessageAction
import com.digia.engage.framework.actions.fireevent.FireEventAction
import com.digia.engage.framework.actions.executeCallBack.ExecuteCallBackAction
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.actions.callRestApi.CallRestApiAction
//import com.digia.engage.framework.actions.callRestApi.CallRestApiAction
import com.digia.engage.framework.actions.ControlObject.ControlObjectAction
import com.digia.engage.framework.actions.appstate.SetAppStateAction
import com.digia.engage.framework.actions.navigation.GotoPageAction
import com.digia.engage.framework.actions.navigation.PopPageAction
import com.digia.engage.framework.actions.openUrl.OpenUrlAction
import com.digia.engage.framework.actions.rebuildState.RebuildStateAction
import com.digia.engage.framework.actions.setState.SetStateAction
import com.digia.engage.framework.actions.showBottomSheet.ShowBottomSheetAction
import com.digia.engage.framework.actions.openDialog.ShowDialogAction
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
                ActionType.REBUILD_STATE -> RebuildStateAction.fromJson(actionData)
                ActionType.NAVIGATE_TO_PAGE -> GotoPageAction.fromJson(actionData)
                ActionType.NAVIGATE_BACK -> PopPageAction.fromJson(actionData)
                ActionType.OPEN_URL -> OpenUrlAction.fromJson(actionData)
               ActionType.CALL_REST_API -> CallRestApiAction.fromJson(actionData)
                ActionType.SHOW_BOTTOM_SHEET -> ShowBottomSheetAction.fromJson(actionData)
                ActionType.SHOW_DIALOG -> ShowDialogAction.fromJson(actionData)
                ActionType.HIDE_BOTTOM_SHEET -> HideBottomSheetAction.fromJson(actionData)
                ActionType.DISMISS_DIALOG -> DismissDialogAction.fromJson(actionData)
                ActionType.CONTROL_OBJECT -> ControlObjectAction.fromJson(actionData)
                ActionType.SHARE_CONTENT -> ShareAction.fromJson(actionData)
                ActionType.DELAY -> DelayAction.fromJson(actionData)
                ActionType.COPY_TO_CLIPBOARD -> CopyToClipboardAction.fromJson(actionData)
                ActionType.POST_MESSAGE -> PostMessageAction.fromJson(actionData)
                ActionType.FIRE_EVENT -> FireEventAction.fromJson(actionData)
                ActionType.EXECUTE_CALLBACK -> ExecuteCallBackAction.fromJson(actionData)
                ActionType.CALL_EXTERNAL_METHOD -> PostMessageAction.fromJson(actionData)
                ActionType.SET_APP_STATE -> SetAppStateAction.fromJson(actionData)
//                ActionType.SET_APP_STATE -> SetAppStateAction.fromJson(actionData)
//                ActionType.GET_APP_STATE -> GetAppStateAction.fromJson(actionData)
//                ActionType.RESET_APP_STATE -> ResetAppStateAction.fromJson(actionData)
                // Other action types will be implemented later
                else -> null
            }

            // Set disableActionIf on the created action
            return action?.also {
                it.disableActionIf = disableActionIf
            }
        }
    }
}
