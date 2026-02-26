package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Stack widget properties
 *
 * Maps to Flutter Stack widget with properties from schema:
 * - childAlignment: alignment for non-positioned children (topStart, center, etc.)
 * - fit: how stack sizes itself relative to children (expand, loose, passthrough)
 */
data class StackProps(
    val childAlignment: String? = null,
    val fit: String? = null
) {
    companion object {
        fun fromJson(json: JsonLike): StackProps {
            return StackProps(
                childAlignment = json["childAlignment"] as? String,
                fit = json["fit"] as? String
            )
        }
    }
}

/**
 * Positioning data extracted from parentProps
 */
data class PositionData(
    val left: Double? = null,
    val top: Double? = null,
    val right: Double? = null,
    val bottom: Double? = null
) {
    companion object {
        /**
         * Parses position string in format "left,top,right,bottom"
         * where "-" or empty represents null
         */
        fun fromString(positionStr: String): PositionData {
            val parts = positionStr.split(',').map { it.trim() }

            fun parse(value: String): Double? {
                return if (value.isEmpty() || value == "-") null
                else value.toDoubleOrNull()
            }

            return PositionData(
                left = if (parts.isNotEmpty()) parse(parts[0]) else null,
                top = if (parts.size > 1) parse(parts[1]) else null,
                right = if (parts.size > 2) parse(parts[2]) else null,
                bottom = if (parts.size > 3) parse(parts[3]) else null
            )
        }

        fun fromJson(json: JsonLike): PositionData {
            return PositionData(
                left = (json["left"] as? Number)?.toDouble(),
                top = (json["top"] as? Number)?.toDouble(),
                right = (json["right"] as? Number)?.toDouble(),
                bottom = (json["bottom"] as? Number)?.toDouble()
            )
        }
    }
}

/**
 * Virtual Stack widget
 *
 * Renders children in a stack layout using Compose Box.
 * Children are laid out on top of each other with optional positioning.
 *
 * Equivalent to Flutter's Stack widget.
 */
class VWStack(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: StackProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<StackProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<StackProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    @Composable
    override fun Render(payload: RenderPayload) {
        // Return empty if no children
        if (children.isEmpty()) {
            Empty()
            return
        }

        val alignment = toAlignment(props.childAlignment)
        val stackFit = props.fit ?: "loose"

        // Build base modifier with common props
        val baseModifier = Modifier.buildModifier(payload)

        // Apply fit modifier based on fit type
        // In Flutter Stack:
        // - expand: Stack fills parent, non-positioned children also fill
        // - loose: Stack sizes to largest child (default)
        // - passthrough: Stack passes parent constraints to children
        val boxModifier = when (stackFit.lowercase()) {
            "expand" -> baseModifier.fillMaxSize()
            "passthrough" -> baseModifier.wrapContentSize(unbounded = true)
            else -> baseModifier // loose (default) - wrap content
        }

        Box(
            modifier = boxModifier,
            contentAlignment = alignment
        ) {
            // In Flutter Stack, children are rendered in order and overlap.
            // The first child is at the bottom, last child is on top.
            // Non-positioned children are aligned according to `alignment`.
            // Positioned children are placed at absolute positions.
            children.forEach { child ->
                RenderStackChild(child, payload, alignment, stackFit)
            }
        }
    }

    @Composable
    private fun BoxScope.RenderStackChild(
        child: VirtualNode,
        payload: RenderPayload,
        stackAlignment: Alignment,
        stackFit: String
    ) {
        val position = extractPosition(child)

        if (position != null && hasAnyPositioning(position)) {
            // Child has explicit positioning - apply offset from edges
            Box(modifier = buildPositionedModifier(position)) {
                child.ToWidget(payload)
            }
        } else {
            // No positioning - child should be aligned according to stack's alignment
            // and overlap with other non-positioned children
            //
            // In Flutter with fit: expand, non-positioned children fill the stack
            // In Flutter with fit: loose, non-positioned children use their natural size
            val childModifier = when (stackFit.lowercase()) {
                "expand" -> Modifier.align(stackAlignment).fillMaxSize()
                else -> Modifier.align(stackAlignment)
            }

            Box(modifier = childModifier) {
                child.ToWidget(payload)
            }
        }
    }

    /**
     * Extracts position data from child's parentProps
     */
    private fun extractPosition(child: VirtualNode): PositionData? {
        val parentProps = child.parentProps ?: return null
        val positionValue = parentProps.value["position"] ?: return null

        return when (positionValue) {
            is String -> PositionData.fromString(positionValue)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                PositionData.fromJson(positionValue as JsonLike)
            }
            else -> null
        }
    }

    /**
     * Checks if position has any non-null values
     */
    private fun hasAnyPositioning(position: PositionData): Boolean {
        return position.left != null || position.top != null ||
                position.right != null || position.bottom != null
    }

    /**
     * Builds a modifier for absolute positioning from edges
     *
     * This mimics Flutter's Positioned widget behavior:
     * - left: distance from left edge
     * - top: distance from top edge
     * - right: distance from right edge
     * - bottom: distance from bottom edge
     */
    private fun BoxScope.buildPositionedModifier(position: PositionData): Modifier {
        // Determine which corner/edge to align to based on specified positions
        val alignment = when {
            position.left != null && position.top != null -> Alignment.TopStart
            position.right != null && position.top != null -> Alignment.TopEnd
            position.left != null && position.bottom != null -> Alignment.BottomStart
            position.right != null && position.bottom != null -> Alignment.BottomEnd
            position.left != null -> Alignment.CenterStart
            position.right != null -> Alignment.CenterEnd
            position.top != null -> Alignment.TopCenter
            position.bottom != null -> Alignment.BottomCenter
            else -> Alignment.TopStart
        }

        // Calculate offset based on specified positions
        val offsetX = when {
            position.left != null -> position.left.dp
            position.right != null -> -(position.right.dp)
            else -> 0.dp
        }

        val offsetY = when {
            position.top != null -> position.top.dp
            position.bottom != null -> -(position.bottom.dp)
            else -> 0.dp
        }

        return Modifier
            .align(alignment)
            .offset(x = offsetX, y = offsetY)
    }

    /**
     * Converts alignment string to Compose Alignment
     */
    private fun toAlignment(value: String?): Alignment {
        return when (value?.lowercase()) {
            "topleft", "topstart" -> Alignment.TopStart
            "topcenter" -> Alignment.TopCenter
            "topright", "topend" -> Alignment.TopEnd
            "centerleft", "centerstart" -> Alignment.CenterStart
            "center" -> Alignment.Center
            "centerright", "centerend" -> Alignment.CenterEnd
            "bottomleft", "bottomstart" -> Alignment.BottomStart
            "bottomcenter" -> Alignment.BottomCenter
            "bottomright", "bottomend" -> Alignment.BottomEnd
            else -> Alignment.TopStart // Default to topStart as per schema
        }
    }
}

/** Builder function for Stack widget */
fun stackBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {
    val childrenData = data.childGroups?.mapValues { (_, childrenData) ->
        childrenData.map { childData ->
            registry.createWidget(childData, parent)
        }
    }
    return VWStack(
        refName = data.refName,
        commonProps = data.commonProps,
        props = StackProps.fromJson(data.props.value),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
        parent = parent,
        parentProps = data.parentProps
    )
}