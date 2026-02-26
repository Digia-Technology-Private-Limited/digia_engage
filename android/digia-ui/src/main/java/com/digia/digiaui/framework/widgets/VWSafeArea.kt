package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike

/** SafeArea widget - avoids operating system interfaces. */
class VWSafeArea(
        refName: String? = null,
        commonProps: CommonProps? = null,
        private val safeAreaProps: SafeAreaProps,
        parent: VirtualNode? = null,
        slots: ((VirtualCompositeNode<SafeAreaProps>) -> Map<String, List<VirtualNode>>?)? = null,
        parentProps: Props? = null
) :
        VirtualCompositeNode<SafeAreaProps>(
                props = safeAreaProps,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName,
                _slots = slots
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val left = safeAreaProps.left ?: true
        val top = safeAreaProps.top ?: true
        val right = safeAreaProps.right ?: true
        val bottom = safeAreaProps.bottom ?: true

        // Default to Unspecified (0)
        var sides: WindowInsetsSides? = null

        if (left) sides = sides?.plus(WindowInsetsSides.Left) ?: WindowInsetsSides.Left
        if (top) sides = sides?.plus(WindowInsetsSides.Top) ?: WindowInsetsSides.Top
        if (right) sides = sides?.plus(WindowInsetsSides.Right) ?: WindowInsetsSides.Right
        if (bottom) sides = sides?.plus(WindowInsetsSides.Bottom) ?: WindowInsetsSides.Bottom

        var modifier = Modifier.buildModifier(payload)

        // Only apply insets if we have some sides selected
        if (sides != null) {
            modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(sides))
        }

        Box(modifier = modifier) { child?.ToWidget(payload) }
    }
}

// ============== Props ==============

data class SafeAreaProps(
        val left: Boolean? = null,
        val top: Boolean? = null,
        val right: Boolean? = null,
        val bottom: Boolean? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): SafeAreaProps {
            return SafeAreaProps(
                    left = json["left"] as? Boolean,
                    top = json["top"] as? Boolean,
                    right = json["right"] as? Boolean,
                    bottom = json["bottom"] as? Boolean
            )
        }
    }
}

// ============== Builder ==============

fun safeAreaBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWSafeArea(
            refName = data.refName,
            commonProps = data.commonProps,
            safeAreaProps = SafeAreaProps.fromJson(data.props.value),
            slots = { self -> registerAllChildern(data.childGroups, self, registry) },
            parent = parent,
            parentProps = data.parentProps
    )
}
