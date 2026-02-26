package com.digia.digiaui.framework.widgets.tabview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil

/**
 * TabViewContentProps - properties for the TabViewContent widget. Matches Flutter's
 * TabViewContentProps schema.
 */
data class TabViewContentProps(
        val isScrollable: Boolean? = null,
        val viewportFraction: Double = 1.0,
        val keepTabsAlive: ExprOr<Boolean>? = null
) {
    companion object {
        fun fromJson(json: JsonLike): TabViewContentProps {
            return TabViewContentProps(
                    isScrollable = json["isScrollable"] as? Boolean,
                    viewportFraction = NumUtil.toDouble(json["viewportFraction"]) ?: 1.0,
                    keepTabsAlive = ExprOr.fromJson(json["keepTabsAlive"])
            )
        }
    }
}

/**
 * VWTabViewContent - renders a HorizontalPager that syncs with the TabViewController. This widget
 * must be a descendant of VWTabViewController. It displays the content for each tab and allows
 * swiping between tabs.
 */
class VWTabViewContent(
        refName: String? = null,
        commonProps: CommonProps? = null,
        props: TabViewContentProps,
        parent: VirtualNode? = null,
        slots: ((VirtualCompositeNode<TabViewContentProps>) -> Map<String, List<VirtualNode>>?)? =
                null,
        parentProps: Props? = null
) :
        VirtualCompositeNode<TabViewContentProps>(
                props = props,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName,
                _slots = slots
        ) {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Render(payload: RenderPayload) {
        // Get the controller from CompositionLocal
        val controller = LocalTabViewController.current
        if (controller == null || controller.tabs.isEmpty()) {
            Empty()
            return
        }

        if (child == null) {
            Empty()
            return
        }

        val keepTabsAlive = payload.evalExpr(props.keepTabsAlive) ?: false
        val isScrollable = props.isScrollable ?: true

        // Create pager state synced with controller
        val pagerState =
                rememberPagerState(
                        initialPage = controller.currentIndex.coerceIn(0, controller.length - 1),
                        pageCount = { controller.length }
                )

        // Sync pager state with controller (when controller changes from TabBar clicks)
        LaunchedEffect(controller.currentIndex) {
            if (pagerState.currentPage != controller.currentIndex) {
                pagerState.animateScrollToPage(controller.currentIndex)
            }
        }

        // Sync controller with pager (when user swipes)
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                if (page != controller.currentIndex) {
                    controller.onTabChange(page)
                }
            }
        }

        HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = isScrollable,
                beyondViewportPageCount = if (keepTabsAlive) 1 else 0
        ) { page ->
            val item = controller.tabs.getOrNull(page)
            val scopeContext = createExprContext(item, page)
            val scopedPayload = payload.copyWithChainedContext(scopeContext)

            Box(modifier = Modifier.fillMaxSize()) { child?.ToWidget(scopedPayload) }
        }
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val tabViewContentObj = mapOf("currentItem" to item, "index" to index)
        val variables =
                mutableMapOf<String, Any?>().apply {
                    putAll(tabViewContentObj)
                    refName?.let { put(it, tabViewContentObj) }
                }
        return DefaultScopeContext(variables = variables)
    }
}

/** Builder function for TabViewContent widget */
fun tabViewContentBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWTabViewContent(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = TabViewContentProps.fromJson(data.props.value),
            slots = { self -> registerAllChildern(data.childGroups, self, registry) },
    )
}
