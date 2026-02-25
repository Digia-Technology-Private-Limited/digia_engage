package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.textStyle
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.utils.ToUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import LocalUIResources
import resourceIcon

/**
 * Button widget properties
 * Maps to the Flutter VWButton props structure from button.dart schema
 */
data class ButtonProps(
    val isDisabled: ExprOr<Boolean>?,
    val disabledStyle: DisabledStyleProps?,
    val defaultStyle: DefaultStyleProps?,
    val text: TextContentProps?,
    val shape: ShapeProps?,
    val leadingIcon: IconContentProps?,
    val trailingIcon: IconContentProps?,
    val onClick: ActionFlow?
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): ButtonProps {
            return ButtonProps(
                isDisabled = ExprOr.fromValue(json["isDisabled"]),
                disabledStyle = (json["disabledStyle"] as? JsonLike)?.let { 
                    DisabledStyleProps.fromJson(it) 
                },
                defaultStyle = (json["defaultStyle"] as? JsonLike)?.let { 
                    DefaultStyleProps.fromJson(it) 
                },
                text = (json["text"] as? JsonLike)?.let { 
                    TextContentProps.fromJson(it) 
                },
                shape = (json["shape"] as? JsonLike)?.let { 
                    ShapeProps.fromJson(it) 
                },
                leadingIcon = (json["leadingIcon"] as? JsonLike)?.let { 
                    IconContentProps.fromJson(it) 
                },
                trailingIcon = (json["trailingIcon"] as? JsonLike)?.let { 
                    IconContentProps.fromJson(it) 
                },
                onClick = (json["onClick"] as? JsonLike)?.let { 
                    ActionFlow.fromJson(it) 
                }
            )
        }
    }
}

/**
 * Disabled style properties
 */
data class DisabledStyleProps(
    val backgroundColor: ExprOr<String>?,
    val disabledTextColor: ExprOr<String>?,
    val disabledIconColor: ExprOr<String>?,
    val previewInUI: ExprOr<Boolean>?
) {
    companion object {
        fun fromJson(json: JsonLike): DisabledStyleProps {
            return DisabledStyleProps(
                backgroundColor = ExprOr.fromValue(json["backgroundColor"]),
                disabledTextColor = ExprOr.fromValue(json["disabledTextColor"]),
                disabledIconColor = ExprOr.fromValue(json["disabledIconColor"]),
                previewInUI = ExprOr.fromValue(json["previewInUI"])
            )
        }
    }
}

/**
 * Default style properties
 */
data class DefaultStyleProps(
    val backgroundColor: ExprOr<String>?,
    val padding: String?,
    val elevation: Double?,
    val shadowColor: ExprOr<String>?,
    val alignment: String?,
    val height: String?,
    val width: String?
) {
    companion object {
        fun fromJson(json: JsonLike): DefaultStyleProps {
            return DefaultStyleProps(
                backgroundColor = ExprOr.fromValue(json["backgroundColor"]),
                padding = json["padding"] as? String,
                elevation = NumUtil.toDouble(json["elevation"]),
                shadowColor = ExprOr.fromValue(json["shadowColor"]),
                alignment = json["alignment"] as? String,
                height = json["height"] as? String,
                width = json["width"] as? String
            )
        }
    }
}

/**
 * Text content properties for button label
 */
data class TextContentProps(
    val text: ExprOr<String>?,
    val textStyle: JsonLike?,
    val alignment: String?,
    val maxLines: Int?,
    val overflow: String?
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): TextContentProps {
            return TextContentProps(
                text = ExprOr.fromValue(json["text"]),
                textStyle = json["textStyle"] as? JsonLike,
                alignment = json["alignment"] as? String,
                maxLines = NumUtil.toInt(json["maxLines"]),
                overflow = json["overflow"] as? String
            )
        }
    }
}

/**
 * Shape properties for button styling
 */
data class ShapeProps(
    val value: String?,
    val borderRadius: String?,
    val eccentricity: Double?,
    val borderColor: ExprOr<String>?,
    val borderWidth: Double?,
    val borderStyle: String?
) {
    companion object {
        fun fromJson(json: JsonLike): ShapeProps {
            return ShapeProps(
                value = json["value"] as? String,
                borderRadius = json["borderRadius"] as? String,
                eccentricity = NumUtil.toDouble(json["eccentricity"]),
                borderColor = ExprOr.fromValue(json["borderColor"]),
                borderWidth = NumUtil.toDouble(json["borderWidth"]),
                borderStyle = json["borderStyle"] as? String
            )
        }
    }
}

/**
 * Icon content properties for leading/trailing icons
 */
data class IconContentProps(
    val iconData: JsonLike?,
    val iconSize: Double?,
    val iconColor: ExprOr<String>?
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): IconContentProps {
            return IconContentProps(
                iconData = json["iconData"] as? JsonLike,
                iconSize = NumUtil.toDouble(json["iconSize"]),
                iconColor = ExprOr.fromValue(json["iconColor"])
            )
        }
    }
}

/**
 * Virtual Button widget - Jetpack Compose implementation
 * 
 * Mirrors the Flutter VWButton implementation from button.dart
 * Uses Material3 Button component with custom styling support
 */
class VWButton(
    refName: String?,
    commonProps: CommonProps?,
    parent: VirtualNode?,
    parentProps: Props? = null,
    props: ButtonProps
) : VirtualLeafNode<ButtonProps>(
    props = props,
    commonProps = commonProps,
    parent = parent,
    refName = refName,
    parentProps = parentProps
) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current

        // Evaluate disabled state
        val isDisabled = payload.evalObserve(props.isDisabled) ?: (props.onClick == null)

        // Get style properties
        val defaultStyle = props.defaultStyle
        val disabledStyle = props.disabledStyle

        // Background colors
        val defaultBgColor = defaultStyle?.backgroundColor?.let { payload.evalColor(it.value) }
        val disabledBgColor = disabledStyle?.backgroundColor?.let { payload.evalColor(it.value) }
        
        // Shadow color - default to black if not specified
        val shadowColor = defaultStyle?.shadowColor?.let { payload.evalColor(it.value) } 
            ?: Color.Black

        // Elevation - schema default is 2
        val elevationValue = (defaultStyle?.elevation ?: 2.0).dp

        // Parse width and height
        val screenWidthDp = configuration.screenWidthDp.dp
        val screenHeightDp = configuration.screenHeightDp.dp
        
        val widthDp = defaultStyle?.width?.let { 
            parseDimension(it, screenWidthDp, density) 
        }
        val heightDp = defaultStyle?.height?.let { 
            parseDimension(it, screenHeightDp, density) 
        }

        // Padding
        val padding = ToUtils.edgeInsets(
            defaultStyle?.padding,
            or = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        )

        // Shape
        val buttonShape = toButtonShape(props.shape, payload)

        // Content alignment
        val contentAlignment = toContentAlignment(defaultStyle?.alignment)

        // Disabled colors for content
        val disabledTextColor = disabledStyle?.disabledTextColor?.let { 
            payload.evalColor(it.value) 
        }
        val disabledIconColor = disabledStyle?.disabledIconColor?.let { 
            payload.evalColor(it.value) 
        }

        // Button colors with proper disabled state handling
        val buttonColors = ButtonDefaults.elevatedButtonColors(
            containerColor = defaultBgColor ?: MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = disabledBgColor 
                ?: defaultBgColor?.copy(alpha = 0.38f)
                ?: MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.38f)
        )

        // Build modifier with custom shadow and size
        var buttonModifier = Modifier.buildModifier(payload)
        
        // Apply width if specified
        if (widthDp != null) {
            buttonModifier = buttonModifier.width(widthDp)
        }
        
        // Apply height if specified
        if (heightDp != null) {
            buttonModifier = buttonModifier.height(heightDp)
        }
        
        // Apply shadow if elevation > 0 and not disabled
        if (elevationValue.value > 0 && !isDisabled) {
            buttonModifier = buttonModifier.shadow(
                elevation = elevationValue,
                shape = buttonShape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
        }

        // Use ElevatedButton with elevation set to 0 since we handle shadow via modifier
        ElevatedButton(
            onClick = {
                if (!isDisabled && props.onClick != null) {
                    payload.executeAction(
                        context = context,
                        actionFlow = props.onClick,
                        actionExecutor = actionExecutor,
                        stateContext = stateContext,
                        resourcesProvider = resources
                    )
                }
            },
            enabled = !isDisabled,
            shape = buttonShape,
            colors = buttonColors,
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp
            ),
            contentPadding = padding,
            modifier = buttonModifier
        ) {
            ButtonContent(
                payload = payload,
                isDisabled = isDisabled,
                disabledTextColor = disabledTextColor,
                disabledIconColor = disabledIconColor
            )
        }
    }

    /**
     * Parses dimension string to Dp
     * Supports: "100" (dp), "100px" (pixels), "50%" (percentage of reference)
     */
    @Composable
    private fun parseDimension(value: String, referenceDp: Dp, density: androidx.compose.ui.unit.Density): Dp? {
        val trimmed = value.trim()
        
        return when {
            // Percentage
            trimmed.endsWith("%") -> {
                val percent = trimmed.dropLast(1).toDoubleOrNull() ?: return null
                referenceDp * (percent.toFloat() / 100f)
            }
            // Pixels
            trimmed.endsWith("px") -> {
                val px = trimmed.dropLast(2).toDoubleOrNull() ?: return null
                with(density) { px.toFloat().toDp() }
            }
            // dp (explicit or implicit)
            trimmed.endsWith("dp") -> {
                val dp = trimmed.dropLast(2).toDoubleOrNull() ?: return null
                dp.dp
            }
            // Plain number - treat as dp
            else -> {
                val dp = trimmed.toDoubleOrNull() ?: return null
                dp.dp
            }
        }
    }

    @Composable
    private fun ButtonContent(
        payload: RenderPayload,
        isDisabled: Boolean,
        disabledTextColor: Color?,
        disabledIconColor: Color?
    ) {
        val textProps = props.text
        val leadingIconProps = props.leadingIcon
        val trailingIconProps = props.trailingIcon

        val hasLeadingIcon = leadingIconProps?.iconData != null
        val hasTrailingIcon = trailingIconProps?.iconData != null

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon
            if (hasLeadingIcon && leadingIconProps != null) {
                val iconColor = if (isDisabled) {
                    disabledIconColor
                } else {
                    leadingIconProps.iconColor?.let { payload.evalColor(it.value) }
                }
                val iconSize = (leadingIconProps.iconSize ?: 16.0).dp
                val iconKey = extractIconKey(leadingIconProps.iconData)
                val iconData = iconKey?.let { resourceIcon(it) }

                if (iconData != null) {
                    Icon(
                        imageVector = iconData,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = iconColor ?: Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            // Text content
            if (textProps != null) {
                val text = payload.evalObserve(textProps.text) ?: ""
                val textColor = if (isDisabled) {
                    disabledTextColor
                } else {
                    null
                }
                
                val style = payload.textStyle(textProps.textStyle)?.let { baseStyle ->
                    textColor?.let { baseStyle.copy(color = it) } ?: baseStyle
                }

                // Parse overflow to TextOverflow
                val overflow = when (textProps.overflow) {
                    "ellipsis" -> androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    "clip" -> androidx.compose.ui.text.style.TextOverflow.Clip
                    "visible" -> androidx.compose.ui.text.style.TextOverflow.Visible
                    else -> androidx.compose.ui.text.style.TextOverflow.Ellipsis // Default to ellipsis
                }

                Text(
                    text = text.toString(),
                    style = style ?: androidx.compose.ui.text.TextStyle.Default,
                    maxLines = textProps.maxLines ?: 1,
                    overflow = overflow,
                    modifier = Modifier.weight(1f, fill = false)  // Allow text to shrink and ellipsize
                )
            }

            // Trailing icon
            if (hasTrailingIcon && trailingIconProps != null) {
                Spacer(modifier = Modifier.width(4.dp))
                
                val iconColor = if (isDisabled) {
                    disabledIconColor
                } else {
                    trailingIconProps.iconColor?.let { payload.evalColor(it.value) }
                }
                val iconSize = (trailingIconProps.iconSize ?: 16.0).dp
                val iconKey = extractIconKey(trailingIconProps.iconData)
                val iconData = iconKey?.let { resourceIcon(it) }

                if (iconData != null) {
                    Icon(
                        imageVector = iconData,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = iconColor ?: Color.Unspecified
                    )
                }
            }
        }
    }

    /**
     * Extracts icon key from iconData map
     * IconData format: { "name": "icon_name", "type": "material" } or similar
     */
    private fun extractIconKey(iconData: JsonLike?): String? {
        if (iconData == null) return null
        
        // Try common keys for icon identifier
        return iconData["name"] as? String
            ?: iconData["icon"] as? String
            ?: iconData["key"] as? String
    }

    /**
     * Converts shape properties to Compose Shape
     * Maps Flutter's OutlinedBorder to Compose shapes
     */
    private fun toButtonShape(shapeProps: ShapeProps?, payload: RenderPayload): Shape {
        if (shapeProps == null) {
            // Default to stadium shape (fully rounded ends like Flutter)
            return RoundedCornerShape(50)
        }

        return when (shapeProps.value) {
            "stadium" -> RoundedCornerShape(50) // Fully rounded ends
            "circle" -> CircleShape
            "roundedRect" -> ToUtils.borderRadius(
                shapeProps.borderRadius,
                or = RoundedCornerShape(0.dp)
            )
            "none" -> RoundedCornerShape(0.dp)
            else -> ToUtils.borderRadius(
                shapeProps.borderRadius,
                or = RoundedCornerShape(0.dp)
            )
        }
    }

    /**
     * Converts alignment string to Compose Alignment
     */
    private fun toContentAlignment(alignment: String?): Alignment {
        return when (alignment) {
            "center" -> Alignment.Center
            "centerLeft" -> Alignment.CenterStart
            "centerRight" -> Alignment.CenterEnd
            "topCenter" -> Alignment.TopCenter
            "bottomCenter" -> Alignment.BottomCenter
            "topLeft" -> Alignment.TopStart
            "topRight" -> Alignment.TopEnd
            "bottomLeft" -> Alignment.BottomStart
            "bottomRight" -> Alignment.BottomEnd
            else -> Alignment.Center
        }
    }
}

/**
 * Builder function for Button widget
 * Used by VirtualWidgetRegistry to create VWButton instances
 */
fun buttonBuilder(
    data: VWNodeData, 
    parent: VirtualNode?, 
    registry: VirtualWidgetRegistry
): VirtualNode {
    return VWButton(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = ButtonProps.fromJson(data.props.value)
    )
}
