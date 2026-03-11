/**
 * DigiaSlotViewManager
 *
 * React Native ViewManager that exposes [DigiaSlotComposeView] as the native view behind the JS
 * `<DigiaSlotView>` component.
 *
 * Supported JS props:
 * - `placementKey` (String) — matches the placement key set in the Digia dashboard.
 */
package com.digia.engage.rn

import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

internal class DigiaSlotViewManager : SimpleViewManager<DigiaSlotComposeView>() {

    override fun getName(): String = VIEW_NAME

    override fun createViewInstance(context: ThemedReactContext): DigiaSlotComposeView {
        val activityContext: Context = context.currentActivity ?: context

        val view = DigiaSlotComposeView(activityContext)

        // Wire Architecture Component owners so the Compose runtime works correctly.
        val activity = context.currentActivity
        if (activity is LifecycleOwner) {
            view.setViewTreeLifecycleOwner(activity)
        }
        if (activity is ViewModelStoreOwner) {
            view.setViewTreeViewModelStoreOwner(activity)
        }
        if (activity is SavedStateRegistryOwner) {
            view.setViewTreeSavedStateRegistryOwner(activity)
        }

        // React Native controls sizing; fill whatever space the JS stylesheet allocates.
        view.layoutParams =
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                )

        return view
    }

    @ReactProp(name = "placementKey")
    fun setPlacementKey(view: DigiaSlotComposeView, placementKey: String?) {
        view.placementKey = placementKey.orEmpty()
    }

    companion object {
        const val VIEW_NAME = "DigiaSlotView"
    }
}
