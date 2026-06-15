package com.digia.engage

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.fragment.app.Fragment
import com.digia.engage.R

// ─────────────────────────────────────────────────────────────────────────────
// DigiaHostView
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A View-based wrapper around [DigiaHost] for use in XML / View-based layouts.
 *
 * Place this as a **full-screen, last-child** overlay in your root layout so that SDK-triggered
 * Dialogs and Bottom Sheets are rendered on top of your existing View hierarchy. No Compose code is
 * needed in your Activity or Fragment.
 *
 * **XML usage:**
 * ```xml
 * <FrameLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent">
 *
 *     <!-- your normal views / fragments -->
 *
 *     <!-- must be the last child so it draws on top -->
 *     <com.digia.engage.DigiaHostView
 *         android:layout_width="match_parent"
 *         android:layout_height="match_parent" />
 *
 * </FrameLayout>
 * ```
 *
 * **Programmatic usage:**
 * ```kotlin
 * val digiaHost = DigiaHostView(this)
 * rootLayout.addView(
 *     digiaHost,
 *     FrameLayout.LayoutParams(
 *         FrameLayout.LayoutParams.MATCH_PARENT,
 *         FrameLayout.LayoutParams.MATCH_PARENT,
 *     )
 * )
 * ```
 */
class DigiaHostView
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
) : AbstractComposeView(context, attrs, defStyle) {

    init {
        isClickable = false
        isFocusable = false
    }

    // Balloon manages its own PopupWindow for both tooltip and spotlight — always return false
    // so touches pass through to React Native content below.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false


    @Composable
    override fun Content() {
        DigiaHost {}
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DigiaSlotView
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A View-based wrapper around [DigiaSlot] for use in XML / View-based layouts.
 *
 * Set [placementKey] via the `app:placementKey` XML attribute **or** programmatically. The view
 * re-renders automatically whenever [placementKey] changes.
 *
 * Ensure a [DigiaHostView] (or a Compose [DigiaHost]) is present in the same window so that the
 * render engine is initialised before this view tries to display content.
 *
 * **XML usage:**
 * ```xml
 * <com.digia.engage.DigiaSlotView
 *     android:id="@+id/homeBannerSlot"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:placementKey="home_banner" />
 * ```
 *
 * **Programmatic usage:**
 * ```kotlin
 * val slot = DigiaSlotView(context)
 * slot.placementKey = "home_banner"
 * container.addView(slot)
 * ```
 *
 * Or via binding after inflation:
 * ```kotlin
 * binding.homeBannerSlot.placementKey = "home_banner"
 * ```
 */
class DigiaSlotView
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
) : AbstractComposeView(context, attrs, defStyle) {

    /**
     * The placement key identifying which server-configured slot to display. Updating this property
     * triggers an automatic recomposition.
     */
    var placementKey: String by mutableStateOf("")

    init {
        if (attrs != null) {
            val typedArray =
                    context.obtainStyledAttributes(attrs, R.styleable.DigiaSlotView, defStyle, 0)
            try {
                placementKey = typedArray.getString(R.styleable.DigiaSlotView_placementKey) ?: ""
            } finally {
                typedArray.recycle()
            }
        }
    }

    @Composable
    override fun Content() {
        if (placementKey.isNotEmpty()) {
            DigiaSlot(placementKey = placementKey)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DigiaAnchorView
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A plain FrameLayout that reports its on-screen position to the SDK under [anchorKey].
 * Used as a reference point for SHOW_TOOLTIP and SHOW_SPOTLIGHT overlays.
 *
 * Wrap the element you want to anchor to as the single child of this view.
 */
class DigiaAnchorView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : android.widget.FrameLayout(context, attrs, defStyle) {

    var anchorKey: String = ""
        set(value) {
            if (field == value) return
            if (field.isNotEmpty()) Digia.unregisterAnchor(field)
            field = value
            if (isAttachedToWindow) reportPosition()
        }

    private val globalLayoutListener =
        android.view.ViewTreeObserver.OnGlobalLayoutListener { reportPosition() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        reportPosition()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        if (anchorKey.isNotEmpty()) Digia.unregisterAnchor(anchorKey)
        super.onDetachedFromWindow()
    }

    private fun reportPosition() {
        val key = anchorKey.takeIf { it.isNotEmpty() } ?: return
        // Derive dimensions from the anchored child; the wrapper itself may not be laid out.
        val target = if (childCount > 0) getChildAt(0) else this
        val w = target.width
        val h = target.height
        if (w == 0 || h == 0) return // not measured yet — wait for the next layout pass
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        Digia.registerAnchor(key, loc[0], loc[1], w, h)
        Digia.registerAnchorView(key, target)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DigiaScreen — View-based screen tracking
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reports the current screen name to the Digia SDK from an [Activity].
 *
 * Call this in [Activity.onResume] as the non-Compose equivalent of the [DigiaScreen] composable.
 *
 * ```kotlin
 * override fun onResume() {
 *     super.onResume()
 *     digiaScreen("HomeScreen")
 * }
 * ```
 */
fun Activity.digiaScreen(name: String) {
    Digia.setCurrentScreen(name)
}

/**
 * Reports the current screen name to the Digia SDK from a [Fragment].
 *
 * Call this in [Fragment.onResume] as the non-Compose equivalent of the [DigiaScreen] composable.
 *
 * ```kotlin
 * override fun onResume() {
 *     super.onResume()
 *     digiaScreen("ProfileScreen")
 * }
 * ```
 */
fun Fragment.digiaScreen(name: String) {
    Digia.setCurrentScreen(name)
}
