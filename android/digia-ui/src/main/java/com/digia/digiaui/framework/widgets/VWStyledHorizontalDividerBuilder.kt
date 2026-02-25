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
import com.digia.digiaui.framework.utils.NumUtil
import kotlin.math.max

/** Styled Horizontal Divider Props */
data class StyledHorizontalDividerProps(
        val thickness: ExprOr<Double>? = null,
        val indent: ExprOr<Double>? = null,
        val endIndent: ExprOr<Double>? = null,
        val height: ExprOr<Double>? = null,
        val borderPattern: BorderPatternProps? = null,
        val color: ExprOr<String>? = null,
        val gradient: GradientProps? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): StyledHorizontalDividerProps {
            val colorType = json["colorType"] as? JsonLike
            val borderPattern = json["borderPattern"] as? JsonLike

            return StyledHorizontalDividerProps(
                    thickness = ExprOr.fromJson(json["thickness"]),
                    indent = ExprOr.fromJson(json["indent"]),
                    endIndent = ExprOr.fromJson(json["endIndent"]),
                    height = ExprOr.fromJson(json["height"]),
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

/** Border Pattern Props for divider styling */
data class BorderPatternProps(
        val value: String? = null,
        val strokeCap: String? = null,
        val dashPattern: List<Double>? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): BorderPatternProps {
            val dashPatternRaw = json["dashPattern"] as? List<*>
            val dashPattern = dashPatternRaw?.mapNotNull { NumUtil.toDouble(it) }

            return BorderPatternProps(
                    value = json["value"] as? String,
                    strokeCap = json["strokeCap"] as? String,
                    dashPattern = dashPattern
            )
        }
    }
}

/**
 * Virtual Styled Horizontal Divider Widget
 *
 * Renders a horizontal divider with support for:
 * - Solid, dashed, and dotted line styles
 * - Custom thickness and color
 * - Gradient support
 * - Indentation from start and end
 * - Configurable stroke caps
 * 
 * Mirrors Flutter's DividerWithPattern implementation for WYSIWYG consistency
 */
class VWStyledHorizontalDivider(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        dividerProps: StyledHorizontalDividerProps
) :
        VirtualLeafNode<StyledHorizontalDividerProps>(
                props = dividerProps,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        // Evaluate properties - matching Flutter's DividerWithPattern defaults
        val thickness = payload.evalExpr(props.thickness) ?: 1.0
        val indent = payload.evalExpr(props.indent) ?: 0.0
        val endIndent = payload.evalExpr(props.endIndent) ?: 0.0
        // In Flutter: size is the container height (space), defaults to 16 from theme
        val containerHeight = payload.evalExpr(props.height) ?: 16.0

        // Evaluate line style and pattern
        val lineStyle = props.borderPattern?.value ?: "solid"
        val strokeCapStr = props.borderPattern?.strokeCap ?: "butt"
        val customDashPattern = props.borderPattern?.dashPattern

        // Determine actual pattern based on line style - matching Flutter's getData()
        val (patternType, actualDashPattern) = getLineStyleData(lineStyle, thickness, customDashPattern)

        // Evaluate color - Flutter defaults to black, we'll use Gray as fallback
        val color = props.color?.let { payload.evalExpr(it) }?.let { payload.evalColor(it) }
                ?: Color.Gray

        // Convert stroke cap
        val strokeCap = toStrokeCap(strokeCapStr)

        // Create path effect for dashed/dotted lines
        val pathEffect = when (patternType) {
            LinePattern.DASHED, LinePattern.DOTTED -> {
                val pattern = if (actualDashPattern.isNotEmpty()) actualDashPattern else listOf(3.0, 3.0)
                PathEffect.dashPathEffect(pattern.map { it.toFloat() }.toFloatArray())
            }
            LinePattern.SOLID -> null
        }

        // Pre-evaluate gradient colors BEFORE entering Canvas (since evalColor is @Composable)
        val gradientColors: List<Color>? = props.gradient?.colorList?.mapNotNull { 
            payload.evalColor(it.color) 
        }?.takeIf { it.isNotEmpty() }
        
        val gradientStops: List<Float>? = props.gradient?.colorList?.mapNotNull { 
            it.stop?.toFloat() 
        }
        
        val gradientType = props.gradient?.type
        val gradientBegin = props.gradient?.begin
        val gradientEnd = props.gradient?.end
        val gradientCenter = props.gradient?.center
        val gradientRadius = props.gradient?.radius

        // Render divider - matching Flutter's structure:
        // Padding(indent) -> SizedBox(height: size) -> Center -> CustomPaint(size: Size(infinity, thickness))
        Box(
                modifier = Modifier
                        .fillMaxWidth()
                        .height(containerHeight.dp)
                        .padding(start = indent.dp, end = endIndent.dp)
        ) {
            // Canvas size matches Flutter's CustomPaint size: Size(maxWidth, thickness)
            // This is critical for WYSIWYG - the canvas height equals the stroke thickness
            Canvas(
                    modifier = Modifier
                            .fillMaxWidth()
                            .height(thickness.dp)
                            .align(Alignment.Center)
            ) {
                // In Flutter: path draws from (0, size.height/2) to (size.width, size.height/2)
                // Since our canvas height = thickness, the line is drawn at y = thickness/2
                val y = size.height / 2f
                
                // Create gradient brush using pre-evaluated colors
                val gradientBrush = if (gradientColors != null && gradientColors.isNotEmpty()) {
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

                // Draw line with gradient or solid color
                if (gradientBrush != null) {
                    drawLine(
                            brush = gradientBrush,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = thickness.toFloat(),
                            cap = strokeCap,
                            pathEffect = pathEffect
                    )
                } else {
                    drawLine(
                            color = color,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = thickness.toFloat(),
                            cap = strokeCap,
                            pathEffect = pathEffect
                    )
                }
            }
        }
    }

    /**
     * Creates a gradient brush with proper size-aware offsets
     * Matches Flutter's gradient.createShader(rect) behavior
     */
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
                // Convert alignment strings to actual pixel offsets based on canvas size
                // Flutter uses Alignment which maps -1..1 to 0..size
                val startOffset = alignmentToOffset(begin, canvasSize, isEnd = false)
                val endOffset = alignmentToOffset(end, canvasSize, isEnd = true)

                if (hasValidStops) {
                    Brush.linearGradient(
                            colorStops = stops!!.zip(colors).toTypedArray(),
                            start = startOffset,
                            end = endOffset
                    )
                } else {
                    Brush.linearGradient(
                            colors = colors,
                            start = startOffset,
                            end = endOffset
                    )
                }
            }
            "angular" -> {
                val centerOffset = alignmentToOffset(center, canvasSize)
                val radiusValue = ((radius ?: 0.5) * minOf(canvasSize.width, canvasSize.height)).toFloat()

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

    /**
     * Converts Flutter-style alignment string to pixel offset
     * Flutter Alignment: (-1,-1) = topLeft, (1,1) = bottomRight
     * Maps to: x = (alignment.x + 1) / 2 * width, y = (alignment.y + 1) / 2 * height
     */
    private fun alignmentToOffset(alignment: String?, size: Size, isEnd: Boolean = false): Offset {
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
                // Default: left to right for horizontal divider gradient
                if (isEnd) Offset(size.width, size.height / 2f)
                else Offset(0f, size.height / 2f)
            }
        }
    }

    /**
     * Get line style data based on style type
     * Matches Flutter's getData() method exactly
     */
    private fun getLineStyleData(
            style: String,
            thickness: Double,
            customDashPattern: List<Double>?
    ): Pair<LinePattern, List<Double>> {
        return when (style.lowercase()) {
            "dashed" -> {
                // Flutter: [(5 * max(thickness, 1)), 2 * max(thickness, 1)]
                val pattern = customDashPattern ?: listOf(
                        5.0 * max(thickness, 1.0),
                        2.0 * max(thickness, 1.0)
                )
                Pair(LinePattern.DASHED, pattern)
            }
            "dotted" -> {
                // Flutter: [max(thickness, 1), max(thickness, 1)]
                val pattern = listOf(max(thickness, 1.0), max(thickness, 1.0))
                Pair(LinePattern.DOTTED, pattern)
            }
            "dashdotted" -> {
                // Flutter: [3 * max, max, max, max]
                val pattern = listOf(
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

    /** Convert stroke cap string to StrokeCap */
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

/** Builder function for StyledHorizontalDivider */
fun styledHorizontalDividerBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWStyledHorizontalDivider(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            dividerProps = StyledHorizontalDividerProps.fromJson(data.props.value)
    )
}
