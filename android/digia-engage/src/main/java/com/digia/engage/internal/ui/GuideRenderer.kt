package com.digia.engage.internal.ui

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.digia.engage.internal.ActiveGuideState
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.ScreenRect
import com.digia.engage.internal.interpolate
import com.digia.engage.internal.model.ActionType
import com.digia.engage.internal.model.GuideStepWidgetConfig
import kotlinx.coroutines.delay

// Ambient variable map for the active guide session.
// Provided at the GuideRenderer level; consumed by any composable that renders text.
internal val LocalDigiaVariables = compositionLocalOf<Map<String, String>?> { null }

/**
 * GuideRenderer — pure Compose, no external popup library.
 *
 * Uses Compose [Popup] which properly initialises its own composition context,
 * avoiding the "Cannot locate windowRecomposer" crash that occurred with
 * Balloon 1.6.x when used inside a React Native window.
 */
@Composable
internal fun GuideRenderer() {
    val state  by DigiaInstance.guideState.collectAsState()
    val anchorVersion by DigiaInstance.anchorVersion.collectAsState()

    if (state == null) return

    val guideState = state!!
    val step       = guideState.currentStep
    val config     = step.widgetConfig
    val anchorRect = DigiaInstance.findAnchor(step.anchorKey) ?: return

    // Analytics: the guide is on screen (anchor resolved). `Digia Experience
    // Viewed` fires once per guide; `Digia Step Viewed` fires per step.
    LaunchedEffect(guideState.campaign.campaignKey) { DigiaInstance.reportGuideViewed() }
    LaunchedEffect(guideState.campaign.campaignKey, guideState.stepIndex) {
        DigiaInstance.reportGuideStepViewed(guideState.stepIndex)
    }

    // Advancing past the final step is a completion; earlier steps just advance.
    val advanceOrComplete: () -> Unit = {
        if (guideState.hasNext) DigiaInstance.advanceGuide() else DigiaInstance.completeGuide()
    }

    // Auto-advance coroutine
    if (step.advanceTrigger == "auto" && step.autoDelayMs != null) {
        LaunchedEffect(guideState.stepIndex) {
            delay(step.autoDelayMs.toLong())
            advanceOrComplete()
        }
    }

    val onAdvance: () -> Unit = {
        // A CTA-driven advance is also a `Digia Step Clicked`.
        DigiaInstance.emitGuideStepClick(guideState.stepIndex)
        advanceOrComplete()
    }
    val onDismiss: () -> Unit = { DigiaInstance.dismissGuide() }

    CompositionLocalProvider(LocalDigiaVariables provides guideState.variables) {
        GuideTooltipOverlay(
            config     = config,
            guideState = guideState,
            anchorRect = anchorRect,
            onAdvance  = onAdvance,
            onDismiss  = onDismiss,
        )
    }
}

// ── Overlay + popup ───────────────────────────────────────────────────────────

@Composable
private fun GuideTooltipOverlay(
    config:     GuideStepWidgetConfig,
    guideState: ActiveGuideState,
    anchorRect: ScreenRect,
    onAdvance:  () -> Unit,
    onDismiss:  () -> Unit,
) {
    val overlay = config.overlay
    val bubble  = config.bubble
    val arrow   = bubble.arrow

    // ── Dark overlay (spotlight mode) ──────────────────────────────────────────
    if (overlay.visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(applyAlpha(overlay.color, overlay.alpha)))
                .clickable(enabled = overlay.dismissOnTap) { onDismiss() }
        )
    }

    // ── Tooltip popup ──────────────────────────────────────────────────────────
    // "top"  = arrow preferred at TOP of bubble  → bubble is BELOW the anchor
    // others = arrow preferred at BOTTOM         → bubble is ABOVE the anchor (default)
    val showBelow = (arrow.preferredDirection == "top")

    val arrowSizePx  = arrow.size
    val positionProvider = tooltipPositionProvider(
        anchorRect  = anchorRect,
        showBelow   = showBelow,
        arrowSizePx = arrowSizePx,
    )

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest      = onDismiss,
        properties            = PopupProperties(focusable = overlay.visible),
    ) {
        TooltipBubble(
            config     = config,
            guideState = guideState,
            showBelow  = showBelow,
            onAdvance  = onAdvance,
            onDismiss  = onDismiss,
        )
    }
}

/** Positions the popup above or below the anchor, horizontally centred and screen-clamped. */
private fun tooltipPositionProvider(
    anchorRect:  ScreenRect,
    showBelow:   Boolean,
    arrowSizePx: Int,
) = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds:    IntRect,
        windowSize:      IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val halfW  = popupContentSize.width / 2
        val anchorCx = anchorRect.x + anchorRect.width / 2

        val x = (anchorCx - halfW).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        val y = if (showBelow) {
            // arrow at top → popup bottom = anchor bottom edge (tip touches anchor bottom)
            anchorRect.y + anchorRect.height
        } else {
            // arrow at bottom → popup bottom = anchor top edge
            (anchorRect.y - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

// ── Bubble content ────────────────────────────────────────────────────────────

@Composable
private fun TooltipBubble(
    config:     GuideStepWidgetConfig,
    guideState: ActiveGuideState,
    showBelow:  Boolean,
    onAdvance:  () -> Unit,
    onDismiss:  () -> Unit,
) {
    val bubble = config.bubble
    val arrow  = bubble.arrow
    val shape  = RoundedCornerShape(bubble.cornerRadius.dp)
    val vars   = LocalDigiaVariables.current

    Column(modifier = Modifier.widthIn(max = bubble.maxWidthDp.dp)) {
        // ── Arrow above bubble (tooltip shown below anchor) ────────────────────
        if (arrow.visible && showBelow) {
            ArrowTriangle(
                color     = androidx.compose.ui.graphics.Color(arrow.color),
                pointUp   = true,
                sizePx    = arrow.size,
            )
        }

        // ── Main bubble card ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .shadow(bubble.elevation.dp, shape)
                .background(androidx.compose.ui.graphics.Color(bubble.backgroundColor), shape)
                .padding(
                    horizontal = bubble.paddingHorizontal.dp,
                    vertical   = bubble.paddingVertical.dp,
                ),
        ) {
            config.content.title?.takeIf { it.text.isNotBlank() }?.let { tc ->
                androidx.compose.material3.Text(
                    text       = interpolate(tc.text, vars),
                    fontSize   = tc.fontSize.sp,
                    color      = androidx.compose.ui.graphics.Color(tc.textColor),
                    fontWeight = FontWeight.Bold,
                )
            }

            config.content.body?.takeIf { it.text.isNotBlank() }?.let { tc ->
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text     = interpolate(tc.text, vars),
                    fontSize = tc.fontSize.sp,
                    color    = androidx.compose.ui.graphics.Color(tc.textColor),
                )
            }

            val steps = guideState.campaign.guideConfig?.steps?.size ?: 1
            if (config.content.stepIndicator.visible && steps > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text     = "${guideState.stepIndex + 1} / $steps",
                    fontSize = 12.sp,
                    color    = androidx.compose.ui.graphics.Color(config.content.stepIndicator.color),
                )
            }

            if (config.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    config.actions.forEach { action ->
                        TextButton(
                            onClick = {
                                when (action.actionType) {
                                    ActionType.DISMISS -> onAdvance()
                                    ActionType.NEXT    -> onAdvance()
                                    ActionType.PREV    -> {}
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor   = androidx.compose.ui.graphics.Color(action.textColor),
                                containerColor = androidx.compose.ui.graphics.Color(action.backgroundColor),
                            ),
                        ) {
                            androidx.compose.material3.Text(
                                text     = interpolate(action.label, vars),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        // ── Arrow below bubble (tooltip shown above anchor) ────────────────────
        if (arrow.visible && !showBelow) {
            ArrowTriangle(
                color   = androidx.compose.ui.graphics.Color(arrow.color),
                pointUp = false,
                sizePx  = arrow.size,
            )
        }
    }
}

/** Equilateral triangle arrow drawn with Compose Canvas. */
@Composable
private fun ArrowTriangle(
    color:   androidx.compose.ui.graphics.Color,
    pointUp: Boolean,
    sizePx:  Int,
) {
    val density = LocalDensity.current
    val sizeDp  = with(density) { sizePx.toDp() }

    androidx.compose.foundation.Canvas(modifier = Modifier.size(sizeDp * 2, sizeDp)) {
        val path = Path().apply {
            if (pointUp) {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path, color)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun applyAlpha(color: Int, alpha: Float): Int {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}
