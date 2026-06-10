package com.digia.engage.framework.actions

import com.digia.engage.framework.actions.delay.DelayProcessor
import com.digia.engage.framework.actions.fireevent.FireEventProcessor
import com.digia.engage.framework.actions.executeCallBack.ExecuteCallBackProcessor
import com.digia.engage.framework.actions.base.Action
import com.digia.engage.framework.actions.base.ActionProcessor
import com.digia.engage.framework.actions.base.ActionType
import com.digia.engage.framework.actions.openUrl.OpenUrlProcessor
import com.digia.engage.framework.actions.setState.SetStateProcessor
import com.digia.engage.framework.actions.showToast.ShowToastProcessor
import com.digia.engage.framework.actions.hideBottomSheet.HideBottomSheetProcessor
import com.digia.engage.framework.actions.dismissDialog.DismissDialogProcessor
import com.digia.engage.framework.datatype.methodbinding.MethodBindingRegistry

/** Action processor factory - routes actions to their processors */
open class ActionProcessorFactory {
    open fun getProcessor(action: Action, registry: MethodBindingRegistry): ActionProcessor<*> {
        return when (action.actionType) {
            ActionType.SHOW_TOAST -> ShowToastProcessor()
            ActionType.SET_STATE -> SetStateProcessor()
            ActionType.OPEN_URL -> OpenUrlProcessor()
            ActionType.HIDE_BOTTOM_SHEET -> HideBottomSheetProcessor()
            ActionType.DISMISS_DIALOG -> DismissDialogProcessor()
            ActionType.DELAY -> DelayProcessor()
            ActionType.FIRE_EVENT -> FireEventProcessor()
            ActionType.EXECUTE_CALLBACK -> ExecuteCallBackProcessor()
            // Other action types are not used in Engage SDK
            else -> throw IllegalArgumentException("Unsupported action type: ${action.actionType}")
        }
    }
}
