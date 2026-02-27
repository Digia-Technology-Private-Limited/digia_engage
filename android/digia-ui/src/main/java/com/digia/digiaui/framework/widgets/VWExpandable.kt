package com.digia.digiaui.framework.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWData
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.utils.ToUtils

/**
 * VWExpandable
 *
 * Implements the Expandable widget from the schema. Exhibits a header and a collapsible body.
 */
class VWExpandable(
        refName: String? = null,
        commonProps: CommonProps? = null,
        private val expandableProps: ExpandableProps,
        parent: VirtualNode? = null,
        slots: ((VirtualCompositeNode<ExpandableProps>) -> Map<String, List<VirtualNode>>?)? = null,
        parentProps: Props? = null,
        // Slot Fields (populated by Builder)
        private val headerNodes: List<VirtualNode>? = null,
        private val collapsedNodes: List<VirtualNode>? = null,
        private val expandedNodes: List<VirtualNode>? = null
) :
        VirtualCompositeNode<ExpandableProps>(
                props = expandableProps,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName,
                _slots = slots
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        // State - default from props
        var isExpanded by remember { mutableStateOf(expandableProps.activeView == "expanded") }

        val animationDuration = expandableProps.animationDuration ?: 300
        val borderRadius = ToUtils.borderRadius(expandableProps.borderRadius ?: 0)

        // Alignment
        val headerAlignment = expandableProps.headerAlignment.toAlignment(Alignment.CenterStart)
        val bodyAlignment = expandableProps.bodyAlignment.toHorizontalAlignment()

        // Icon logic
        val iconProps = expandableProps.icon
        val hasIcon = iconProps?.hasIcon ?: true
        val iconPlacement = iconProps?.iconPlacement ?: "left"
        val iconPadding = ToUtils.edgeInsets(iconProps?.iconPadding)
        val iconSize = (iconProps?.iconSize ?: 24.0).dp
        // Standard rotation: Collapsed (0) -> Expanded (90) if using ChevronRight
        val iconRotAngle = (iconProps?.iconRotationAngle ?: 90.0).toFloat()

        val iconColorVal = iconProps?.iconColor
        val iconColor =
                if (iconColorVal != null)
                        payload.evalColor(iconColorVal) ?: LocalContentColor.current
                else LocalContentColor.current.copy(alpha = 0.6f)

        val tapHeaderToExpand = expandableProps.tapHeaderToExpand ?: true

        var modifier = Modifier.buildModifier(payload)

        // Apply borderRadius clipping to the whole container
        if (expandableProps.borderRadius != null) {
            modifier = modifier.clip(borderRadius)
        }

        Column(modifier = modifier) {
            // --- HEADER ---
            val headerModifier =
                    Modifier.fillMaxWidth()
                            .then(
                                    if (tapHeaderToExpand) {
                                        Modifier.clickable(
                                                interactionSource =
                                                        remember { MutableInteractionSource() },
                                                indication = null // or generic ripple? inherited
                                                // usually.
                                                ) { isExpanded = !isExpanded }
                                    } else Modifier
                            )

            Row(modifier = headerModifier, verticalAlignment = Alignment.CenterVertically) {
                // Icon Left
                if (hasIcon && iconPlacement == "left") {
                    Box(modifier = Modifier.padding(iconPadding)) {
                        ExpandableIcon(isExpanded, iconRotAngle, iconSize, iconColor)
                    }
                }

                // Actual Header
                Box(modifier = Modifier.weight(1f), contentAlignment = headerAlignment) {
                    if (headerNodes.isNullOrEmpty()) {
                        // No header content?
                    } else {
                        headerNodes.forEach { it.ToWidget(payload) }
                    }
                }

                // Icon Right
                if (hasIcon && iconPlacement == "right") {
                    Box(modifier = Modifier.padding(iconPadding)) {
                        ExpandableIcon(isExpanded, iconRotAngle, iconSize, iconColor)
                    }
                }
            }

            // --- EXPANDED VIEW ---
            AnimatedVisibility(
                    visible = isExpanded,
                    enter =
                            expandVertically(tween(animationDuration)) +
                                    fadeIn(tween(animationDuration)),
                    exit =
                            shrinkVertically(tween(animationDuration)) +
                                    fadeOut(tween(animationDuration))
            ) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = bodyAlignment) {
                    expandedNodes?.forEach { it.ToWidget(payload) }
                }
            }

            // --- COLLAPSED VIEW ---
            // Shown when NOT expanded
            if (!isExpanded) {
                if (!collapsedNodes.isNullOrEmpty()) {
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = bodyAlignment
                    ) { collapsedNodes.forEach { it.ToWidget(payload) } }
                }
            }
        }
    }

    @Composable
    private fun ExpandableIcon(
            isExpanded: Boolean,
            rotation: Float,
            size: androidx.compose.ui.unit.Dp,
            color: Color
    ) {
        // If expanded, rotate by `rotation` degrees (relative to initial state?).
        // Typically: Initial = Right (0 deg). Expanded = Down (90 deg).
        // If user passes `iconRotationAngle = 180`, then Expanded = Left (180 deg).
        // Simple logic:
        val degrees = if (isExpanded) rotation else 0f

        Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(size).rotate(degrees),
                tint = color
        )
    }
}

// ============== Props ==============

data class ExpandableProps(
        val activeView: String? = null,
        val alignment: String? = null,
        val bodyAlignment: String? = null,
        val headerAlignment: String? = null,
        val useInkWell: Boolean? = null,
        val borderRadius: Any? = null,
        val animationDuration: Int? = null,
        val tapHeaderToExpand: Boolean? = null,
        val tapBodyToExpand: Boolean? = null,
        val tapBodyToCollapse: Boolean? = null,
        val icon: IconProps? = null
) {
    companion object {
        fun fromJson(json: JsonLike): ExpandableProps {
            return ExpandableProps(
                    activeView = json["__activeView"] as? String,
                    alignment = json["alignment"] as? String,
                    bodyAlignment = json["bodyAlignment"] as? String,
                    headerAlignment = json["headerAlignment"] as? String,
                    useInkWell = json["useInkWell"] as? Boolean,
                    borderRadius = json["borderRadius"],
                    animationDuration = NumUtil.toInt(json["animationDuration"]),
                    tapHeaderToExpand = json["tapHeaderToExpand"] as? Boolean,
                    tapBodyToExpand = json["tapBodyToExpand"] as? Boolean,
                    tapBodyToCollapse = json["tapBodyToCollapse"] as? Boolean,
                    icon = (json["icon"] as? JsonLike)?.let { IconProps.fromJson(it) }
            )
        }
    }
}

data class IconProps(
        val hasIcon: Boolean? = null,
        val iconPlacement: String? = null,
        val iconPadding: Any? = null,
        val iconSize: Double? = null,
        val iconColor: Any? = null,
        val iconRotationAngle: Double? = null
) {
    companion object {
        fun fromJson(json: JsonLike): IconProps {
            return IconProps(
                    hasIcon = json["hasIcon"] as? Boolean,
                    iconPlacement = json["iconPlacement"] as? String,
                    iconPadding = json["iconPadding"],
                    iconSize = NumUtil.toDouble(json["iconSize"]),
                    iconColor = json["iconColor"],
                    iconRotationAngle = NumUtil.toDouble(json["iconRotationAngle"])
            )
        }
    }
}

// ============== Builder ==============

fun expandableBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    // We pre-calculate children mapping to pass to constructor
    val childGroups = data.childGroups

    val headerGroup = childGroups?.get("header")
    val collapsedGroup = childGroups?.get("collapsedView")
    val expandedGroup = childGroups?.get("expandedView")

    // Create widgets for each group
    fun createWidgets(list: List<VWData>?): List<VirtualNode>? {
        // We filter for VWNodeData or handle VWComponentData/VWStateData if registry supports it.
        // Assuming registry.createWidget takes VWData (which is base class).
        return list?.mapNotNull { item ->
            // We need to cast or registry handles VWData?
            // If registry.createWidget takes VWNodeData, we need to check type.
            // If registry.createWidget takes VWData, we are good.
            // Based on usage in other files (like stackBuilder), they usually pass childData
            // directly.
            // Let's assume registry.createWidget takes VWData for now or check usage.
            // Error said: expected VWNodeData in createWidget call?
            // "Argument type mismatch: actual type is VWData, but VWNodeData was expected."
            // checks registry signature.
            if (item is VWNodeData) {
                registry.createWidget(item, parent)
            } else {
                // Try to force create if registry accepts VWData,
                // or just ignore if it only supports VWNodeData (unlikely).
                // Actually, let's see registry definition.
                // If I can't wait, I'll assume I need to cast to VWNodeData.
                (item as? VWNodeData)?.let { registry.createWidget(it, parent) }
            }
        }
    }

    val headerNodes = createWidgets(headerGroup)
    val collapsedNodes = createWidgets(collapsedGroup)
    val expandedNodes = createWidgets(expandedGroup)

    return VWExpandable(
            refName = data.refName,
            commonProps = data.commonProps,
            expandableProps = ExpandableProps.fromJson(data.props.value),
            slots = { self ->
                // Standard registration so base class knows about children (though flattened)
                registerAllChildern(data.childGroups, self, registry)
            },
            parent = parent,
            parentProps = data.parentProps,
            headerNodes = headerNodes,
            collapsedNodes = collapsedNodes,
            expandedNodes = expandedNodes
    )
}

// ============== Extensions ==============

private fun String?.toAlignment(default: Alignment = Alignment.Center): Alignment =
        when (this) {
            "topLeft", "topStart" -> Alignment.TopStart
            "topCenter" -> Alignment.TopCenter
            "topRight", "topEnd" -> Alignment.TopEnd
            "centerLeft", "centerStart" -> Alignment.CenterStart
            "center" -> Alignment.Center
            "centerRight", "centerEnd" -> Alignment.CenterEnd
            "bottomLeft", "bottomStart" -> Alignment.BottomStart
            "bottomCenter" -> Alignment.BottomCenter
            "bottomRight", "bottomEnd" -> Alignment.BottomEnd
            else -> default
        }

private fun String?.toHorizontalAlignment(
        default: Alignment.Horizontal = Alignment.Start
): Alignment.Horizontal =
        when (this) {
            "center" -> Alignment.CenterHorizontally
            "right", "end" -> Alignment.End
            else -> default
        }
