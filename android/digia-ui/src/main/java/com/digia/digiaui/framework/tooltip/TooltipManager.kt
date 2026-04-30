package com.digia.digiaui.framework.tooltip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.DefaultVirtualWidgetRegistry
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.coachmark.CoachmarkLabelRegistry
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Position of the tooltip bubble relative to its target.
 */
enum class TooltipPosition { ABOVE, BELOW, LEFT, RIGHT, AUTO }

/**
 * A tooltip request.
 *
 * Inline payload:
 * {
 *   "command": "SHOW_TOOLTIP",
 *   "viewId": "tooltip_component",
 *   "placementKey": "discount_badge",
 *   "args": { "text": "20% OFF today!" },
 *   "position": "above",   // above | below | left | right | auto
 *   "arrowColor": "#FFFFFF" // hex color, should match DUI bubble background
 * }
 */
data class TooltipRequest(
    val componentId: String,
    val args: JsonLike?,
    val targetKey: String?,
    val position: TooltipPosition = TooltipPosition.AUTO,
    /** Hex color for the arrow/caret. Should match the DUI component's background color. */
    val arrowColorHex: String = "#FFFFFF",
    val onDismiss: ((Any?) -> Unit)? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Manager
// ─────────────────────────────────────────────────────────────────────────────

class TooltipManager {
    private val _currentRequest = MutableStateFlow<TooltipRequest?>(null)
    val currentRequest: StateFlow<TooltipRequest?> = _currentRequest.asStateFlow()

    fun show(
        componentId: String,
        args: JsonLike? = null,
        targetKey: String? = null,
        position: TooltipPosition = TooltipPosition.AUTO,
        arrowColorHex: String = "#FFFFFF",
        onDismiss: ((Any?) -> Unit)? = null,
    ) {
        _currentRequest.value = TooltipRequest(
            componentId   = componentId,
            args          = args,
            targetKey     = targetKey,
            position      = position,
            arrowColorHex = arrowColorHex,
            onDismiss     = onDismiss,
        )
    }

    fun dismiss(result: Any? = null) {
        val req = _currentRequest.value
        _currentRequest.value = null
        req?.onDismiss?.invoke(result)
    }

    fun clear() {
        _currentRequest.value = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Host composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TooltipHost(
    tooltipManager: TooltipManager,
    registry: DefaultVirtualWidgetRegistry,
    resources: UIResources,
) {
    val request by tooltipManager.currentRequest.collectAsState()

    request?.let { req ->
        val targetRect = req.targetKey?.let { CoachmarkLabelRegistry.getRect(it) }

        // Resolve position at composition time so the arrow direction is known for rendering
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }.roundToInt()
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.roundToInt()

        val resolvedPosition = remember(targetRect, req.position, screenWidthPx, screenHeightPx) {
            resolveTooltipSide(targetRect, req.position, screenWidthPx, screenHeightPx)
        }

        val arrowColor = remember(req.arrowColorHex) {
            runCatching { Color(android.graphics.Color.parseColor(req.arrowColorHex)) }
                .getOrDefault(Color.White)
        }

        val positionProvider = remember(targetRect, resolvedPosition) {
            TooltipPositionProvider(targetRect, resolvedPosition)
        }

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = { tooltipManager.dismiss() },
            properties = PopupProperties(focusable = false),
        ) {
            TooltipContentWithArrow(
                resolvedPosition = resolvedPosition,
                componentId      = req.componentId,
                args             = req.args,
                arrowColor       = arrowColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tooltip bubble + arrow caret
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps the DUI component with a directional arrow/caret pointing toward the target element.
 *
 * Caret placement by resolved position:
 *  ABOVE  → arrow at BOTTOM of bubble, pointing DOWN  (toward target below)
 *  BELOW  → arrow at TOP of bubble,    pointing UP    (toward target above)
 *  LEFT   → arrow at RIGHT of bubble,  pointing RIGHT (toward target on right)
 *  RIGHT  → arrow at LEFT of bubble,   pointing LEFT  (toward target on left)
 */
@Composable
private fun TooltipContentWithArrow(
    resolvedPosition: TooltipPosition,
    componentId: String,
    args: JsonLike?,
    arrowColor: Color,
    arrowWidth: Dp = 16.dp,
    arrowHeight: Dp = 9.dp,
) {
    when (resolvedPosition) {
        TooltipPosition.ABOVE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DUIFactory.getInstance().CreateComponent(componentId, args)
            TooltipArrow(direction = ArrowDirection.DOWN, width = arrowWidth, height = arrowHeight, color = arrowColor)
        }
        TooltipPosition.BELOW -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TooltipArrow(direction = ArrowDirection.UP, width = arrowWidth, height = arrowHeight, color = arrowColor)
            DUIFactory.getInstance().CreateComponent(componentId, args)
        }
        TooltipPosition.LEFT  -> Row(verticalAlignment = Alignment.CenterVertically) {
            DUIFactory.getInstance().CreateComponent(componentId, args)
            TooltipArrow(direction = ArrowDirection.RIGHT, width = arrowHeight, height = arrowWidth, color = arrowColor)
        }
        TooltipPosition.RIGHT -> Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipArrow(direction = ArrowDirection.LEFT, width = arrowHeight, height = arrowWidth, color = arrowColor)
            DUIFactory.getInstance().CreateComponent(componentId, args)
        }
        TooltipPosition.AUTO  -> DUIFactory.getInstance().CreateComponent(componentId, args)
    }
}

private enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }

@Composable
private fun TooltipArrow(
    direction: ArrowDirection,
    width: Dp,
    height: Dp,
    color: Color,
) {
    Canvas(modifier = Modifier.size(width = width, height = height)) {
        val path = Path()
        when (direction) {
            ArrowDirection.UP -> {
                // Triangle pointing up: apex at top-center
                path.moveTo(size.width / 2f, 0f)
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height)
            }
            ArrowDirection.DOWN -> {
                // Triangle pointing down: apex at bottom-center
                path.moveTo(0f, 0f)
                path.lineTo(size.width, 0f)
                path.lineTo(size.width / 2f, size.height)
            }
            ArrowDirection.LEFT -> {
                // Triangle pointing left: apex at left-center
                path.moveTo(0f, size.height / 2f)
                path.lineTo(size.width, 0f)
                path.lineTo(size.width, size.height)
            }
            ArrowDirection.RIGHT -> {
                // Triangle pointing right: apex at right-center
                path.moveTo(size.width, size.height / 2f)
                path.lineTo(0f, 0f)
                path.lineTo(0f, size.height)
            }
        }
        path.close()
        drawPath(path, color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Position resolution helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resolves [TooltipPosition.AUTO] by picking the side with the most available space
 * relative to [targetRect]. Returns a concrete non-AUTO position.
 */
private fun resolveTooltipSide(
    targetRect: IntRect?,
    position: TooltipPosition,
    screenWidthPx: Int,
    screenHeightPx: Int,
): TooltipPosition {
    if (position != TooltipPosition.AUTO) return position
    if (targetRect == null) return TooltipPosition.BELOW

    val spaceAbove = targetRect.top
    val spaceBelow = screenHeightPx - targetRect.bottom
    val spaceLeft  = targetRect.left
    val spaceRight = screenWidthPx - targetRect.right

    return maxOf(
        spaceAbove to TooltipPosition.ABOVE,
        spaceBelow to TooltipPosition.BELOW,
        spaceLeft  to TooltipPosition.LEFT,
        spaceRight to TooltipPosition.RIGHT,
        comparator = compareBy { it.first },
    ).second
}

// ─────────────────────────────────────────────────────────────────────────────
// Popup position provider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Positions the tooltip popup adjacent to [targetRect].
 * [resolvedPosition] must already be a concrete side (not AUTO).
 */
private class TooltipPositionProvider(
    private val targetRect: IntRect?,
    private val resolvedPosition: TooltipPosition,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val rect = targetRect ?: return IntOffset(
            x = (windowSize.width - popupContentSize.width) / 2,
            y = (windowSize.height - popupContentSize.height) / 2,
        )

        val targetCenterX = (rect.left + rect.right) / 2
        val targetCenterY = (rect.top + rect.bottom) / 2

        return when (resolvedPosition) {
            TooltipPosition.ABOVE  -> IntOffset(
                x = (targetCenterX - popupContentSize.width / 2).coerceIn(0, windowSize.width - popupContentSize.width),
                y = rect.top - popupContentSize.height,
            )
            TooltipPosition.BELOW  -> IntOffset(
                x = (targetCenterX - popupContentSize.width / 2).coerceIn(0, windowSize.width - popupContentSize.width),
                y = rect.bottom,
            )
            TooltipPosition.LEFT   -> IntOffset(
                x = rect.left - popupContentSize.width,
                y = (targetCenterY - popupContentSize.height / 2).coerceIn(0, windowSize.height - popupContentSize.height),
            )
            TooltipPosition.RIGHT  -> IntOffset(
                x = rect.right,
                y = (targetCenterY - popupContentSize.height / 2).coerceIn(0, windowSize.height - popupContentSize.height),
            )
            else -> IntOffset(
                x = (windowSize.width - popupContentSize.width) / 2,
                y = (windowSize.height - popupContentSize.height) / 2,
            )
        }
    }
}

internal fun String?.toTooltipPosition(): TooltipPosition = when (this?.trim()?.lowercase()) {
    "above"  -> TooltipPosition.ABOVE
    "below"  -> TooltipPosition.BELOW
    "left"   -> TooltipPosition.LEFT
    "right"  -> TooltipPosition.RIGHT
    else     -> TooltipPosition.AUTO
}
