package com.digia.engage

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.fragment.app.Fragment
import com.digia.digiaui.R

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

    @Composable
    override fun Content() {
        // Empty content lambda — your Views are the actual app content.
        // DigiaHost installs the DialogHost / BottomSheetHost overlay layers.
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
