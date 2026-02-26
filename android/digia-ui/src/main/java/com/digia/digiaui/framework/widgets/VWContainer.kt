package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.BoxShadowData
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.utils.ToUtils
import com.digia.digiaui.framework.utils.drawCustomBorder
import com.digia.digiaui.framework.utils.drawCustomShadow
import com.digia.digiaui.framework.utils.toDp
import com.digia.digiaui.framework.utils.toPercentFraction

/** Container widget - uses commonProps for most styling, adds gradient/elevation/alignment */
class VWContainer(
        refName: String? = null,
        commonProps: CommonProps? = null,
        private val containerProps: ContainerProps,
        parent: VirtualNode? = null,
        slots: ((VirtualCompositeNode<ContainerProps>) -> Map<String, List<VirtualNode>>?)? = null,
        parentProps: Props? = null
) :
        VirtualCompositeNode<ContainerProps>(
                props = containerProps,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName,
                _slots = slots
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {

        /* ───────── Context ───────── */

        val context = LocalContext.current.applicationContext
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current

        /* ───────── Shape ───────── */

        val isCircle = containerProps.shape == "circle"
        val borderRadius = ToUtils.borderRadius(containerProps.borderRadius)
        val shape: Shape = if (isCircle) CircleShape else borderRadius

        /* ───────── Visual props ───────── */

        val gradient = containerProps.gradient?.toBrush(payload)
        val elevation = (containerProps.elevation ?: 0.0).dp
        val alignment = containerProps.childAlignment.toAlignment()
        val bgColor = payload.evalColor(containerProps.color)
        val borderWidth = (containerProps.border?.borderWidth ?: 0.0).dp

        /* ───────── Modifier base ───────── */

        var modifier: Modifier = Modifier;

        /* ==========================================================
         * 1️⃣ Margin (outer spacing – Compose has no real margin)
         * ========================================================== */
        modifier = modifier.padding(ToUtils.edgeInsets(containerProps.margin))

        /* ==========================================================
         * 1.5 Shadow & Elevation (Before background)
         * ========================================================== */

        // Custom Shadows
        val customShadows =
                containerProps.shadow?.map { shadowProp ->
                    BoxShadowData(
                            color = payload.evalColor(shadowProp.color) ?: Color.Black,
                            blurRadius = (shadowProp.blur ?: 0.0).dp,
                            spreadRadius = (shadowProp.spreadRadius ?: 0.0).dp,
                            offset =
                                    Offset(
                                            (shadowProp.offsetX ?: 0.0).toFloat(),
                                            (shadowProp.offsetY ?: 0.0).toFloat()
                                    )
                    )
                }
        modifier = modifier.drawCustomShadow(shape, customShadows)

        // Elevation
        if (elevation > 0.dp) {
            modifier = modifier.shadow(elevation = elevation, shape = shape, clip = false)
        }

        /* ==========================================================
         * 2️⃣ Size & Constraints (PURE FIX)
         * Order: fixed → min/max → fill (%)
         * ========================================================== */

        // Handle width with percentage
        containerProps.width?.let { widthValue ->
            val widthPercent = widthValue.toPercentFraction()
            if (widthPercent != null) {
                // Apply percentage width using fillMaxWidth with fraction
                modifier = modifier.fillMaxWidth(widthPercent)
            } else {
                // Apply fixed width
                widthValue.toDp()?.let { dpValue -> modifier = modifier.width(dpValue) }
            }
        }

        // Handle height with percentage
        containerProps.height?.let { heightValue ->
            val heightPercent = heightValue.toPercentFraction()
            if (heightPercent != null) {
                // Apply percentage height using fillMaxHeight with fraction
                modifier = modifier.fillMaxHeight(heightPercent)
            } else {
                // Apply fixed height
                heightValue.toDp()?.let { dpValue -> modifier = modifier.height(dpValue) }
            }
        }

        // Handle minimum width with percentage
        containerProps.minWidth?.let { minWidthValue ->
            val minWidthPercent = minWidthValue.toPercentFraction()
            if (minWidthPercent != null) {
                // Apply percentage minimum width
                modifier = modifier.fillMinWidth(minWidthPercent)
            } else {
                // Apply fixed minimum width
                minWidthValue.toDp()?.let { dpValue -> modifier = modifier.widthIn(min = dpValue) }
            }
        }

        // Handle minimum height with percentage
        containerProps.minHeight?.let { minHeightValue ->
            val minHeightPercent = minHeightValue.toPercentFraction()
            if (minHeightPercent != null) {
                // Apply percentage minimum height
                modifier = modifier.fillMinHeight(minHeightPercent)
            } else {
                // Apply fixed minimum height
                minHeightValue.toDp()?.let { dpValue ->
                    modifier = modifier.heightIn(min = dpValue)
                }
            }
        }

        // Handle maximum width with percentage
        containerProps.maxWidth?.let { maxWidthValue ->
            val maxWidthPercent = maxWidthValue.toPercentFraction()
            if (maxWidthPercent != null) {
                // Apply percentage maximum width (clamp to percentage)
                modifier = modifier.fillMaxWidth(maxWidthPercent)
            } else {
                // Apply fixed maximum width
                maxWidthValue.toDp()?.let { dpValue -> modifier = modifier.widthIn(max = dpValue) }
            }
        }

        // Handle maximum height with percentage
        containerProps.maxHeight?.let { maxHeightValue ->
            val maxHeightPercent = maxHeightValue.toPercentFraction()
            if (maxHeightPercent != null) {
                // Apply percentage maximum height (clamp to percentage)
                modifier = modifier.fillMaxHeight(maxHeightPercent)
            } else {
                // Apply fixed maximum height
                maxHeightValue.toDp()?.let { dpValue ->
                    modifier = modifier.heightIn(max = dpValue)
                }
            }
        }
        /* ==========================================================
         * 3️⃣ Background / Gradient
         * ========================================================== */
        modifier =
                when {
                    gradient != null -> modifier.background(gradient, shape)
                    bgColor != null -> modifier.background(bgColor, shape)
                    else -> modifier
                }

        /* ==========================================================
         * 4️⃣ Decoration Image
         * ========================================================== */
        containerProps.decorationImage?.let { decoration ->
            val src = decoration.source
            if (!src.isNullOrEmpty() && src.startsWith("http")) {
                val painter =
                        rememberAsyncImagePainter(
                                model =
                                        ImageRequest.Builder(LocalContext.current)
                                                .data(src)
                                                .crossfade(true)
                                                .build()
                        )

                modifier =
                        modifier.paint(
                                painter = painter,
                                contentScale = decoration.fit.toContentScale(),
                                alignment = decoration.alignment.toAlignment(),
                                alpha = (decoration.opacity ?: 1.0).toFloat()
                        )
            }
        }

        /* ==========================================================
         * 5️⃣ Border
         * ========================================================== */
        containerProps.border?.let { border ->
            val color = payload.evalColor(border.borderColor) ?: Color.Transparent
            val borderBrush = border.borderGradient?.toBrush(payload) ?: SolidColor(color)
            if (borderWidth > 0.dp) {
                modifier =
                        modifier.drawCustomBorder(
                                shape = shape,
                                borderWidth = borderWidth,
                                borderBrush = borderBrush,
                                borderPattern = border.borderPattern,
                                dashPattern = border.dashPattern,
                                strokeCap = border.strokeCap
                        )
            }
        }

        /* ==========================================================
         * 6️⃣ Clip (before clickable)
         * ========================================================== */
            modifier = modifier.clip(shape).buildModifier(payload)



        /* ==========================================================
         * 8️⃣ Padding (inner spacing)
         * ========================================================== */
        if (borderWidth > 0.dp) {
            modifier = modifier.padding(borderWidth)
        }
        modifier = modifier.padding(ToUtils.edgeInsets(containerProps.padding))

        /* ==========================================================
         * 9️⃣ Render
         * ========================================================== */
        Box(modifier = modifier, contentAlignment = alignment) { child?.ToWidget(payload) }
    }
}

// Helper extension functions for percentage constraints
private fun Modifier.fillMinWidth(fraction: Float): Modifier {
    return this.layout { measurable, constraints ->
        val minWidth = (constraints.maxWidth * fraction).toInt()
        val newConstraints = constraints.copy(minWidth = minWidth)
        val placeable = measurable.measure(newConstraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private fun Modifier.fillMinHeight(fraction: Float): Modifier {
    return this.layout { measurable, constraints ->
        val minHeight = (constraints.maxHeight * fraction).toInt()
        val newConstraints = constraints.copy(minHeight = minHeight)
        val placeable = measurable.measure(newConstraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

// ============== Props ==============

data class ContainerProps(
        val shape: String? = null,
        val elevation: Double? = null,
        val borderRadius: Any? = null,
        val childAlignment: String? = null,
        val gradient: GradientProps? = null,
        val shadow: List<ShadowProps>? = null,
        val decorationImage: DecorationImageProps? = null,
        val width: String? = null,
        val height: String? = null,
        val minWidth: String? = null,
        val maxWidth: String? = null,
        val minHeight: String? = null,
        val maxHeight: String? = null,
        val padding: Any? = null,
        val margin: Any? = null,
        val color: Any? = null,
        val border: BorderProps? = null,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): ContainerProps {
            return ContainerProps(
                    width = json["width"] as? String,
                    height = json["height"] as? String,
                    minWidth = json["minWidth"] as? String,
                    maxWidth = json["maxWidth"] as? String,
                    minHeight = json["minHeight"] as? String,
                    maxHeight = json["maxHeight"] as? String,
                    padding = json["padding"],
                    margin = json["margin"],
                    color = json["color"],
                    childAlignment = json["childAlignment"] as? String,
                    shape = json["shape"] as? String,
                    elevation = NumUtil.toDouble(json["elevation"]),
                    borderRadius = json["borderRadius"],
                    border = (json["border"] as? JsonLike)?.let { BorderProps.fromJson(it) },
                    gradient = (json["gradiant"] as? JsonLike)?.let { GradientProps.fromJson(it) },
                    shadow =
                            (json["shadow"] as? List<*>)?.mapNotNull { item ->
                                (item as? JsonLike)?.let { ShadowProps.fromJson(it) }
                            },
                    decorationImage =
                            (json["decorationImage"] as? JsonLike)?.let {
                                DecorationImageProps.fromJson(it)
                            },
            )
        }
    }
}

data class BorderProps(
        val borderWidth: Double? = null,
        val borderColor: Any? = null,
        val borderGradient: GradientProps? = null,
        val borderPattern: String? = null,
        val strokeCap: String? = null,
        val dashPattern: List<Double>? = null,
        val strokeAlign: String? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): BorderProps {
            val borderType = json["borderType"] as? JsonLike
            return BorderProps(
                    borderWidth = NumUtil.toDouble(json["borderWidth"]),
                    borderColor = json["borderColor"],
                    borderGradient =
                            (json["borderGradiant"] as? JsonLike)?.let {
                                GradientProps.fromJson(it)
                            },
                    borderPattern = borderType?.get("borderPattern") as? String,
                    strokeCap = borderType?.get("strokeCap") as? String,
                    dashPattern =
                            (borderType?.get("dashPattern") as? List<*>)?.mapNotNull {
                                NumUtil.toDouble(it)
                            },
                    strokeAlign = json["strokeAlign"] as? String
            )
        }
    }
}

data class GradientProps(
        val type: String? = null,
        val colorList: List<ColorStop>? = null,
        val begin: String? = null,
        val end: String? = null,
        val center: String? = null,
        val radius: Double? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): GradientProps {
            val colorListRaw = json["colorList"] as? List<*>
            val colorList =
                    colorListRaw?.mapNotNull { item ->
                        (item as? JsonLike)?.let { ColorStop.fromJson(it) }
                    }

            return GradientProps(
                    type = json["type"] as? String,
                    colorList = colorList,
                    begin = json["begin"] as? String,
                    end = json["end"] as? String,
                    center = json["center"] as? String,
                    radius = NumUtil.toDouble(json["radius"])
            )
        }
    }

    @Composable
    fun toBrush(payload: RenderPayload): Brush? {
        val colorStops = colorList ?: return null
        if (colorStops.isEmpty()) return null

        val colors = colorStops.mapNotNull { payload.evalColor(it.color) }
        if (colors.isEmpty()) return null

        val stops = colorStops.mapNotNull { it.stop?.toFloat() }
        val hasValidStops = stops.size == colors.size

        return object : androidx.compose.ui.graphics.ShaderBrush() {
            override fun createShader(
                    size: androidx.compose.ui.geometry.Size
            ): androidx.compose.ui.graphics.Shader {
                val width = size.width
                val height = size.height

                fun resolveOffset(alignment: String?): Offset {
                    return when (alignment) {
                        "topLeft", "topStart" -> Offset(0f, 0f)
                        "topCenter" -> Offset(width / 2, 0f)
                        "topRight", "topEnd" -> Offset(width, 0f)
                        "centerLeft", "centerStart" -> Offset(0f, height / 2)
                        "center" -> Offset(width / 2, height / 2)
                        "centerRight", "centerEnd" -> Offset(width, height / 2)
                        "bottomLeft", "bottomStart" -> Offset(0f, height)
                        "bottomCenter" -> Offset(width / 2, height)
                        "bottomRight", "bottomEnd" -> Offset(width, height)
                        else -> Offset(0f, 0f) // Default
                    }
                }

                return when (type) {
                    "linear" -> {
                        val start = resolveOffset(begin ?: "topCenter")
                        val end = resolveOffset(end ?: "bottomCenter")
                        androidx.compose.ui.graphics.LinearGradientShader(
                                from = start,
                                to = end,
                                colors = colors,
                                colorStops = if (hasValidStops) stops else null
                        )
                    }
                    "angular" -> {
                        val centerOffset = resolveOffset(center ?: "center")
                        val r = (radius ?: 0.5).toFloat()
                        // Flutter RadialGradient radius is fraction of shortest side (0.5 = 50% of
                        // shortest side)
                        val radiusPx = r * kotlin.math.min(width, height)

                        androidx.compose.ui.graphics.RadialGradientShader(
                                center = centerOffset,
                                radius = radiusPx,
                                colors = colors,
                                colorStops = if (hasValidStops) stops else null
                        )
                    }
                    else -> {
                        // Fallback or invalid
                        androidx.compose.ui.graphics.LinearGradientShader(
                                from = Offset.Zero,
                                to = Offset.Zero,
                                colors = colors
                        )
                    }
                }
            }
        }
    }
}

data class ColorStop(val color: Any? = null, val stop: Double? = null) {
    companion object {
        fun fromJson(json: JsonLike) =
                ColorStop(color = json["color"], stop = NumUtil.toDouble(json["stop"]))
    }
}

data class ShadowProps(
        val color: Any? = null,
        val blur: Double? = null,
        val spreadRadius: Double? = null,
        val offsetX: Double? = null,
        val offsetY: Double? = null,
        val blurStyle: String? = null
) {
    companion object {
        fun fromJson(json: JsonLike): ShadowProps {
            return ShadowProps(
                    color = json["color"],
                    blur = NumUtil.toDouble(json["blur"]),
                    spreadRadius = NumUtil.toDouble(json["spreadRadius"]),
                    offsetX = NumUtil.toDouble((json["offset"] as? JsonLike)?.get("x")),
                    offsetY = NumUtil.toDouble((json["offset"] as? JsonLike)?.get("y")),
                    blurStyle = json["blurStyle"] as? String
            )
        }
    }
}

data class DecorationImageProps(
        val source: String? = null,
        val sourceType: String? = null,
        val imageType: String? = null,
        val fit: String? = null,
        val alignment: String? = null,
        val opacity: Double? = null
) {
    companion object {
        fun fromJson(json: JsonLike): DecorationImageProps {
            return DecorationImageProps(
                    source = json["path"] as? String ?: json["source"] as? String,
                    sourceType = json["sourceType"] as? String,
                    imageType = json["imageType"] as? String,
                    fit = json["fit"] as? String,
                    alignment = json["alignment"] as? String,
                    opacity = NumUtil.toDouble(json["opacity"])?.let { it / 100.0 }
            )
        }
    }
}

// ============== Extensions ==============

private fun String?.toAlignment(): Alignment =
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
            else -> Alignment.TopStart
        }

private fun String?.toContentScale(): ContentScale =
        when (this) {
            "cover" -> ContentScale.Crop
            "fill" -> ContentScale.FillBounds
            "fitWidth" -> ContentScale.FillWidth
            "fitHeight" -> ContentScale.FillHeight
            "none" -> ContentScale.None
            "scaleDown" -> ContentScale.Inside
            "contain" -> ContentScale.Fit
            else -> ContentScale.Crop
        }

private fun String?.toGradientOffset(isEnd: Boolean = false): Offset =
        when (this) {
            "topLeft" -> Offset.Zero
            "topCenter" -> Offset(Float.POSITIVE_INFINITY, 0f) // Will be clamped to width/2, 0
            "topRight" -> Offset(Float.POSITIVE_INFINITY, 0f)
            "centerLeft" -> Offset(0f, Float.POSITIVE_INFINITY)
            "center" -> Offset(Float.POSITIVE_INFINITY / 2, Float.POSITIVE_INFINITY / 2)
            "centerRight" -> Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY / 2)
            "bottomLeft" -> Offset(0f, Float.POSITIVE_INFINITY)
            "bottomCenter" -> Offset(Float.POSITIVE_INFINITY / 2, Float.POSITIVE_INFINITY)
            "bottomRight" -> Offset.Infinite
            else -> if (isEnd) Offset.Infinite else Offset.Zero
        }

// ============== Builder ==============

fun containerBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {

    return VWContainer(
            refName = data.refName,
            commonProps = data.commonProps,
            containerProps = ContainerProps.fromJson(data.props.value),
            slots = { self -> registerAllChildern(data.childGroups, self, registry) },
            parent = parent,
            parentProps = data.parentProps
    )
}
