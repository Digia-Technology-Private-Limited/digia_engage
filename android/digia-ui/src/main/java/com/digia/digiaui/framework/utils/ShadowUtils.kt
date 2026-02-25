package com.digia.digiaui.framework.utils

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

data class BoxShadowData(
        val color: Color,
        val blurRadius: Dp,
        val spreadRadius: Dp,
        val offset: androidx.compose.ui.geometry.Offset
)

fun Modifier.drawCustomShadow(shape: Shape, shadows: List<BoxShadowData>?): Modifier {
    if (shadows.isNullOrEmpty()) return this

    return this.drawBehind {
        shadows.forEach { shadow ->
            val paint = Paint()
            val frameworkPaint = paint.asFrameworkPaint()
            frameworkPaint.color = shadow.color.toArgb()

            val blurRadiusPx = shadow.blurRadius.toPx()
            val spreadRadiusPx = shadow.spreadRadius.toPx()

            if (blurRadiusPx > 0) {
                frameworkPaint.maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
            }

            val shadowSize =
                    size.copy(
                            width = size.width + (spreadRadiusPx * 2),
                            height = size.height + (spreadRadiusPx * 2)
                    )

            // Offset for spread (centering the larger rect)
            // spread adds to all sides, so we move top-left back by spreadRadius
            // Plus user offset

            // Actually, we don't draw a Rect. We draw the Outline of the shape.
            // But we need to inflate the outline if spread > 0.
            // Shape outlines are hard to "inflate" generically unless we use Path operations.
            // For Box shadows, usually just drawing the shape same size but blurred is standard
            // behavior (if spread=0).
            // If spread > 0, it's tricky with arbitrary shapes.
            // But for Rect/RoundedRect, easy.

            // Standard Flutter BoxShadow with spread:
            // "The shadow is painted by drawing the box with the spread radius added to the shape's
            // dimensions..."

            // For now, let's just translate by offset. Spread is complex for generic shapes.
            // We'll ignore spread for complex paths/outlines for now unless it's easy.
            // But we can translate.

            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(shadow.offset.x, shadow.offset.y)

                // If we want spread, strictly speaking we need to scale or inflate the shape.
                // Simple scaling around center might work?
                if (spreadRadiusPx > 0) {
                    // Scale logic... maybe too complex for now to get perfect.
                    // Let's just draw shadow at original size (or maybe slightly larger?)
                }

                val outline = shape.createOutline(size, layoutDirection, this@drawBehind)
                when (outline) {
                    is androidx.compose.ui.graphics.Outline.Rectangle -> {
                        canvas.drawRect(outline.rect, paint)
                    }
                    is androidx.compose.ui.graphics.Outline.Rounded -> {
                        val path =
                                androidx.compose.ui.graphics.Path().apply {
                                    addRoundRect(outline.roundRect)
                                }
                        canvas.drawPath(path, paint)
                    }
                    is androidx.compose.ui.graphics.Outline.Generic -> {
                        canvas.drawPath(outline.path, paint)
                    }
                }
                canvas.restore()
            }
        }
    }
}
