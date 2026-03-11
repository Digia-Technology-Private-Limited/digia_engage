/**
 * DigiaSlotComposeView
 *
 * An AbstractComposeView that renders the `DigiaSlot` composable for a given placement key. Mounted
 * by [DigiaSlotViewManager] when React Native renders a `<DigiaSlotView placementKey="hero_banner"
 * />` element.
 *
 * Unlike [DigiaHostComposeView] (which is a transparent full-screen overlay), this view is sized by
 * the React Native layout system — the host JS component controls width/height via `style`. Inline
 * Compose content renders inside that allocated space.
 */
package com.digia.engage.rn

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import com.digia.engage.DigiaSlot

internal class DigiaSlotComposeView
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {

    /** Set by [DigiaSlotViewManager] when the `placementKey` prop changes on the JS side. */
    var placementKey: String by mutableStateOf("")

    @Composable
    override fun Content() {
        if (placementKey.isNotEmpty()) {
            DigiaSlot(placementKey = placementKey)
        }
    }
}
