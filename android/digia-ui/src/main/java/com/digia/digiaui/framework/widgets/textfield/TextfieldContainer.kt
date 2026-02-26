package com.digia.digiaui.framework.widgets.textfield

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.FocusedIndicatorThickness
import androidx.compose.material3.TextFieldDefaults.UnfocusedIndicatorThickness
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.widgets.VWInputBorder

@Composable
fun TextFieldContainer(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
        modifier: Modifier = Modifier,
        colors: DigiaTextFieldColors? = null,
        shape: Shape = TextFieldDefaults.shape,
        enabledBorder: VWInputBorder = VWInputBorder.None,
        disabledBorder: VWInputBorder = VWInputBorder.None,
        focusedBorder: VWInputBorder = VWInputBorder.None,
        focusedErrorBorder: VWInputBorder = VWInputBorder.None,
        errorBorder: VWInputBorder = VWInputBorder.None,
        focusedIndicatorLineThickness: Dp = 1.dp,
        unfocusedIndicatorLineThickness: Dp = 1.dp,
        cutoutModifier: Modifier = Modifier
) {
    val focused = interactionSource.collectIsFocusedAsState().value

    // Determine which border to use based on state
    val currentBorder =
            when {
                !enabled -> disabledBorder
                isError && focused -> focusedErrorBorder
                isError -> errorBorder
                focused -> focusedBorder
                else -> enabledBorder
            }

    // Animate container color
    val containerColor =
            animateColorAsState(
                    targetValue = colors?.containerColor(enabled, isError, focused)
                                    ?: Color.Transparent,
                    animationSpec = tween(durationMillis = 150),
            )

    // Get border color for animation
    val borderColor =
            when (currentBorder) {
                is VWInputBorder.Outline -> currentBorder.color
                is VWInputBorder.Underline -> currentBorder.color
                VWInputBorder.None -> Color.Transparent
            }

    val animatedBorderColor =
            animateColorAsState(
                    targetValue = borderColor,
                    animationSpec = tween(durationMillis = 150)
            )

    val finalShape = (currentBorder as? VWInputBorder.Outline)?.shape ?: shape

    Box(
            modifier =
                    modifier.textFieldBackground(containerColor::value, finalShape)
                            .then(cutoutModifier)
                            .then(
                                    when (currentBorder) {
                                        is VWInputBorder.Outline ->
                                                Modifier.outlineBorder(
                                                        border = currentBorder,
                                                        color = animatedBorderColor.value,
                                                        shape = finalShape
                                                )
                                        is VWInputBorder.Underline ->
                                                Modifier.underlineBorder(
                                                        border = currentBorder,
                                                        color = animatedBorderColor.value,
                                                        focused = focused,
                                                        focusedIndicatorLineThickness =
                                                                focusedIndicatorLineThickness,
                                                        unfocusedIndicatorLineThickness =
                                                                unfocusedIndicatorLineThickness
                                                )
                                        VWInputBorder.None -> Modifier
                                    }
                            )
    )
}

// Fixed outline border implementation
private fun Modifier.outlineBorder(
        border: VWInputBorder.Outline,
        color: Color,
        shape: Shape
): Modifier =
        this.then(
                if (border.strokeWidth.value > 0) {
                    Modifier.drawWithCache {
                        onDrawWithContent {
                            drawContent()

                            if (border.dashed && border.dashPattern != null) {
                                // Draw dashed outline using drawIntoCanvas
                                drawIntoCanvas { canvas ->
                                    // Create path based on shape
                                    val outline =
                                            shape.createOutline(
                                                    size = size,
                                                    layoutDirection = layoutDirection,
                                                    density = this
                                            )

                                    val path =
                                            when (outline) {
                                                is Outline.Rectangle -> {
                                                    Path().apply { addRect(outline.rect) }
                                                }
                                                is Outline.Rounded -> {
                                                    Path().apply { addRoundRect(outline.roundRect) }
                                                }
                                                is Outline.Generic -> outline.path
                                            }

                                    // Apply stroke width offset
                                    val halfStrokeWidth = border.strokeWidth.toPx() / 2
                                    val pathBounds = path.getBounds()
                                    val scaledPath =
                                            if (pathBounds.width > 0 && pathBounds.height > 0) {
                                                Path().apply {
                                                    op(path, Path(), PathOperation.Difference)
                                                }
                                            } else {
                                                path
                                            }

                                    val paint =
                                            android.graphics.Paint().apply {
                                                this.color = color.toArgb()
                                                style = android.graphics.Paint.Style.STROKE
                                                strokeWidth = border.strokeWidth.toPx()
                                                strokeCap =
                                                        when (border.strokeCap) {
                                                            androidx.compose.ui.graphics.StrokeCap
                                                                    .Butt ->
                                                                    android.graphics.Paint.Cap.BUTT
                                                            androidx.compose.ui.graphics.StrokeCap
                                                                    .Round ->
                                                                    android.graphics.Paint.Cap.ROUND
                                                            androidx.compose.ui.graphics.StrokeCap
                                                                    .Square ->
                                                                    android.graphics.Paint.Cap
                                                                            .SQUARE
                                                            else -> android.graphics.Paint.Cap.BUTT
                                                        }
                                                if (border.dashed && border.dashPattern != null) {
                                                    pathEffect =
                                                            android.graphics.DashPathEffect(
                                                                    border.dashPattern!!
                                                                            .toFloatArray(),
                                                                    0f
                                                            )
                                                }
                                            }

                                    canvas.nativeCanvas.drawPath(scaledPath.asAndroidPath(), paint)
                                }
                            } else {
                                // Draw solid outline using drawOutline
                                drawOutline(
                                        outline =
                                                shape.createOutline(
                                                        size = size,
                                                        layoutDirection = layoutDirection,
                                                        density = this
                                                ),
                                        color = color,
                                        style =
                                                Stroke(
                                                        width = border.strokeWidth.toPx(),
                                                        cap =
                                                                when (border.strokeCap) {
                                                                    androidx.compose.ui.graphics
                                                                            .StrokeCap.Butt ->
                                                                            androidx.compose.ui
                                                                                    .graphics
                                                                                    .StrokeCap.Butt
                                                                    androidx.compose.ui.graphics
                                                                            .StrokeCap.Round ->
                                                                            androidx.compose.ui
                                                                                    .graphics
                                                                                    .StrokeCap.Round
                                                                    androidx.compose.ui.graphics
                                                                            .StrokeCap.Square ->
                                                                            androidx.compose.ui
                                                                                    .graphics
                                                                                    .StrokeCap
                                                                                    .Square
                                                                    else ->
                                                                            androidx.compose.ui
                                                                                    .graphics
                                                                                    .StrokeCap.Butt
                                                                }
                                                )
                                )
                            }
                        }
                    }
                } else {
                    Modifier
                }
        )

// Fixed underline border implementation
private fun Modifier.underlineBorder(
        border: VWInputBorder.Underline,
        color: Color,
        focused: Boolean,
        focusedIndicatorLineThickness: Dp,
        unfocusedIndicatorLineThickness: Dp
): Modifier =
        this.then(
                Modifier.drawWithCache {
                    onDrawWithContent {
                        drawContent()

                        val lineThickness =
                                if (focused) {
                                    focusedIndicatorLineThickness.toPx()
                                } else {
                                    unfocusedIndicatorLineThickness.toPx()
                                }

                        var strokeWidth =
                                if (border.strokeWidth.value > 0) {
                                    border.strokeWidth.toPx()
                                } else {
                                    lineThickness
                                }

                        val y = size.height - strokeWidth / 2

                        if (border.dashed) {
                            // Draw dashed underline
                            drawIntoCanvas { canvas ->
                                val paint =
                                        android.graphics.Paint().apply {
                                            this.color = color.toArgb()
                                            style = android.graphics.Paint.Style.STROKE
                                            pathEffect =
                                                    android.graphics.DashPathEffect(
                                                            floatArrayOf(10f, 5f),
                                                            0f
                                                    )
                                        }

                                canvas.nativeCanvas.drawLine(0f, y, size.width, y, paint)
                            }
                        } else {
                            // Draw solid underline
                            drawLine(
                                    color = color,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = strokeWidth
                            )
                        }
                    }
                }
        )

private fun Modifier.textFieldBackground(color: () -> Color, shape: Shape): Modifier =
        this.then(Modifier.background(color = color(), shape = shape).clip(shape))

// Helper function to convert Compose Path to Android Path
private fun Path.asAndroidPath(): android.graphics.Path {
    return this.asAndroidPath()
}

// Simplified version without dashed borders (if you prefer)
private fun Modifier.outlineBorderSimple(
        border: VWInputBorder.Outline,
        color: Color,
        shape: Shape
): Modifier =
        this.then(
                if (border.strokeWidth.value > 0) {
                    Modifier.border(width = border.strokeWidth, color = color, shape = shape)
                } else {
                    Modifier
                }
        )

// Alternative: Use Compose's built-in border with rounded corners
private fun Modifier.customOutlineBorder(border: VWInputBorder.Outline, color: Color): Modifier =
        this.then(
                if (border.strokeWidth.value > 0) {
                    Modifier.border(width = border.strokeWidth, color = color, shape = border.shape)
                } else {
                    Modifier
                }
        )

// StrokeCap enum for compatibility
enum class StrokeCap {
    Butt,
    Round,
    Square
}

internal fun Modifier.textFieldBackground(
        color: ColorProducer,
        shape: Shape,
): Modifier =
        this.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawBehind { drawOutline(outline, color = color()) }
        }

fun Modifier.indicatorLine(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
        colors: DigiaTextFieldColors? = null,
        focusedIndicatorLineThickness: Dp = FocusedIndicatorThickness,
        unfocusedIndicatorLineThickness: Dp = UnfocusedIndicatorThickness
) =
        composed(
                inspectorInfo =
                        debugInspectorInfo {
                            name = "indicatorLine"
                            properties["enabled"] = enabled
                            properties["isError"] = isError
                            properties["interactionSource"] = interactionSource
                            properties["colors"] = colors
                            properties["focusedIndicatorLineThickness"] =
                                    focusedIndicatorLineThickness
                            properties["unfocusedIndicatorLineThickness"] =
                                    unfocusedIndicatorLineThickness
                        }
        ) {
            val focused = interactionSource.collectIsFocusedAsState().value
            val stroke =
                    animateBorderStrokeAsState(
                            enabled,
                            isError,
                            focused,
                            colors,
                            focusedIndicatorLineThickness,
                            unfocusedIndicatorLineThickness
                    )
            Modifier.drawIndicatorLine(stroke)
        }

internal fun Modifier.drawIndicatorLine(indicatorBorder: State<BorderStroke>): Modifier {
    return drawWithContent {
        drawContent()
        val strokeWidth = indicatorBorder.value.width.toPx()
        val y = size.height - strokeWidth / 2
        drawLine(indicatorBorder.value.brush, Offset(0f, y), Offset(size.width, y), strokeWidth)
    }
}

@Composable
internal fun animateBorderStrokeAsState(
        enabled: Boolean,
        isError: Boolean,
        focused: Boolean,
        colors: DigiaTextFieldColors?,
        focusedBorderThickness: Dp,
        unfocusedBorderThickness: Dp
): State<BorderStroke> {
    val targetColor = colors?.indicatorColor(enabled, isError, focused) ?: Color.Green
    val indicatorColor =
            if (enabled) {
                animateColorAsState(targetColor, tween(durationMillis = 150))
            } else {
                rememberUpdatedState(targetColor)
            }

    val thickness =
            if (enabled) {
                val targetThickness =
                        if (focused) focusedBorderThickness else unfocusedBorderThickness
                animateDpAsState(targetThickness, tween(durationMillis = 150))
            } else {
                rememberUpdatedState(unfocusedBorderThickness)
            }

    return rememberUpdatedState(BorderStroke(thickness.value, indicatorColor.value))
}
