package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike
import kotlin.math.max

/** Styled Vertical Divider Props */
data class StyledVerticalDividerProps(
        val thickness: ExprOr<Double>? = null,
        val indent: ExprOr<Double>? = null,
        val endIndent: ExprOr<Double>? = null,
        val width: ExprOr<Double>? = null,
        val borderPattern: BorderPatternProps? = null,
        val color: ExprOr<String>? = null,
        val gradient: GradientProps? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): StyledVerticalDividerProps {
            val colorType = json["colorType"] as? JsonLike
            val borderPattern = json["borderPattern"] as? JsonLike
            val size = json["size"] as? JsonLike

            // Handle width from root or size object
            val widthVal = json["width"] ?: size?.get("width")

            // Handle thickness from root or size object (defensive)
            val thicknessVal = json["thickness"] ?: size?.get("thickness")

            return StyledVerticalDividerProps(
                    thickness = ExprOr.fromJson(thicknessVal),
                    indent = ExprOr.fromJson(json["indent"]),
                    endIndent = ExprOr.fromJson(json["endIndent"]),
                    width = ExprOr.fromJson(widthVal),
                    borderPattern = borderPattern?.let { BorderPatternProps.fromJson(it) },
                    color = ExprOr.fromJson(colorType?.get("color")),
                    gradient =
                            (colorType?.get("gradiant") as? JsonLike)?.let {
                                GradientProps.fromJson(it)
                            }
            )
        }
    }
}

/**
 * Virtual Styled Vertical Divider Widget
 *
 * Renders a vertical divider with support for:
 * - Solid, dashed, and dotted line styles
 * - Custom thickness and color
 * - Gradient support
 * - Indentation from top and bottom
 * - Configurable stroke caps
 *
 * Mirrors Flutter's VerticalDivider implementation
 */
class VWVerticalDivider(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        dividerProps: StyledVerticalDividerProps
) :
        VirtualLeafNode<StyledVerticalDividerProps>(
                props = dividerProps,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val thickness = payload.evalExpr(props.thickness) ?: 2.0 // Default 2.0 from schema
        val indent = payload.evalExpr(props.indent) ?: 0.0
        val endIndent = payload.evalExpr(props.endIndent) ?: 0.0
        val containerWidth = payload.evalExpr(props.width) ?: 10.0 // Default 10.0 from schema

        val lineStyle = props.borderPattern?.value ?: "solid"
        val strokeCapStr = props.borderPattern?.strokeCap ?: "butt"
        val customDashPattern = props.borderPattern?.dashPattern

        val (patternType, actualDashPattern) =
                getLineStyleData(lineStyle, thickness, customDashPattern)

        val color =
                props.color?.let { payload.evalExpr(it) }?.let { payload.evalColor(it) }
                        ?: Color.Gray

        val strokeCap = toStrokeCap(strokeCapStr)

        val pathEffect =
                when (patternType) {
                    LinePattern.DASHED, LinePattern.DOTTED -> {
                        val pattern =
                                if (actualDashPattern.isNotEmpty()) actualDashPattern
                                else listOf(3.0, 3.0)
                        PathEffect.dashPathEffect(pattern.map { it.toFloat() }.toFloatArray())
                    }
                    LinePattern.SOLID -> null
                }

        val gradientColors: List<Color>? =
                props.gradient?.colorList?.mapNotNull { payload.evalColor(it.color) }?.takeIf {
                    it.isNotEmpty()
                }

        val gradientStops: List<Float>? =
                props.gradient?.colorList?.mapNotNull { it.stop?.toFloat() }

        val gradientType = props.gradient?.type
        val gradientBegin = props.gradient?.begin
        val gradientEnd = props.gradient?.end
        val gradientCenter = props.gradient?.center
        val gradientRadius = props.gradient?.radius

        Box(
                modifier =
                        Modifier.fillMaxHeight()
                                .width(containerWidth.dp)
                                .padding(top = indent.dp, bottom = endIndent.dp)
        ) {
            Canvas(
                    modifier = Modifier.fillMaxHeight().width(thickness.dp).align(Alignment.Center)
            ) {
                val x = size.width / 2f

                val gradientBrush =
                        if (gradientColors != null && gradientColors.isNotEmpty()) {
                            createGradientBrush(
                                    gradientType,
                                    gradientColors,
                                    gradientStops,
                                    gradientBegin,
                                    gradientEnd,
                                    gradientCenter,
                                    gradientRadius,
                                    size
                            )
                        } else null

                if (gradientBrush != null) {
                    drawLine(
                            brush = gradientBrush,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = thickness.toFloat(),
                            cap = strokeCap,
                            pathEffect = pathEffect
                    )
                } else {
                    drawLine(
                            color = color,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = thickness.toFloat(),
                            cap = strokeCap,
                            pathEffect = pathEffect
                    )
                }
            }
        }
    }

    private fun createGradientBrush(
            type: String?,
            colors: List<Color>,
            stops: List<Float>?,
            begin: String?,
            end: String?,
            center: String?,
            radius: Double?,
            canvasSize: Size
    ): Brush? {
        if (colors.isEmpty()) return null

        val hasValidStops = stops != null && stops.size == colors.size

        return when (type) {
            "linear" -> {
                val startOffset = alignmentToOffset(begin, canvasSize, isVertical = true)
                val endOffset = alignmentToOffset(end, canvasSize, isVertical = true, isEnd = true)

                if (hasValidStops) {
                    Brush.linearGradient(
                            colorStops = stops!!.zip(colors).toTypedArray(),
                            start = startOffset,
                            end = endOffset
                    )
                } else {
                    Brush.linearGradient(colors = colors, start = startOffset, end = endOffset)
                }
            }
            "angular" -> {
                val centerOffset = alignmentToOffset(center, canvasSize)
                val radiusValue =
                        ((radius ?: 0.5) * minOf(canvasSize.width, canvasSize.height)).toFloat()

                if (hasValidStops) {
                    Brush.radialGradient(
                            colorStops = stops!!.zip(colors).toTypedArray(),
                            center = centerOffset,
                            radius = radiusValue
                    )
                } else {
                    Brush.radialGradient(
                            colors = colors,
                            center = centerOffset,
                            radius = radiusValue
                    )
                }
            }
            else -> null
        }
    }

    private fun alignmentToOffset(
            alignment: String?,
            size: Size,
            isVertical: Boolean = false,
            isEnd: Boolean = false
    ): Offset {
        return when (alignment) {
            "topLeft" -> Offset(0f, 0f)
            "topCenter" -> Offset(size.width / 2f, 0f)
            "topRight" -> Offset(size.width, 0f)
            "centerLeft" -> Offset(0f, size.height / 2f)
            "center" -> Offset(size.width / 2f, size.height / 2f)
            "centerRight" -> Offset(size.width, size.height / 2f)
            "bottomLeft" -> Offset(0f, size.height)
            "bottomCenter" -> Offset(size.width / 2f, size.height)
            "bottomRight" -> Offset(size.width, size.height)
            else -> {
                // Default: top to bottom for vertical divider gradient
                if (isVertical) {
                    if (isEnd) Offset(size.width / 2f, size.height) else Offset(size.width / 2f, 0f)
                } else {
                    if (isEnd) Offset(size.width, size.height / 2f)
                    else Offset(0f, size.height / 2f)
                }
            }
        }
    }

    private fun getLineStyleData(
            style: String,
            thickness: Double,
            customDashPattern: List<Double>?
    ): Pair<LinePattern, List<Double>> {
        return when (style.lowercase()) {
            "dashed" -> {
                val pattern =
                        customDashPattern
                                ?: listOf(5.0 * max(thickness, 1.0), 2.0 * max(thickness, 1.0))
                Pair(LinePattern.DASHED, pattern)
            }
            "dotted" -> {
                val pattern = listOf(max(thickness, 1.0), max(thickness, 1.0))
                Pair(LinePattern.DOTTED, pattern)
            }
            "dashdotted" -> {
                val pattern =
                        listOf(
                                3.0 * max(thickness, 1.0),
                                max(thickness, 1.0),
                                max(thickness, 1.0),
                                max(thickness, 1.0)
                        )
                Pair(LinePattern.DASHED, pattern)
            }
            else -> {
                Pair(LinePattern.SOLID, emptyList())
            }
        }
    }

    private fun toStrokeCap(strokeCap: String?): StrokeCap {
        return when (strokeCap?.lowercase()) {
            "round" -> StrokeCap.Round
            "square" -> StrokeCap.Square
            else -> StrokeCap.Butt
        }
    }

    enum class LinePattern {
        SOLID,
        DASHED,
        DOTTED
    }
}

/** Builder function for StyledVerticalDivider */
fun vwVerticalDividerBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWVerticalDivider(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            dividerProps = StyledVerticalDividerProps.fromJson(data.props.value)
    )
}
