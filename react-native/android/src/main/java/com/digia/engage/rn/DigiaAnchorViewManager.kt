package com.digia.engage.rn

import android.graphics.Color
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.digia.engage.DigiaAnchorView
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp

internal class DigiaAnchorViewManager : ViewGroupManager<DigiaAnchorView>() {

    override fun getName(): String = VIEW_NAME

    override fun createViewInstance(context: ThemedReactContext): DigiaAnchorView {
        val activityContext = context.currentActivity ?: context
        val view = DigiaAnchorView(activityContext)

        val activity = context.currentActivity
        if (activity is LifecycleOwner) view.setViewTreeLifecycleOwner(activity)
        if (activity is ViewModelStoreOwner) view.setViewTreeViewModelStoreOwner(activity)
        if (activity is SavedStateRegistryOwner) view.setViewTreeSavedStateRegistryOwner(activity)

        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        // Prevent the FrameLayout background from leaking white in spotlight corners.
        view.setBackgroundColor(Color.TRANSPARENT)
        return view
    }

    @ReactProp(name = "anchorKey")
    fun setAnchorKey(view: DigiaAnchorView, anchorKey: String?) {
        view.anchorKey = anchorKey.orEmpty()
    }

    @ReactProp(name = "cornerRadius", defaultFloat = 0f)
    fun setCornerRadius(view: DigiaAnchorView, cornerRadius: Float) {
        val px = cornerRadius * view.resources.displayMetrics.density
        view.spotlightCornerRadius = px
        if (px > 0f) {
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, px)
                }
            }
            view.clipToOutline = true
        } else {
            view.clipToOutline = false
            view.outlineProvider = ViewOutlineProvider.BOUNDS
        }
    }

    companion object {
        const val VIEW_NAME = "DigiaAnchorView"
    }
}
