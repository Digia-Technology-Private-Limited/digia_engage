/**
 * DigiaViewManager
 *
 * React Native ViewManager that exposes `DigiaHostComposeView` as the native view behind the JS
 * `<DigiaHostView>` component.
 *
 * The view hosts the `DigiaHost` composable which manages dialog and bottom-sheet overlays driven
 * by Digia CEP plugins.
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

internal class DigiaViewManager : SimpleViewManager<DigiaHostComposeView>() {

    override fun getName(): String = VIEW_NAME

    override fun createViewInstance(context: ThemedReactContext): DigiaHostComposeView {
        // Prefer the current Activity as the context provider so that the Compose
        // runtime can access a proper LifecycleOwner / ViewModelStoreOwner.
        val activityContext: Context = context.currentActivity ?: context

        val view = DigiaHostComposeView(activityContext)

        // Explicitly wire the Android Architecture Component owners so that the
        // Compose runtime (which uses ViewTree* APIs) works correctly regardless
        // of React Native's internal view hierarchy wiring.
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

        // React Native controls sizing via its layout system; fill whatever space
        // the JS stylesheet allocates (typically StyleSheet.absoluteFill).
        view.layoutParams =
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                )

        return view
    }

    companion object {
        const val VIEW_NAME = "DigiaHostView"
    }
}
