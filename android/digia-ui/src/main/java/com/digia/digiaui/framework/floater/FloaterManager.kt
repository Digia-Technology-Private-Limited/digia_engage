package com.digia.digiaui.framework.floater

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.DefaultVirtualWidgetRegistry
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.utils.JsonLike
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A draggable floating card request.
 *
 * Inline payload:
 * {
 *   "command": "SHOW_FLOATER",
 *   "viewId": "floater_card",
 *   "args": { "cta": "Claim offer" },
 *   "anchorX": 0.8,      // 0.0–1.0 fraction of screen width
 *   "anchorY": 0.5,      // 0.0–1.0 fraction of screen height
 *   "draggable": true
 * }
 */
data class FloaterRequest(
    val componentId: String,
    val args: JsonLike?,
    /** Fractional initial X position (0.0 = left, 1.0 = right) */
    val anchorX: Float = 0.8f,
    /** Fractional initial Y position (0.0 = top,  1.0 = bottom) */
    val anchorY: Float = 0.5f,
    val draggable: Boolean = true,
    val onDismiss: ((Any?) -> Unit)? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Manager
// ─────────────────────────────────────────────────────────────────────────────

class FloaterManager {
    private val _currentRequest = MutableStateFlow<FloaterRequest?>(null)
    val currentRequest: StateFlow<FloaterRequest?> = _currentRequest.asStateFlow()

    fun show(
        componentId: String,
        args: JsonLike? = null,
        anchorX: Float = 0.8f,
        anchorY: Float = 0.5f,
        draggable: Boolean = true,
        onDismiss: ((Any?) -> Unit)? = null,
    ) {
        _currentRequest.value = FloaterRequest(
            componentId = componentId,
            args        = args,
            anchorX     = anchorX,
            anchorY     = anchorY,
            draggable   = draggable,
            onDismiss   = onDismiss,
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
fun FloaterHost(
    floaterManager: FloaterManager,
    registry: DefaultVirtualWidgetRegistry,
    resources: UIResources,
) {
    val request by floaterManager.currentRequest.collectAsState()

    request?.let { req ->
        FloaterOverlay(
            request   = req,
            onDismiss = { floaterManager.dismiss() },
        )
    }
}

@Composable
private fun FloaterOverlay(
    request: FloaterRequest,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Initial position from anchor fractions (in px)
    var offsetX by remember(request.anchorX) {
        mutableStateOf((screenWidthPx * request.anchorX).coerceIn(0f, screenWidthPx))
    }
    var offsetY by remember(request.anchorY) {
        mutableStateOf((screenHeightPx * request.anchorY).coerceIn(0f, screenHeightPx))
    }
    // Track content size for clamping during drag
    var contentWidth  by remember { mutableStateOf(0) }
    var contentHeight by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                // offset moves both the visual position AND the hit-test region
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .then(
                    if (request.draggable) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                offsetX = (offsetX + dragAmount.x)
                                    .coerceIn(0f, (screenWidthPx - contentWidth).coerceAtLeast(0f))
                                offsetY = (offsetY + dragAmount.y)
                                    .coerceIn(0f, (screenHeightPx - contentHeight).coerceAtLeast(0f))
                            }
                        }
                    } else Modifier
                )
                .onSizeChanged { size ->
                    contentWidth  = size.width
                    contentHeight = size.height
                }
        ) {
            DUIFactory.getInstance().CreateComponent(
                componentId = request.componentId,
                args        = request.args,
            )
        }
    }
}

