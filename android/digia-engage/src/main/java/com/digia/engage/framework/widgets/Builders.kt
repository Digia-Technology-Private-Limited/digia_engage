package com.digia.engage.framework.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.digia.engage.framework.DefaultVirtualWidgetRegistry
import com.digia.engage.framework.RenderPayload
import com.digia.engage.framework.VirtualWidgetRegistry
import com.digia.engage.framework.base.VirtualLeafNode
import com.digia.engage.framework.base.VirtualNode
import com.digia.engage.framework.models.CommonProps
import com.digia.engage.framework.models.Props
import com.digia.engage.framework.models.VWNodeData
import com.digia.engage.framework.widgets.story.storyBuilder
import com.digia.engage.framework.widgets.story.storyVideoPlayerBuilder

/** Register only the widgets needed for Engage campaigns */
fun DefaultVirtualWidgetRegistry.registerEngageWidgets() {
        // Layout
        register("digia/column", ::columnBuilder)
        register("digia/row", ::rowBuilder)
        register("digia/container", ::containerBuilder)
        register("digia/stack", ::stackBuilder)
        register("digia/opacity", ::opacityBuilder)
        register("digia/safeArea", ::safeAreaBuilder)
        // Content
        register("digia/text", ::textBuilder)
        register("digia/richText", ::richTextBuilder)
        register("digia/image", ::imageBuilder)
        register("digia/button", ::buttonBuilder)
        register("digia/lottie", ::lottieBuilder)
        register("digia/carousel", ::carouselBuilder)
        register("digia/videoPlayer", ::videoPlayerBuilder)
        // Structural
        register("fw/sized_box", ::sizedBoxBuilder)
        register("digia/styledHorizontalDivider", ::styledHorizontalDividerBuilder)
        register("digia/styledVerticalDivider", ::vwVerticalDividerBuilder)
        // Story (for inline story campaigns)
        register("digia/story", ::storyBuilder)
        register("digia/storyVideoPlayer", ::storyVideoPlayerBuilder)
}

fun dummyBuilder(
        nodeData: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
        return VWDummy(
                refName = nodeData.refName,
                commonProps = nodeData.commonProps,
                parent = parent,
                parentProps = nodeData.parentProps,
                props = nodeData.props
        )
}

class VWDummy(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        props: Props
) :
        VirtualLeafNode<Props>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

        @Composable
        override fun Render(payload: RenderPayload) {
                Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        tint = Color.Red,
                        contentDescription = "Dummy Widget",
                        modifier =
                                Modifier.buildModifier(payload)
                                        .padding(4.dp)
                                        .background(
                                                color = Color.DarkGray,
                                                shape =
                                                        androidx.compose.foundation.shape
                                                                .RoundedCornerShape(4.dp)
                                        ),
                )
        }
}
