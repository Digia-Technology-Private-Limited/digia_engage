package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.DefaultVirtualWidgetRegistry
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.widgets.story.storyBuilder
import com.digia.digiaui.framework.widgets.story.storyVideoPlayerBuilder
import com.digia.digiaui.framework.widgets.tabview.tabBarBuilder
import com.digia.digiaui.framework.widgets.tabview.tabViewContentBuilder
import com.digia.digiaui.framework.widgets.tabview.tabViewControllerBuilder

/** Register all built-in widgets with the registry */
fun DefaultVirtualWidgetRegistry.registerBuiltInWidgets() {
        register("digia/text", ::textBuilder)
        register("digia/column", ::columnBuilder)
        register("digia/row", ::rowBuilder)
        register("digia/listView", ::listViewBuilder)
        register("digia/paginatedListView", ::paginatedListViewBuilder)
        register("digia/streamBuilder", ::streamBuilderBuilder)
        register("digia/conditionalBuilder", ::conditionalBuilder)
        register("digia/conditionalItem", ::conditionalItemBuilder)
        register("fw/scaffold", ::scaffoldBuilder)
        register("digia/appBar", ::appBarBuilder)
        register("fw/appBar", ::appBarBuilder)
        register("digia/circularProgressBar", ::circularProgressBarBuilder)
        register("digia/futureBuilder", ::futureBuilder)
        register("digia/lottie", ::lottieBuilder)
        register("digia/linearProgressBar", ::linearProgressBarBuilder)
        register("digia/textFormField", ::textFormFieldBuilder)
        register("digia/videoPlayer", ::videoPlayerBuilder)
        register("digia/button", ::buttonBuilder)
        register("digia/icon", ::iconBuilder)
        register("digia/image", ::imageBuilder)
        register("digia/container", ::containerBuilder)
        register("digia/carousel", ::carouselBuilder)
        register("digia/wrap", ::wrapBuilder)
        register("digia/opacity", ::opacityBuilder)
        register("digia/stack", ::stackBuilder)
        register("digia/styledVerticalDivider", ::vwVerticalDividerBuilder)
        register("digia/pageView", ::pageViewBuilder)
        register("digia/refreshIndicator", ::refreshIndicatorBuilder)
        register("digia/markdown", ::markdownBuilder)
        register("digia/youtubePlayer", ::youtubePlayerBuilder)
        register("digia/checkBox", ::checkBoxBuilder)
        register("digia/switch", ::switchBuilder)
        register("digia/webView", ::webViewBuilder)
        register("digia/animatedBuilder", ::animatedBuilderBuilder)
        register("digia/animatedSwitcher", ::animatedSwitcherBuilder)
        register("digia/avatar", ::avatarBuilder)
        register("digia/slider", ::sliderBuilder)
        register("digia/rangeSlider", ::rangeSliderBuilder)
        register("fw/sized_box", ::sizedBoxBuilder)
        register("digia/safeArea", ::safeAreaBuilder)
        register("digia/gridView", ::gridViewBuilder)
        register("digia/richText", ::richTextBuilder)
        register("digia/pinField", ::pinFieldBuilder)
        register("digia/expandable", ::expandableBuilder)
        register("digia/masonryGridView", ::gridViewBuilder)
        register("digia/styledHorizontalDivider", ::styledHorizontalDividerBuilder)
        register("digia/timer", ::timerBuilder)
        register("digia/overlay", ::overlayBuilder)
        register("digia/tabController", ::tabViewControllerBuilder)
        register("digia/tabBar", ::tabBarBuilder)
        register("digia/tabViewContent", ::tabViewContentBuilder)
        register("digia/story", ::storyBuilder)
        register("digia/storyVideoPlayer", ::storyVideoPlayerBuilder)
        register("digia/chart", ::chartBuilder)
        register("digia/scratchCard", ::scratchCardBuilder)
        register("digia/smartScrollView",::smartScrollViewBuilder)
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
