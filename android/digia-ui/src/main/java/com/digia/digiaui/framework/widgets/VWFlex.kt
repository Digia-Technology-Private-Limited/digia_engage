package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Flex direction - horizontal (Row) or vertical (Column)
 */
enum class FlexDirection {
    HORIZONTAL, VERTICAL
}

/**
 * Flex widget properties
 */
data class FlexProps(
    val direction: FlexDirection,
    val mainAxisAlignment: String? = null,
    val crossAxisAlignment: String? = null,
    val mainAxisSize: String? = null,
    val dataSource: Any? = null,
    val spacing: Double? = null,
    val startSpacing: Double? = null,
    val endSpacing: Double? = null,
    val isScrollable: Boolean? = null
) {
    companion object {
        fun fromJson(json: JsonLike, direction: FlexDirection): FlexProps {
            return FlexProps(
                direction = direction,
                mainAxisAlignment = json["mainAxisAlignment"] as? String,
                crossAxisAlignment = json["crossAxisAlignment"] as? String,
                mainAxisSize = json["mainAxisSize"] as? String,
                dataSource = json["dataSource"],
                spacing = (json["spacing"] as? Number)?.toDouble(),
                startSpacing = (json["startSpacing"] as? Number)?.toDouble(),
                endSpacing = (json["endSpacing"] as? Number)?.toDouble(),
                isScrollable = json["isScrollable"] as? Boolean
            )
        }
    }
}

/**
 * Virtual Flex widget - base for Column and Row
 */
class VWFlex(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: FlexProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<FlexProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<FlexProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    private val shouldRepeatChild: Boolean
        get() = props.dataSource != null

    @Composable
    override fun Render(payload: RenderPayload) {
        if (children.isEmpty()) {
            Empty()
            return
        }

        val flexWidget = if (shouldRepeatChild) {
            buildRepeatingFlex(payload)
        } else {
            buildStaticFlex(payload)
        }

        flexWidget
    }

    @Composable
    private fun buildRepeatingFlex(payload: RenderPayload) {
        val childToRepeat = children.firstOrNull() ?: return
        val dataItems = payload.eval<List<Any>>(props.dataSource) ?: emptyList()

        when (props.direction) {
            FlexDirection.VERTICAL -> {
                Column(
                    modifier = buildColumnModifier(payload) .applyMainAxisSize()
                        .let {
                            if (props.isScrollable == true)
                                it.verticalScroll(rememberScrollState())
                            else it
                        },
                    verticalArrangement = toMainAxisAlignmentVertical(props.mainAxisAlignment),
                    horizontalAlignment = toHorizontalAlignment(props.crossAxisAlignment)
                ) {
                    dataItems.forEachIndexed { index, item ->
                        val scopedPayload = payload.copyWithChainedContext(
                            createExprContext(item, index)
                        )
                        childToRepeat.ToWidget(scopedPayload)
                    }
                }
            }
            FlexDirection.HORIZONTAL -> {
                Row(
                    modifier = buildRowModifier(payload) .applyMainAxisSize()
                        .let {
                            if (props.isScrollable == true)
                                it.horizontalScroll(rememberScrollState())
                            else it
                        },
                    horizontalArrangement = toMainAxisAlignmentHorizontal(props.mainAxisAlignment),
                    verticalAlignment = toVerticalAlignment(props.crossAxisAlignment)
                ) {
                    dataItems.forEachIndexed { index, item ->
                        val scopedPayload = payload.copyWithChainedContext(
                            createExprContext(item, index)
                        )
                        childToRepeat.ToWidget(scopedPayload)
                    }
                }
            }
        }
    }

    @Composable
    private fun buildStaticFlex(payload: RenderPayload) {
        when (props.direction) {
            FlexDirection.VERTICAL -> {
                Column(
                    modifier = buildColumnModifier(payload)
                        .applyMainAxisSize()
                        .let {
                        if (props.isScrollable == true)
                            it.verticalScroll(rememberScrollState())
                        else it
                    },
                    verticalArrangement = toMainAxisAlignmentVertical(props.mainAxisAlignment),
                    horizontalAlignment = toHorizontalAlignment(props.crossAxisAlignment)
                ) {
                    // Inside ColumnScope - Modifier.weight() is available
                    children.forEach { child ->
                        val parentProps = child.parentProps
                        val flexFit = parentProps?.getString("flexFit")
                        val flexValue = parentProps?.getDouble("flex") ?: 1.0

                        if (flexFit != null) {
                            val fillMaxSize = when (flexFit.lowercase()) {
                                "tight", "expanded" -> true
                                "loose", "flexible" -> false
                                else -> true
                            }
                            val modifier= Modifier.weight(flexValue.toFloat(), fill = fillMaxSize)
                             child.ToWidgetWithModifier(payload,modifier)
                        } else {
                            child.ToWidget(payload)
                        }
                    }
                }
            }
            FlexDirection.HORIZONTAL -> {
                Row(
                    modifier = buildRowModifier(payload)
                        .applyMainAxisSize()
                        .let {
                        if (props.isScrollable == true)
                            it.horizontalScroll(rememberScrollState())
                        else it
                    },
                    horizontalArrangement = toMainAxisAlignmentHorizontal(props.mainAxisAlignment),
                    verticalAlignment = toVerticalAlignment(props.crossAxisAlignment)
                ) {
                    // Inside RowScope - Modifier.weight() is available
                    children.forEach { child ->
                        val parentProps = child.parentProps
                        val flexFit = parentProps?.getString("expansion.type")
                        val flexValue = parentProps?.getDouble("expansion.flexValue") ?: 1.0

                        if (flexFit != null) {
                            val fillMaxSize = when (flexFit.lowercase()) {
                                "tight", "expanded" -> true
                                "loose", "flexible" -> false
                                else -> true
                            }
                            val modifier= Modifier.weight(flexValue.toFloat(), fill = fillMaxSize)
                            child.ToWidgetWithModifier(payload,modifier)
                        } else {
                            child.ToWidget(payload)
                        }
                    }
                }
            }
        }
    }


    @Composable
    private fun buildColumnModifier(payload: RenderPayload): Modifier {
        val startSpacing = (props.startSpacing ?: 0.0).dp
        val endSpacing = (props.endSpacing ?: 0.0).dp
        return Modifier
            .padding(top = startSpacing, bottom = endSpacing)
            .buildModifier(payload)
    }

    private fun Modifier.applyMainAxisSize(): Modifier {
        val isFlexChild = parentProps?.getString("flexFit") != null ||
                parentProps?.getString("expansion.type") != null

        return when (props.mainAxisSize?.lowercase()) {
            "max" -> {
                when {
                    // If this Flex is expanded, weight already controls size
                    isFlexChild -> this

                    // If parent is same-axis Flex → behave like Flutter (wrap)
                    isSameAxisParentFlex(parent,props) -> this

                    // Otherwise safe to fill
                    props.direction == FlexDirection.VERTICAL -> this.fillMaxHeight()
                    props.direction == FlexDirection.HORIZONTAL -> this.fillMaxWidth()
                    else -> this
                }
            }
            "min" -> this
            else -> this
        }
    }
    @Composable
    private fun buildRowModifier(payload: RenderPayload): Modifier {
        val startSpacing = (props.startSpacing ?: 0.0).dp
        val endSpacing = (props.endSpacing ?: 0.0).dp
        return Modifier
            .padding(start = startSpacing, end = endSpacing)
            .buildModifier(payload)
    }

    private fun toMainAxisAlignmentVertical(value: String?): Arrangement.Vertical {
        return when (value) {
            "start" -> Arrangement.Top
            "end" -> Arrangement.Bottom
            "center" -> Arrangement.Center
            "spaceBetween" -> Arrangement.SpaceBetween
            "spaceAround" -> Arrangement.SpaceAround
            "spaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Top
        }
    }

    private fun toHorizontalAlignment(value: String?): Alignment.Horizontal {
        return when (value) {
            "start" -> Alignment.Start
            "end" -> Alignment.End
            "center" -> Alignment.CenterHorizontally
            else -> Alignment.CenterHorizontally
        }
    }


    private fun toMainAxisAlignmentHorizontal(value: String?): Arrangement.Horizontal {
        return when (value) {
            "start" -> Arrangement.Start
            "end" -> Arrangement.End
            "center" -> Arrangement.Center
            "spaceBetween" -> Arrangement.SpaceBetween
            "spaceAround" -> Arrangement.SpaceAround
            "spaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Start
        }
    }

    private fun toVerticalAlignment(value: String?): Alignment.Vertical {
        return when (value) {
            "top" -> Alignment.Top
            "bottom" -> Alignment.Bottom
            "center" -> Alignment.CenterVertically
            else -> Alignment.CenterVertically
        }
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val flexObj = mapOf(
            "currentItem" to item,
            "index" to index
        )

        val variables = mutableMapOf<String, Any?>()
        variables.putAll(flexObj)

        refName?.let { name ->
            variables[name] = flexObj
        }

        return DefaultScopeContext(
            variables = variables
        )
    }
}

/** Builder function for Column widget */
fun columnBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {

    return VWFlex(
        refName = data.refName,
        commonProps = data.commonProps,
        props = FlexProps.fromJson(data.props.value , FlexDirection.VERTICAL),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
        parent= parent,
        parentProps = data.parentProps

    )
}


/** Builder function for Row widget */
fun rowBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {

    return VWFlex(
        refName = data.refName,
        commonProps = data.commonProps,
        props = FlexProps.fromJson(data.props.value , FlexDirection.HORIZONTAL),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
        parent= parent,
        parentProps = data.parentProps

    )
}




private fun isSameAxisParentFlex(parent: VirtualNode?, props: FlexProps): Boolean {
    val parentFlex = parent as? VWFlex ?: return false
    return parentFlex.props.direction == props.direction
}