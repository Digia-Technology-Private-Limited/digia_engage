package com.digia.digiaui.framework.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Efficiently draws a custom border (solid, dashed, dotted) using [drawWithCache]. This avoids
 * unnecessary object allocation during the draw phase.
 *
 * @param shape The shape of the border.
 * @param borderWidth The width of the border.
 * @param borderBrush The brush of the border.
 * @param borderPattern "solid", "dashed", or "dotted".
 * @param dashPattern Custom dash pattern [on, off, on, off...].
 * @param strokeCap "round", "square", "butt".
 * @param strokeJoin "miter", "round", "bevel".
 */
fun Modifier.drawCustomBorder(
        shape: Shape,
        borderWidth: Dp,
        borderBrush: Brush,
        borderPattern: String? = null,
        dashPattern: List<Double>? = null,
        strokeCap: String? = null,
        strokeJoin: String? = null
): Modifier =
        composed(
                inspectorInfo =
                        debugInspectorInfo {
                            name = "drawCustomBorder"
                            properties["shape"] = shape
                            properties["borderWidth"] = borderWidth
                            properties["borderBrush"] = borderBrush
                            properties["borderPattern"] = borderPattern
                            properties["dashPattern"] = dashPattern
                            properties["strokeCap"] = strokeCap
                            properties["strokeJoin"] = strokeJoin
                        }
        ) {
            if (borderWidth <= 0.dp) {
                return@composed this
            }

            this.drawWithCache {
                // Cache objects here, they only update if this block re-evaluates
                // (which happens if size changes or state reads change, but here depends on inputs)

                val widthPx = borderWidth.toPx()
                val halfWidthPx = widthPx / 2f

                // Resolve StrokeCap
                val cap =
                        when (strokeCap) {
                            "round" -> StrokeCap.Round
                            "square" -> StrokeCap.Square
                            else -> StrokeCap.Butt
                        }

                // Resolve StrokeJoin
                val join =
                        when (strokeJoin) {
                            "round" -> StrokeJoin.Round
                            "bevel" -> StrokeJoin.Bevel
                            else -> StrokeJoin.Miter
                        }

                // Resolve PathEffect
                val pathEffect: PathEffect? =
                        when (borderPattern) {
                            "dashed" -> {
                                val intervals =
                                        if (!dashPattern.isNullOrEmpty()) {
                                            dashPattern
                                                    .map { it.toFloat() * density }
                                                    .toFloatArray()
                                        } else {
                                            // Default dashed: 10dp on, 5dp off (classic dash)
                                            floatArrayOf(10.dp.toPx(), 5.dp.toPx())
                                        }
                                PathEffect.dashPathEffect(intervals, 0f)
                            }
                            "dotted" -> {
                                val intervals =
                                        if (!dashPattern.isNullOrEmpty()) {
                                            dashPattern
                                                    .map { it.toFloat() * density }
                                                    .toFloatArray()
                                        } else {
                                            // Default dotted: 1x width dot, 1x width space
                                            // To make them look like circles with StrokeCap.Round,
                                            // 'on' should be ~0 (just the cap) or very small, and
                                            // 'off' needs to be at least width * 2 for spacing?
                                            // Actually, for StrokeCap.Round, '0' length dash draws
                                            // a circle.
                                            // So we want [0.0, spacing]
                                            // Spacing usually width * 2 to leave 1 width gap
                                            // between centers.
                                            if (strokeCap == "round" || strokeCap == null) {
                                                // Dotted with rounds
                                                floatArrayOf(0f, widthPx * 2)
                                            } else {
                                                // Dotted with squares/butts (tiny rects)
                                                floatArrayOf(widthPx, widthPx)
                                            }
                                        }
                                // If using '0f' for 'on', we MUST ensure Cap is Round/Square,
                                // otherwise nothing draws for Butt.
                                // But let's stick to standard intervals if not 0.
                                PathEffect.dashPathEffect(intervals, 0f)
                            }
                            // Solid or unknown
                            else -> {
                                if (!dashPattern.isNullOrEmpty()) {
                                    // If user provided dashPattern but didn't treat as custom
                                    // dashed
                                    val intervals =
                                            dashPattern
                                                    .map { it.toFloat() * density }
                                                    .toFloatArray()
                                    PathEffect.dashPathEffect(intervals, 0f)
                                } else {
                                    null
                                }
                            }
                        }

                // Final Stroke style
                val stroke =
                        Stroke(
                                width = widthPx,
                                pathEffect = pathEffect,
                                cap =
                                        if (borderPattern == "dotted" && strokeCap == null)
                                                StrokeCap.Round
                                        else cap,
                                join = join
                        )

                // Create the outline
                // We do this to ensure we draw the border at the correct position
                // Modifier.border (and default Stroke) draws centered on the path.
                // If we want the border strictly *inside* the bounds, we could inset the path.
                // However, standard behavior matches the Outline.
                // Since we padded the content, the content is safe. We draw on the edge.

                onDrawWithContent {
                    drawContent()

                    // Draw the border on top
                    val outline = shape.createOutline(size, layoutDirection, this)
                    when (outline) {
                        is androidx.compose.ui.graphics.Outline.Rectangle -> {
                            drawRect(
                                    brush = borderBrush,
                                    topLeft = outline.rect.topLeft,
                                    size = outline.rect.size,
                                    style = stroke
                            )
                        }
                        is androidx.compose.ui.graphics.Outline.Rounded -> {
                            val path = Path().apply { addRoundRect(outline.roundRect) }
                            drawPath(path = path, brush = borderBrush, style = stroke)
                        }
                        is androidx.compose.ui.graphics.Outline.Generic -> {
                            drawPath(path = outline.path, brush = borderBrush, style = stroke)
                        }
                    }
                }
            }
        }
