package com.digia.digiaui.framework.widgets.tabview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil

/**
 * TabBarProps - properties for the TabBar widget. Matches Flutter's TabBar schema from the builder
 * dashboard.
 */
data class TabBarProps(
        // Scrolling
        val isScrollable: Boolean = false,
        val tabAlignment: String? = null,
        // Padding
        val labelPadding: Any? = null,
        val tabBarPadding: Any? = null,
        // Indicator
        val indicatorColor: ExprOr<String>? = null,
        val indicatorWeight: Double = 2.0,
        val indicatorSize: String = "tab",
        // Indicator Decoration
        val indicatorDecoration: IndicatorDecorationProps? = null,
        // Divider
        val dividerColor: ExprOr<String>? = null,
        val dividerHeight: Double? = null
) {
    companion object {
        fun fromJson(json: JsonLike): TabBarProps {
            val scrollableProps = json["tabBarScrollable"] as? Map<*, *>
            val indicatorDecorationJson = json["indicatorDecoration"] as? Map<*, *>

            return TabBarProps(
                    isScrollable = (scrollableProps?.get("value") as? Boolean) ?: false,
                    tabAlignment = scrollableProps?.get("tabAlignment") as? String,
                    labelPadding = json["labelPadding"],
                    tabBarPadding = json["tabBarPadding"],
                    indicatorColor = ExprOr.fromJson(json["indicatorColor"]),
                    indicatorWeight = NumUtil.toDouble(json["indicatorWeight"]) ?: 2.0,
                    indicatorSize = (json["indicatorSize"] as? String) ?: "tab",
                    indicatorDecoration =
                            indicatorDecorationJson?.let { IndicatorDecorationProps.fromJson(it) },
                    dividerColor = ExprOr.fromJson(json["dividerColor"]),
                    dividerHeight = NumUtil.toDouble(json["dividerHeight"])
            )
        }
    }
}

/** Indicator Decoration properties for custom tab indicator styling. */
data class IndicatorDecorationProps(
        val color: ExprOr<String>? = null,
        val shape: String = "rectangle",
        val borderRadius: Any? = null
) {
    companion object {
        fun fromJson(json: Map<*, *>): IndicatorDecorationProps {
            return IndicatorDecorationProps(
                    color = ExprOr.fromJson(json["color"]),
                    shape = (json["shape"] as? String) ?: "rectangle",
                    borderRadius = json["borderRadius"]
            )
        }
    }
}

/**
 * VWTabBar - renders tab buttons that interact with the TabViewController. This widget must be a
 * descendant of VWTabViewController.
 */
class VWTabBar(
        refName: String? = null,
        commonProps: CommonProps? = null,
        props: TabBarProps,
        parent: VirtualNode? = null,
        slots: ((VirtualCompositeNode<TabBarProps>) -> Map<String, List<VirtualNode>>?)? = null,
        parentProps: Props? = null
) :
        VirtualCompositeNode<TabBarProps>(
                props = props,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName,
                _slots = slots
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        // Get the controller from CompositionLocal
        val controller = LocalTabViewController.current
        if (controller == null) {
            Empty()
            return
        }

        val selectedChild = slot("selectedWidget")
        if (selectedChild == null) {
            Empty()
            return
        }

        val unselectedChild = slot("unselectedWidget") ?: selectedChild

        // Evaluate colors
        val indicatorColor = payload.evalColor(props.indicatorColor) ?: Color.Blue
        val dividerColor = payload.evalColor(props.dividerColor) ?: Color.Transparent
        val dividerHeight = props.dividerHeight?.dp ?: 0.dp
        val indicatorWeight = props.indicatorWeight.dp

        // Build indicator
        val indicator:
                @Composable
                (tabPositions: List<androidx.compose.material3.TabPosition>) -> Unit =
                { tabPositions ->
                    if (controller.currentIndex in tabPositions.indices) {
                        // Custom indicator decoration
                        if (props.indicatorDecoration != null) {
                            val decorationColor =
                                    payload.evalColor(props.indicatorDecoration.color)
                                            ?: indicatorColor
                            val shape =
                                    when (props.indicatorDecoration.shape) {
                                        "circle" -> RoundedCornerShape(50)
                                        else -> RoundedCornerShape(4.dp)
                                    }
                            Box(
                                    modifier =
                                            Modifier.tabIndicatorOffset(
                                                            tabPositions[controller.currentIndex]
                                                    )
                                                    .height(indicatorWeight)
                                                    .clip(shape)
                                                    .background(decorationColor)
                            )
                        } else {
                            TabRowDefaults.SecondaryIndicator(
                                    modifier =
                                            Modifier.tabIndicatorOffset(
                                                    tabPositions[controller.currentIndex]
                                            ),
                                    height = indicatorWeight,
                                    color = indicatorColor
                            )
                        }
                    }
                }

        // Build tabs
        val tabs: @Composable () -> Unit = {
            controller.tabs.forEachIndexed { index, item ->
                val isSelected = index == controller.currentIndex
                val scopeContext = createExprContext(item, index)
                val scopedPayload = payload.copyWithChainedContext(scopeContext)

                Tab(selected = isSelected, onClick = { controller.onTabChange(index) }) {
                    val childToRender = if (isSelected) selectedChild else unselectedChild
                    childToRender.ToWidget(scopedPayload)
                }
            }
        }

        // Render TabRow or ScrollableTabRow
        Column {
            if (props.isScrollable) {
                ScrollableTabRow(
                        selectedTabIndex = controller.currentIndex,
                        modifier = Modifier.fillMaxWidth(),
                        indicator = indicator,
                        divider = {
                            HorizontalDivider(thickness = dividerHeight, color = dividerColor)
                        },
                        edgePadding = 0.dp,
                        tabs = tabs
                )
            } else {
                TabRow(
                        selectedTabIndex = controller.currentIndex,
                        modifier = Modifier.fillMaxWidth(),
                        indicator = indicator,
                        divider = {
                            HorizontalDivider(thickness = dividerHeight, color = dividerColor)
                        },
                        tabs = tabs
                )
            }
        }
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val tabBarObj = mapOf("currentItem" to item, "index" to index)
        val variables =
                mutableMapOf<String, Any?>().apply {
                    putAll(tabBarObj)
                    refName?.let { put(it, tabBarObj) }
                }
        return DefaultScopeContext(variables = variables)
    }
}

/** Builder function for TabBar widget */
fun tabBarBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWTabBar(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = TabBarProps.fromJson(data.props.value),
            slots = { self -> registerAllChildern(data.childGroups, self, registry) },
    )
}
