package com.digia.digiaui.framework.actions

import com.digia.digiaui.framework.actions.ControlObject.ControlObjectProcessor
import com.digia.digiaui.framework.actions.share.ShareProcessor
import com.digia.digiaui.framework.actions.delay.DelayProcessor
import com.digia.digiaui.framework.actions.CopyToClipboard.CopyToClipboardProcessor
import com.digia.digiaui.framework.actions.postmessage.PostMessageProcessor
import com.digia.digiaui.framework.actions.fireevent.FireEventProcessor
import com.digia.digiaui.framework.actions.executeCallBack.ExecuteCallBackProcessor
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.actions.callRestApi.CallRestApiProcessor
//import com.digia.digiaui.framework.actions.callRestApi.CallRestApiProcessor
import com.digia.digiaui.framework.actions.navigation.GotoPageProcessor
import com.digia.digiaui.framework.actions.navigation.PopPageProcessor
import com.digia.digiaui.framework.actions.openUrl.OpenUrlProcessor
import com.digia.digiaui.framework.actions.rebuildState.RebuildStateProcessor
import com.digia.digiaui.framework.actions.setState.SetStateProcessor
import com.digia.digiaui.framework.actions.showBottomSheet.ShowBottomSheetProcessor
import com.digia.digiaui.framework.actions.openDialog.ShowDialogProcessor
import com.digia.digiaui.framework.actions.showToast.ShowToastProcessor
import com.digia.digiaui.framework.actions.hideBottomSheet.HideBottomSheetProcessor
import com.digia.digiaui.framework.actions.dismissDialog.DismissDialogProcessor
import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry

/** Action processor factory - routes actions to their processors */
class ActionProcessorFactory {
    fun getProcessor(action: Action,registry: MethodBindingRegistry): ActionProcessor<*> {
        return when (action.actionType) {
            ActionType.SHOW_TOAST -> ShowToastProcessor()
            ActionType.SET_STATE -> SetStateProcessor()
            ActionType.REBUILD_STATE -> RebuildStateProcessor()
            ActionType.NAVIGATE_TO_PAGE -> GotoPageProcessor()
            ActionType.NAVIGATE_BACK -> PopPageProcessor()
            ActionType.OPEN_URL -> OpenUrlProcessor()
            ActionType.CONTROL_OBJECT -> ControlObjectProcessor( registry)
            ActionType.CALL_REST_API -> CallRestApiProcessor()
            ActionType.SHOW_BOTTOM_SHEET -> ShowBottomSheetProcessor()
            ActionType.SHOW_DIALOG -> ShowDialogProcessor()
            ActionType.HIDE_BOTTOM_SHEET -> HideBottomSheetProcessor()
            ActionType.DISMISS_DIALOG -> DismissDialogProcessor()
            ActionType.SHARE_CONTENT -> ShareProcessor()
            ActionType.DELAY -> DelayProcessor()
            ActionType.COPY_TO_CLIPBOARD -> CopyToClipboardProcessor()
            ActionType.POST_MESSAGE -> PostMessageProcessor()
            ActionType.FIRE_EVENT -> FireEventProcessor()
            ActionType.EXECUTE_CALLBACK -> ExecuteCallBackProcessor()
//            ActionType.SET_APP_STATE -> SetAppStateProcessor()
//            ActionType.GET_APP_STATE -> GetAppStateProcessor()
//            ActionType.RESET_APP_STATE -> ResetAppStateProcessor()
            // Other action types will be added here
            else -> throw IllegalArgumentException("Unsupported action type: ${action.actionType}")
        }
    }
}