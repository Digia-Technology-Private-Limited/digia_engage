package com.digia.engage.rn

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.digia.engage.Digia
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp

internal class DigiaAnchorContainerView(context: Context) : FrameLayout(context) {

    var anchorKey: String = ""

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        reportPosition()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        reportPosition()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        if (anchorKey.isNotBlank()) Digia.unregisterAnchor(anchorKey)
        super.onDetachedFromWindow()
    }

    private fun reportPosition() {
        val key = anchorKey.takeIf { it.isNotBlank() } ?: return
        // React Native (Fabric) may not call layout() on this FrameLayout wrapper,
        // so this.width/height can be 0. Derive dimensions from the first child instead.
        val targetView: android.view.View = if (childCount > 0) getChildAt(0) else this
        val w = targetView.width
        val h = targetView.height
        if (w == 0 || h == 0) return  // not measured yet — wait for next layout pass
        val loc = IntArray(2)
        targetView.getLocationOnScreen(loc)
        android.util.Log.d("Digia", "[DigiaAnchorView] registerAnchor key='$key' x=${loc[0]} y=${loc[1]} w=$w h=$h")
        Digia.registerAnchor(key, loc[0], loc[1], w, h)
        Digia.registerAnchorView(key, targetView)
    }
}

// ViewGroupManager (not SimpleViewManager) because Fabric calls getViewGroupManager()
// on any view that hosts RN children — DigiaAnchorView wraps the anchor target element.
internal class DigiaAnchorViewManager : ViewGroupManager<DigiaAnchorContainerView>() {

    override fun getName(): String = VIEW_NAME

    override fun createViewInstance(context: ThemedReactContext) =
        DigiaAnchorContainerView(context)

    // Use updateProperties instead of @ReactProp to be compatible with both
    // Old Architecture (Paper) and New Architecture (Fabric) without codegen.
    override fun updateProperties(
        viewToUpdate: DigiaAnchorContainerView,
        props: com.facebook.react.uimanager.ReactStylesDiffMap,
    ) {
        super.updateProperties(viewToUpdate, props)
        if (props.hasKey("anchorKey")) {
            viewToUpdate.anchorKey = props.getString("anchorKey") ?: ""
        }
    }

    @com.facebook.react.uimanager.annotations.ReactProp(name = "anchorKey")
    fun setAnchorKey(view: DigiaAnchorContainerView, key: String?) {
        view.anchorKey = key.orEmpty()
    }

    override fun addView(parent: DigiaAnchorContainerView, child: View, index: Int) {
        parent.addView(child, index)
    }

    override fun getChildCount(parent: DigiaAnchorContainerView): Int = parent.childCount

    override fun getChildAt(parent: DigiaAnchorContainerView, index: Int): View =
        parent.getChildAt(index)

    override fun removeViewAt(parent: DigiaAnchorContainerView, index: Int) {
        parent.removeViewAt(index)
    }

    companion object {
        const val VIEW_NAME = "DigiaAnchorView"
    }
}
