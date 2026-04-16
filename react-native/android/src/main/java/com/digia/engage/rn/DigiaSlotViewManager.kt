package com.digia.engage.rn

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.digia.engage.DigiaSlotView
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.Event
import java.util.concurrent.atomic.AtomicInteger

// ── ContentSizeChangeEvent ────────────────────────────────────────────────────

private class ContentSizeChangeEvent(
    surfaceId: Int,
    viewTag: Int,
    private val heightDp: Double,
) : Event<ContentSizeChangeEvent>(surfaceId, viewTag) {
    override fun getEventName(): String = "onContentSizeChange"
    override fun getEventData(): WritableMap =
        Arguments.createMap().apply { putDouble("height", heightDp) }
}

// ── DigiaSlotContainerView ────────────────────────────────────────────────────

// Plain FrameLayout wrapper: Fabric can measure it before window attachment.
// The inner DigiaSlotView (ComposeView) is created lazily in onAttachedToWindow.
internal class DigiaSlotContainerView(context: Context) : FrameLayout(context) {

    var rnContext: ThemedReactContext? = null

    private var _slotView: DigiaSlotView? = null
    private val lastReportedHeightPx = AtomicInteger(-1)

    var placementKey: String = ""
        set(value) {
            field = value
            val slot = _slotView ?: return
            slot.placementKey = value
            lastReportedHeightPx.set(-1)
            // Defer measure so the slot has a non-zero width. Do NOT call requestLayout() here —
            // it sets PFLAG_FORCE_LAYOUT on the container and RN bypasses a full traversal,
            // causing subsequent Compose requestLayout() calls to be swallowed.
            post { measureAndDispatch() }
            postDelayed({
                lastReportedHeightPx.set(-1)
                measureAndDispatch()
            }, DELAYED_MEASURE_MS)
        }

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        measureAndDispatch()
    }

    // preDrawListener catches Compose content changes that globalLayoutListener misses in RN's
    // layout model: Compose's invalidate() propagates to ViewRootImpl even when requestLayout()
    // is blocked by PFLAG_FORCE_LAYOUT.
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        measureAndDispatch()
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (_slotView == null) createSlotView()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        viewTreeObserver.addOnPreDrawListener(preDrawListener)
        post { measureAndDispatch() }
        // Retry: catches campaigns that arrive slightly after mount.
        postDelayed({
            if (lastReportedHeightPx.get() <= 0) {
                lastReportedHeightPx.set(-1)
                measureAndDispatch()
            }
        }, DELAYED_MEASURE_MS)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        super.onDetachedFromWindow()
        lastReportedHeightPx.set(-1)
    }

    // Defer measureAndDispatch to avoid re-measuring during Compose's composition phase,
    // which crashes with "pending composition has not been applied".
    override fun requestLayout() {
        super.requestLayout()
        post { measureAndDispatch() }
    }

    private fun createSlotView() {
        val themedCtx = context as? ThemedReactContext
        val activityCtx: Context = themedCtx?.currentActivity ?: context

        val slot = DigiaSlotView(activityCtx)

        val activity = themedCtx?.currentActivity
        if (activity is LifecycleOwner) slot.setViewTreeLifecycleOwner(activity)
        if (activity is ViewModelStoreOwner) slot.setViewTreeViewModelStoreOwner(activity)
        if (activity is SavedStateRegistryOwner) slot.setViewTreeSavedStateRegistryOwner(activity)

        slot.placementKey = placementKey
        slot.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        slot.addOnLayoutChangeListener { _: View, l: Int, _: Int, r: Int, _: Int,
                                         _: Int, _: Int, _: Int, _: Int ->
            if (r - l > 0) measureAndDispatch()
        }

        addView(slot)
        _slotView = slot
    }

    fun measureAndDispatch() {
        val slot = _slotView ?: return
        val ctx = rnContext ?: return
        val viewWidth = width
        if (viewWidth <= 0) return

        val widthSpec = MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        slot.measure(widthSpec, heightSpec)
        val intrinsicHeightPx = slot.measuredHeight

        if (intrinsicHeightPx == lastReportedHeightPx.get()) return
        lastReportedHeightPx.set(intrinsicHeightPx)

        val density = resources.displayMetrics.density
        val heightDp = intrinsicHeightPx / density

        val dispatcher = UIManagerHelper.getEventDispatcherForReactTag(ctx, id) ?: return
        val surfaceId = UIManagerHelper.getSurfaceId(this)
        dispatcher.dispatchEvent(ContentSizeChangeEvent(surfaceId, id, heightDp.toDouble()))
    }

    companion object {
        private const val DELAYED_MEASURE_MS = 300L
    }
}

// ── DigiaSlotViewManager ──────────────────────────────────────────────────────

internal class DigiaSlotViewManager : SimpleViewManager<DigiaSlotContainerView>() {

    override fun getName(): String = VIEW_NAME

    override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> =
        mapOf("onContentSizeChange" to mapOf("registrationName" to "onContentSizeChange"))

    override fun createViewInstance(context: ThemedReactContext): DigiaSlotContainerView {
        val container = DigiaSlotContainerView(context)
        container.rnContext = context
        container.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        return container
    }

    @ReactProp(name = "placementKey")
    fun setPlacementKey(view: DigiaSlotContainerView, placementKey: String?) {
        view.placementKey = placementKey.orEmpty()
    }

    companion object {
        const val VIEW_NAME = "DigiaSlotView"
    }
}
