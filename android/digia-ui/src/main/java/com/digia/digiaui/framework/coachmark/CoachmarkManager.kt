package com.digia.digiaui.framework.coachmark

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.DefaultVirtualWidgetRegistry
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single coachmark step.
 *
 * JSON shape:
 * {
 *   "targetKey": "checkout_btn",   // optional — matches Modifier.coachmarkLabel("checkout_btn")
 *   "componentId": "step1_content", // DUI component rendered as the card
 *   "args": { … },                  // optional args passed to the component
 *   "spotlightPadding": 16,
 *   "spotlightRadius": 8
 * }
 */
data class CoachmarkStep(
    val targetKey: String?,
    val componentId: String,
    val args: JsonLike?,
    val spotlightPadding: Float = 16f,
    val spotlightRadius: Float = 8f,
) {
    companion object {
        fun fromJson(json: JsonLike): CoachmarkStep = CoachmarkStep(
            targetKey = json["targetKey"] as? String,
            componentId = json["componentId"] as? String ?: "",
            args = json["args"] as? JsonLike,
            spotlightPadding = (json["spotlightPadding"] as? Number)?.toFloat() ?: 16f,
            spotlightRadius = (json["spotlightRadius"] as? Number)?.toFloat() ?: 8f,
        )
    }
}

/**
 * Full coachmark request pushed by [ShowCoachmarkAction].
 *
 * JSON shape (the "data" block inside the action):
 * {
 *   "steps": [ { … }, { … } ],
 *   "dimColor": "#B4000000",
 *   "onFinish": { … }   // optional ActionFlow JSON
 * }
 */
data class CoachmarkRequest(
    val steps: List<CoachmarkStep>,
    val dimColor: Color = Color(0xB4000000),
    val onDismiss: ((Any?) -> Unit)? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Manager  (mirrors DialogManager / BottomSheetManager)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Manages coachmark display state.
 * Lives in [DigiaUIManager] as `coachmarkManager`.
 * Observed by [CoachmarkHost] in the composable tree.
 */
class CoachmarkManager {

    private val _currentRequest = MutableStateFlow<CoachmarkRequest?>(null)
    val currentRequest: StateFlow<CoachmarkRequest?> = _currentRequest.asStateFlow()

    private var _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    fun show(request: CoachmarkRequest) {
        _currentStep.value = 0
        _currentRequest.value = request
    }

    fun next() {
        val req = _currentRequest.value ?: return
        val next = _currentStep.value + 1
        if (next >= req.steps.size) {
            dismiss()
            req.onDismiss?.invoke(null)
        } else {
            _currentStep.value = next
        }
    }

    fun dismiss(result: Any? = null) {
        val req = _currentRequest.value
        _currentRequest.value = null
        _currentStep.value = 0
        req?.onDismiss?.invoke(result)
    }

    fun clear() {
        _currentRequest.value = null
        _currentStep.value = 0
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Label registry  (mirrors how DUI registers widget rects)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Global screen-rect registry, keyed by label string.
 * Composables register with [Modifier.coachmarkLabel].
 */
object CoachmarkLabelRegistry {
    internal val rects = mutableStateMapOf<String, IntRect>()

    fun getRect(key: String): IntRect? = rects[key]
    fun clear() = rects.clear()
}

/**
 * Tag a composable so [CoachmarkHost] can spotlight it.
 *
 * Usage:
 *   Button(modifier = Modifier.coachmarkLabel("checkout_btn")) { … }
 *
 * JSON:  "targetKey": "checkout_btn"
 */
fun Modifier.coachmarkLabel(key: String): Modifier = this.then(
    Modifier.onGloballyPositioned { coords ->
        val pos = coords.positionInWindow()
        CoachmarkLabelRegistry.rects[key] = IntRect(
            left  = pos.x.toInt(),
            top   = pos.y.toInt(),
            right = (pos.x + coords.size.width).toInt(),
            bottom = (pos.y + coords.size.height).toInt(),
        )
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Host composable  (mirrors DialogHost / BottomSheetHost)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Observe [CoachmarkManager] and render the coachmark overlay.
 *
 * Place alongside [DialogHost] and [BottomSheetHost] inside the root [Box] in
 * [DigiaUIApp] and [DigiaComposableApi].
 */
@Composable
fun CoachmarkHost(
    coachmarkManager: CoachmarkManager,
    registry: DefaultVirtualWidgetRegistry,
    resources: UIResources,
) {
    val request by coachmarkManager.currentRequest.collectAsState()
    val stepIndex by coachmarkManager.currentStep.collectAsState()

    request?.let { req ->
        val step = req.steps.getOrNull(stepIndex) ?: return@let

        CoachmarkDialog(
            request = req,
            step = step,
            stepIndex = stepIndex,
            totalSteps = req.steps.size,
            onNext  = { coachmarkManager.next() },
            onDismiss = { coachmarkManager.dismiss() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Coachmark dialog (full-screen dim + spotlight + DUI content card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CoachmarkDialog(
    request: CoachmarkRequest,
    step: CoachmarkStep,
    stepIndex: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Resolve the spotlight rect (null = no spotlight, full-dim step)
        val spotRect: IntRect? = step.targetKey?.let { CoachmarkLabelRegistry.getRect(it) }

        // Animate spotlight smoothly between steps
        val animRect = remember { Animatable(AnimatedRect.ZERO, AnimatedRect.Converter) }
        LaunchedEffect(spotRect) {
            val target = spotRect?.toAnimatedRect() ?: AnimatedRect.ZERO
            animRect.animateTo(target, tween(280))
        }

        val density = LocalDensity.current
        val paddingPx  = with(density) { step.spotlightPadding.dp.toPx() }
        val cornerPx   = with(density) { step.spotlightRadius.dp.toPx() }

        Box(Modifier.fillMaxSize()) {

            // ── 1. Dim overlay with transparent spotlight punch-through ────
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                // Full-screen dim
                drawRect(request.dimColor)

                // Punch hole (BlendMode.Clear ≡ PorterDuff.CLEAR)
                val r = animRect.value
                if (r != AnimatedRect.ZERO) {
                    val left   = r.left   - paddingPx
                    val top    = r.top    - paddingPx
                    val right  = r.right  + paddingPx
                    val bottom = r.bottom + paddingPx
                    if (cornerPx > 0f) {
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            cornerRadius = CornerRadius(cornerPx),
                            blendMode = BlendMode.Clear,
                        )
                    } else {
                        drawRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            blendMode = BlendMode.Clear,
                        )
                    }
                }
            }

            // ── 2. Content card — DUI component rendered from componentId ──
            CoachmarkCard(
                step = step,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                spotRect = spotRect,
                onNext = onNext,
                onDismiss = onDismiss,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.CoachmarkCard(
    step: CoachmarkStep,
    stepIndex: Int,
    totalSteps: Int,
    spotRect: IntRect?,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Position below spotlight if it fits, else above, else center
    val screenH = LocalConfiguration.current.screenHeightDp

    val alignment = when {
        spotRect == null -> Alignment.Center
        spotRect.top < screenH / 2 -> Alignment.BottomCenter  // target in upper half → card below
        else -> Alignment.TopCenter                             // target in lower half → card above
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = alignment,
    ) {
        // Render the DUI component specified by componentId in the step
        // This is the same pattern DialogHost uses — server-driven card content
        DUIFactory.getInstance().CreateComponent(
            componentId = step.componentId,
            args = (step.args ?: emptyMap<String, Any>()) + mapOf(
                // Inject navigation callbacks so the card can call next/dismiss
                "__coachmark_stepIndex" to stepIndex,
                "__coachmark_totalSteps" to totalSteps,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Animation helper
// ─────────────────────────────────────────────────────────────────────────────

private data class AnimatedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val ZERO = AnimatedRect(0f, 0f, 0f, 0f)

        val Converter: TwoWayConverter<AnimatedRect, AnimationVector4D> =
            TwoWayConverter(
                convertToVector = { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
                convertFromVector = { AnimatedRect(it.v1, it.v2, it.v3, it.v4) },
            )
    }
}

private fun IntRect.toAnimatedRect() =
    AnimatedRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
