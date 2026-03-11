/**
 * DigiaHostComposeView
 *
 * An AbstractComposeView that hosts the `DigiaHost { }` composable. Mounted by DigiaViewManager
 * (when used as <DigiaHostView> in JS) or programmatically via DigiaModule.mountHost().
 *
 * Compose Dialogs and BottomSheets rendered by DigiaHost create separate Android windows (via
 * Compose Dialog / ModalBottomSheet), so they naturally appear on top of all React Native content.
 */
package com.digia.engage.rn

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.digia.engage.DigiaHost

internal class DigiaHostComposeView
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {

    init {
        // This view itself is just a transparent anchor for the Compose composition.
        // The actual overlays (Dialogs, BottomSheets) appear in separate Android windows
        // created by Compose – so this view must NOT steal touch events from React Native.
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    @Composable
    override fun Content() {
        // DigiaHost wraps content and manages Dialog / BottomSheet overlays.
        // We pass an empty lambda; the overlays use separate Compose window layers.
        DigiaHost {}
    }

    // Pass all touch events through to the React Native views behind this view.
    override fun onTouchEvent(event: MotionEvent): Boolean = false
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
}
