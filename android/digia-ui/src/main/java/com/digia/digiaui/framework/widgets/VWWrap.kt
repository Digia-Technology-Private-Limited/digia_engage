package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Wrap widget properties
 * 
 * Maps to Flutter Wrap widget with properties from schema:
 * - wrapAlignment: start, end, center, spaceBetween, spaceAround, spaceEvenly
 * - runAlignment: start, end, center, spaceBetween, spaceAround, spaceEvenly
 * - wrapCrossAlignment: start, end, center
 * - direction: horizontal, vertical
 * - verticalDirection: up, down
 * - clipBehavior: none, hardEdge, antiAlias, antiAliasWithSaveLayer
 * - spacing: horizontal spacing between children
 * - runSpacing: vertical spacing between runs
 * - dataSource: for repeating children with data
 */
data class WrapProps(
    val wrapAlignment: String? = null,
    val runAlignment: String? = null,
    val wrapCrossAlignment: String? = null,
    val direction: String? = null,
    val verticalDirection: String? = null,
    val clipBehavior: String? = null,
    val spacing: Double? = null,
    val runSpacing: Double? = null,
    val dataSource: Any? = null
) {
    companion object {
        fun fromJson(json: JsonLike): WrapProps {
            return WrapProps(
                wrapAlignment = json["wrapAlignment"] as? String,
                runAlignment = json["runAlignment"] as? String,
                wrapCrossAlignment = json["wrapCrossAlignment"] as? String,
                direction = json["direction"] as? String,
                verticalDirection = json["verticalDirection"] as? String,
                clipBehavior = json["clipBehavior"] as? String,
                spacing = (json["spacing"] as? Number)?.toDouble(),
                runSpacing = (json["runSpacing"] as? Number)?.toDouble(),
                dataSource = json["dataSource"]
            )
        }
    }
}

/**
 * Virtual Wrap widget
 *
 * Renders children in a wrap layout (FlowRow for horizontal, FlowColumn for vertical).
 * Children wrap to the next line when they exceed available space.
 *
 * Equivalent to Flutter's Wrap widget.
 */
class VWWrap(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: WrapProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<WrapProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<WrapProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    private val shouldRepeatChild: Boolean
        get() = props.dataSource != null

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Render(payload: RenderPayload) {
        // Return empty if no children
        if (children.isEmpty()) {
            Empty()
            return
        }

        val spacing = (props.spacing ?: 10.0).dp
        val runSpacing = (props.runSpacing ?: 10.0).dp
        val isHorizontal = toDirection(props.direction)
        val verticalDirectionUp = props.verticalDirection == "up"

        if (isHorizontal) {
            val horizontalArrangement = toHorizontalArrangement(props.wrapAlignment, spacing)
            
            FlowRow(
                modifier = Modifier.buildModifier(payload),
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = Arrangement.spacedBy(runSpacing),
            ) {
                renderChildren(payload, verticalDirectionUp)
            }
        } else {
            val verticalArrangement = toVerticalArrangement(props.wrapAlignment, spacing)
            
            FlowColumn(
                modifier = Modifier.buildModifier(payload),
                verticalArrangement = verticalArrangement,
                horizontalArrangement = Arrangement.spacedBy(runSpacing),
            ) {
                renderChildren(payload, verticalDirectionUp)
            }
        }
    }

    @Composable
    private fun renderChildren(payload: RenderPayload, reverseOrder: Boolean) {
        if (shouldRepeatChild) {
            val childToRepeat = children.firstOrNull() ?: return
            val dataItems = payload.eval<List<Any>>(props.dataSource) ?: emptyList()
            val items = if (reverseOrder) dataItems.reversed() else dataItems

            items.forEachIndexed { index, item ->
                val actualIndex = if (reverseOrder) dataItems.size - 1 - index else index
                val scopedPayload = payload.copyWithChainedContext(
                    createExprContext(item, actualIndex)
                )
                childToRepeat.ToWidget(scopedPayload)
            }
        } else {
            val childrenList = if (reverseOrder) children.reversed() else children
            childrenList.forEach { child ->
                child.ToWidget(payload)
            }
        }
    }

    /**
     * Converts direction string to boolean (true = horizontal, false = vertical)
     */
    private fun toDirection(value: String?): Boolean = when (value?.lowercase()) {
        "vertical" -> false
        "horizontal" -> true
        else -> true // default to horizontal
    }

    /**
     * Converts alignment string to Horizontal Arrangement with spacing
     */
    private fun toHorizontalArrangement(value: String?, spacing: Dp): Arrangement.Horizontal {
        return when (value?.lowercase()) {
            "start" -> Arrangement.spacedBy(spacing, Alignment.Start)
            "end" -> Arrangement.spacedBy(spacing, Alignment.End)
            "center" -> Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
            "spacebetween" -> Arrangement.SpaceBetween
            "spacearound" -> Arrangement.SpaceAround
            "spaceevenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.spacedBy(spacing, Alignment.Start)
        }
    }

    /**
     * Converts alignment string to Vertical Arrangement with spacing
     */
    private fun toVerticalArrangement(value: String?, spacing: Dp): Arrangement.Vertical {
        return when (value?.lowercase()) {
            "start" -> Arrangement.spacedBy(spacing, Alignment.Top)
            "end" -> Arrangement.spacedBy(spacing, Alignment.Bottom)
            "center" -> Arrangement.spacedBy(spacing, Alignment.CenterVertically)
            "spacebetween" -> Arrangement.SpaceBetween
            "spacearound" -> Arrangement.SpaceAround
            "spaceevenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.spacedBy(spacing, Alignment.Top)
        }
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val wrapObj = mapOf(
            "currentItem" to item,
            "index" to index
        )

        val variables = mutableMapOf<String, Any?>()
        variables.putAll(wrapObj)

        // Add named reference if refName is provided
        refName?.let { name ->
            variables[name] = wrapObj
        }

        return DefaultScopeContext(
            variables = variables
        )
    }
}

/** Builder function for Wrap widget */
fun wrapBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {

    return VWWrap(
        refName = data.refName,
        commonProps = data.commonProps,
        props = WrapProps.fromJson(data.props.value),
        slots = {
            self ->
            registerAllChildern(data.childGroups, self, registry)
        },
        parent = parent,
        parentProps = data.parentProps
    )
}

