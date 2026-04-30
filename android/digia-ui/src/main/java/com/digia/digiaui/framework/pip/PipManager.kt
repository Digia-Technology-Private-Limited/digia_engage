package com.digia.digiaui.framework.pip

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.DefaultVirtualWidgetRegistry
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.utils.JsonLike
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─────────────────────────────────────────────────────────────────────────────
// Analytics interface — mirrors PlotlineEventsListener / Apxor event pattern
// ─────────────────────────────────────────────────────────────────────────────

interface PipEventListener {
    fun onEvent(eventName: String, properties: Map<String, Any?>)
}

object PipEvent {
    const val SHOWN         = "pip_shown"
    const val VIDEO_STARTED = "pip_video_started"
    const val VIDEO_FAILED  = "pip_video_failed"
    const val PLAY          = "pip_play_clicked"
    const val PAUSE         = "pip_pause_clicked"
    const val MUTE          = "pip_mute_clicked"
    const val UNMUTE        = "pip_unmute_clicked"
    const val EXPAND        = "pip_expand_clicked"
    const val COLLAPSE      = "pip_collapse_clicked"
    const val CLOSE         = "pip_close_clicked"
    const val DISMISSED     = "pip_dismissed"
}

// ─────────────────────────────────────────────────────────────────────────────
// Position preset — mirrors Apxor's vi_position: "br"|"bl"|"tr"|"tl"|"c"
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Named screen quadrant for the initial PiP position.
 *
 * Apxor uses "br"/"bl"/"tr"/"tl"/"c".
 * Plotline uses startX/startY fractions.
 * Digia supports both: set [position] for quadrant snapping or
 * leave it null and set [startX]/[startY] for exact fractional positioning.
 */
enum class PipPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER;

    /** Convert to [startX, startY] screen fractions (same unit as Plotline). */
    fun toFraction(pipWidthFraction: Float, pipHeightFraction: Float): Pair<Float, Float> =
        when (this) {
            TOP_LEFT     -> Pair(0.02f, 0.05f)
            TOP_RIGHT    -> Pair(1f - pipWidthFraction - 0.02f, 0.05f)
            BOTTOM_LEFT  -> Pair(0.02f, 1f - pipHeightFraction - 0.08f)
            BOTTOM_RIGHT -> Pair(1f - pipWidthFraction - 0.02f, 1f - pipHeightFraction - 0.08f)
            CENTER       -> Pair((1f - pipWidthFraction) / 2f, (1f - pipHeightFraction) / 2f)
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen filter — mirrors Apxor's restriction_type + screen list
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Controls on which screens the PiP should be visible.
 *
 * Apxor: restriction_type = "blacklist"|"whitelist" + screen_names list.
 * Plotline: flowStep.e = allowed pages list; dismiss fires in trackPage().
 *
 * [screenNames] are matched against the screen key passed to [PipManager.onScreenChanged].
 */
data class PipScreenFilter(
    val type: Type,
    val screenNames: Set<String>,
) {
    enum class Type { WHITELIST, BLACKLIST }

    fun isAllowed(currentScreen: String): Boolean = when (type) {
        Type.WHITELIST -> screenNames.isEmpty() || screenNames.contains(currentScreen)
        Type.BLACKLIST -> !screenNames.contains(currentScreen)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Drag bounds — neither Plotline nor Apxor support this; Digia differentiator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Restricts the draggable area of the PiP overlay to a sub-region of the screen.
 * All fractions are in [0, 1] relative to screen width/height.
 *
 * Example: maxYFraction = 0.85 keeps PiP above a bottom sheet at 85% screen height.
 */
data class PipDragBounds(
    val minXFraction: Float = 0f,
    val maxXFraction: Float = 1f,
    val minYFraction: Float = 0f,
    val maxYFraction: Float = 1f,
)

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

data class PipRequest(
    // Content
    val componentId: String = "",
    val args: JsonLike? = null,
    val videoUrl: String? = null,

    // Position — use [position] preset OR [startX]/[startY] fractions (Plotline style)
    val position: PipPosition? = null,       // Apxor vi_position equivalent
    val startX: Float = 0.7f,               // Plotline startX — ignored if position != null
    val startY: Float = 0.1f,               // Plotline startY — ignored if position != null

    // Size
    val widthDp: Float = 200f,
    val heightDp: Float = 120f,
    val cornerRadiusDp: Float = 12f,
    val backgroundColor: Color = Color.Black,

    // Controls
    val showClose: Boolean = true,
    val expandable: Boolean = true,          // Apxor can_minimize
    val autoPlay: Boolean = true,
    val looping: Boolean = false,
    val muted: Boolean = false,

    // Timing constraints — mirrors Apxor delay + auto_dismiss / Plotline delay + dismissAfter
    val delayMs: Long = 0L,                  // wait N ms before showing after show() is called
    val autoDismissMs: Long = 0L,            // 0 = never auto-dismiss

    // Screen constraints — mirrors Apxor restriction_type / Plotline allowedPages
    val screenFilter: PipScreenFilter? = null,
    val closeOnScreenChange: Boolean = false, // Apxor close_on_screen_change
    val closeOnNavigation: Boolean = false,   // Apxor close_on_navigation (Activity change)

    // Drag constraints — Digia-only; null = full screen draggable
    val dragBounds: PipDragBounds? = null,

    // Animation
    val animationDurationMs: Int = 300,       // Apxor pip_animation_duration (default 600)

    // Callbacks
    val onDismiss: ((Any?) -> Unit)? = null,
    val onEvent: PipEventListener? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Manager
// ─────────────────────────────────────────────────────────────────────────────

class PipManager {
    private val _currentRequest = MutableStateFlow<PipRequest?>(null)
    val currentRequest: StateFlow<PipRequest?> = _currentRequest.asStateFlow()

    fun show(
        componentId: String = "",
        args: JsonLike? = null,
        videoUrl: String? = null,
        position: PipPosition? = null,
        startX: Float = 0.7f,
        startY: Float = 0.1f,
        widthDp: Float = 200f,
        heightDp: Float = 120f,
        cornerRadiusDp: Float = 12f,
        backgroundColor: Color = Color.Black,
        showClose: Boolean = true,
        expandable: Boolean = true,
        autoPlay: Boolean = true,
        looping: Boolean = false,
        muted: Boolean = false,
        delayMs: Long = 0L,
        autoDismissMs: Long = 0L,
        screenFilter: PipScreenFilter? = null,
        closeOnScreenChange: Boolean = false,
        closeOnNavigation: Boolean = false,
        dragBounds: PipDragBounds? = null,
        animationDurationMs: Int = 300,
        onDismiss: ((Any?) -> Unit)? = null,
        onEvent: PipEventListener? = null,
    ) {
        _currentRequest.value = PipRequest(
            componentId         = componentId,
            args                = args,
            videoUrl            = videoUrl,
            position            = position,
            startX              = startX,
            startY              = startY,
            widthDp             = widthDp,
            heightDp            = heightDp,
            cornerRadiusDp      = cornerRadiusDp,
            backgroundColor     = backgroundColor,
            showClose           = showClose,
            expandable          = expandable,
            autoPlay            = autoPlay,
            looping             = looping,
            muted               = muted,
            delayMs             = delayMs,
            autoDismissMs       = autoDismissMs,
            screenFilter        = screenFilter,
            closeOnScreenChange = closeOnScreenChange,
            closeOnNavigation   = closeOnNavigation,
            dragBounds          = dragBounds,
            animationDurationMs = animationDurationMs,
            onDismiss           = onDismiss,
            onEvent             = onEvent,
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

    /**
     * Call this whenever the current screen changes — mirrors Plotline's trackPage()
     * and Apxor's ActivityChangeListener.onActivityChanged().
     *
     * Applies [PipRequest.screenFilter] and [PipRequest.closeOnScreenChange].
     */
    fun onScreenChanged(screenName: String) {
        val req = _currentRequest.value ?: return
        val shouldDismiss = when {
            req.closeOnScreenChange -> true
            req.screenFilter != null && !req.screenFilter.isAllowed(screenName) -> true
            else -> false
        }
        if (shouldDismiss) dismiss("screen_change")
    }

    /**
     * Call this on Activity resume / navigation — mirrors Apxor's close_on_navigation.
     * Only dismisses if [PipRequest.closeOnNavigation] is true.
     */
    fun onNavigated() {
        val req = _currentRequest.value ?: return
        if (req.closeOnNavigation) dismiss("navigation")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Host composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PipHost(
    pipManager: PipManager,
    registry: DefaultVirtualWidgetRegistry,
    resources: UIResources,
) {
    val request by pipManager.currentRequest.collectAsState()
    request?.let { req ->
        PipOverlay(
            request   = req,
            onDismiss = { pipManager.dismiss() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overlay — position / delay / auto-dismiss / drag / expand
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PipOverlay(
    request: PipRequest,
    onDismiss: () -> Unit,
) {
    val density       = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope         = rememberCoroutineScope()
    val activity      = LocalContext.current as? androidx.activity.ComponentActivity

    val screenWidthDp  = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenWidthPx  = with(density) { screenWidthDp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.toPx() }
    val pipWidthPx     = with(density) { request.widthDp.dp.toPx() }
    val pipHeightPx    = with(density) { request.heightDp.dp.toPx() }

    // Safe area insets — keep PiP within the visible area (below status bar, above nav bar)
    val rootInsets   = activity?.window?.decorView?.rootWindowInsets
    val safeTopPx    = (rootInsets?.systemWindowInsetTop    ?: 0).toFloat()
    val safeBottomPx = (rootInsets?.systemWindowInsetBottom ?: 0).toFloat()

    // Drag bound pixel values — derived from dragBounds fractions (null = full screen), clamped to safe area
    val minXPx = screenWidthPx  * (request.dragBounds?.minXFraction ?: 0f)
    val maxXPx = screenWidthPx  * (request.dragBounds?.maxXFraction ?: 1f)
    val minYPx = maxOf(screenHeightPx * (request.dragBounds?.minYFraction ?: 0f), safeTopPx)
    val maxYPx = minOf(screenHeightPx * (request.dragBounds?.maxYFraction ?: 1f), screenHeightPx - safeBottomPx)

    // Snap the current position to the nearest of the 4 allowed corners
    fun snapCorner(x: Float, y: Float): Pair<Float, Float> {
        val midX  = (minXPx + maxXPx) / 2f
        val midY  = (minYPx + maxYPx) / 2f
        val snapX = if (x + pipWidthPx  / 2f < midX) minXPx else (maxXPx - pipWidthPx).coerceAtLeast(minXPx)
        val snapY = if (y + pipHeightPx / 2f < midY) minYPx else (maxYPx - pipHeightPx).coerceAtLeast(minYPx)
        return Pair(snapX, snapY)
    }

    // Resolve initial position — preset takes priority over raw fractions (Apxor vi_position wins)
    val (resolvedStartX, resolvedStartY) = remember(request) {
        request.position?.toFraction(
            pipWidthFraction  = request.widthDp  / configuration.screenWidthDp,
            pipHeightFraction = request.heightDp / configuration.screenHeightDp,
        ) ?: Pair(request.startX, request.startY)
    }

    // Snap initial position to the nearest corner so it always lands on a corner
    val (initSnapX, initSnapY) = remember(resolvedStartX, resolvedStartY) {
        val rawX = (screenWidthPx * resolvedStartX).coerceIn(minXPx, (maxXPx - pipWidthPx).coerceAtLeast(minXPx))
        val rawY = (screenHeightPx * resolvedStartY).coerceIn(minYPx, (maxYPx - pipHeightPx).coerceAtLeast(minYPx))
        snapCorner(rawX, rawY)
    }

    // Animatable for X/Y — snapTo during drag (no lag), animateTo on release/expand
    val animX = remember { Animatable(initSnapX) }
    val animY = remember { Animatable(initSnapY) }

    // Remember where the pip was before expanding so we can restore on collapse
    var lastCollapsedX by remember { mutableStateOf(initSnapX) }
    var lastCollapsedY by remember { mutableStateOf(initSnapY) }

    var isExpanded by remember { mutableStateOf(false) }

    // Back press while expanded → collapse (don't navigate)
    BackHandler(enabled = isExpanded) {
        isExpanded = false
        request.onEvent?.onEvent(PipEvent.COLLAPSE, mapOf("source" to "back_press"))
    }

    // Always start hidden so AnimatedVisibility always plays the entry animation
    var isVisible by remember { mutableStateOf(false) }

    // Back press while collapsed → dismiss pip, then pop the screen
    BackHandler(enabled = isVisible && !isExpanded) {
        onDismiss()
        // Post so our BackHandler leaves composition before re-dispatching
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    // Animate X/Y when expand state changes
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            lastCollapsedX = animX.value
            lastCollapsedY = animY.value
            launch { animX.animateTo(0f, tween(request.animationDurationMs)) }
            launch { animY.animateTo(0f, tween(request.animationDurationMs)) }
        } else {
            launch { animX.animateTo(lastCollapsedX, tween(request.animationDurationMs)) }
            launch { animY.animateTo(lastCollapsedY, tween(request.animationDurationMs)) }
        }
    }

    // Delay before showing — mirrors Apxor delay / Plotline delay field
    LaunchedEffect(Unit) {
        if (request.delayMs > 0L) delay(request.delayMs)
        isVisible = true
        request.onEvent?.onEvent(
            PipEvent.SHOWN,
            mapOf(
                "pip_type"    to if (request.videoUrl != null) "video" else "component",
                "componentId" to request.componentId,
                "videoUrl"    to request.videoUrl,
                "position"    to (request.position?.name ?: "custom"),
            )
        )
    }

    // Auto-dismiss — mirrors Apxor auto_dismiss / Plotline dismissAfter
    LaunchedEffect(isVisible) {
        if (isVisible && request.autoDismissMs > 0L) {
            delay(request.autoDismissMs)
            request.onEvent?.onEvent(PipEvent.DISMISSED, mapOf("dismiss_type" to "auto_dismiss"))
            onDismiss()
        }
    }

    val animSpecDp = tween<Dp>(request.animationDurationMs)
    val currentWidth  by animateDpAsState(if (isExpanded) screenWidthDp  else request.widthDp.dp,  animSpecDp)
    val currentHeight by animateDpAsState(if (isExpanded) screenHeightDp else request.heightDp.dp, animSpecDp)
    val currentRadius by animateDpAsState(if (isExpanded) 0.dp else request.cornerRadiusDp.dp, animSpecDp)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter   = fadeIn(tween(request.animationDurationMs)) +
                      scaleIn(tween(request.animationDurationMs), initialScale = 0.85f),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                    .size(width = currentWidth, height = currentHeight)
                    .clip(RoundedCornerShape(currentRadius))
                    .background(request.backgroundColor)
                    .pointerInput(isExpanded) {
                        if (!isExpanded) {
                            detectDragGestures(
                                onDrag = { _, dragAmount ->
                                    // snapTo = instant update, no animation lag while dragging
                                    scope.launch {
                                        animX.snapTo(
                                            (animX.value + dragAmount.x)
                                                .coerceIn(minXPx, (maxXPx - pipWidthPx).coerceAtLeast(minXPx))
                                        )
                                        animY.snapTo(
                                            (animY.value + dragAmount.y)
                                                .coerceIn(minYPx, (maxYPx - pipHeightPx).coerceAtLeast(minYPx))
                                        )
                                    }
                                },
                                onDragEnd = {
                                    val (sx, sy) = snapCorner(animX.value, animY.value)
                                    lastCollapsedX = sx
                                    lastCollapsedY = sy
                                    scope.launch { animX.animateTo(sx, tween(request.animationDurationMs)) }
                                    scope.launch { animY.animateTo(sy, tween(request.animationDurationMs)) }
                                },
                                onDragCancel = {
                                    val (sx, sy) = snapCorner(animX.value, animY.value)
                                    lastCollapsedX = sx
                                    lastCollapsedY = sy
                                    scope.launch { animX.animateTo(sx, tween(request.animationDurationMs)) }
                                    scope.launch { animY.animateTo(sy, tween(request.animationDurationMs)) }
                                },
                            )
                        }
                    }
            ) {
                if (!request.videoUrl.isNullOrBlank()) {
                    PipVideoContent(
                        videoUrl       = request.videoUrl,
                        autoPlay       = request.autoPlay,
                        looping        = request.looping,
                        muted          = request.muted,
                        showClose      = request.showClose,
                        expandable     = request.expandable,
                        isExpanded     = isExpanded,
                        onToggleExpand = {
                            val next = !isExpanded
                            isExpanded = next
                            request.onEvent?.onEvent(
                                if (next) PipEvent.EXPAND else PipEvent.COLLAPSE,
                                mapOf("pip_type" to "video")
                            )
                        },
                        onDismiss = {
                            request.onEvent?.onEvent(PipEvent.CLOSE,    mapOf("dismiss_type" to "close_button"))
                            request.onEvent?.onEvent(PipEvent.DISMISSED, mapOf("dismiss_type" to "close_button"))
                            onDismiss()
                        },
                        onEvent = request.onEvent,
                    )
                } else {
                    DUIFactory.getInstance().CreateComponent(
                        componentId = request.componentId,
                        args        = request.args,
                    )
                }
            }
        } // AnimatedVisibility
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Video content — play/pause · mute/unmute · expand/collapse · close
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PipVideoContent(
    videoUrl: String,
    autoPlay: Boolean,
    looping: Boolean,
    muted: Boolean,
    showClose: Boolean,
    expandable: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    onEvent: PipEventListener?,
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode    = if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            volume        = if (muted) 0f else 1f
            playWhenReady = autoPlay
            prepare()
        }
    }

    var isPlaying   by remember { mutableStateOf(autoPlay) }
    var isMuted     by remember { mutableStateOf(muted) }
    var isBuffering by remember { mutableStateOf(true) }

    LaunchedEffect(autoPlay) {
        if (autoPlay) exoPlayer.play() else exoPlayer.pause()
        isPlaying = autoPlay
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING ||
                              playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                    onEvent?.onEvent(PipEvent.VIDEO_STARTED, mapOf("videoUrl" to videoUrl))
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isBuffering = false
                onEvent?.onEvent(PipEvent.VIDEO_FAILED, mapOf("error" to error.message))
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player        = exoPlayer
                    layoutParams  = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view -> view.player = exoPlayer },
        )

        if (isBuffering) {
            CircularProgressIndicator(
                modifier    = Modifier.align(Alignment.Center).size(36.dp),
                color       = Color.White,
                strokeWidth = 2.5.dp,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        ) {
            Row {
                IconButton(
                    onClick  = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                        onEvent?.onEvent(
                            if (isMuted) PipEvent.MUTE else PipEvent.UNMUTE,
                            mapOf("videoUrl" to videoUrl)
                        )
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector        = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint               = Color.White,
                        modifier           = Modifier.size(16.dp),
                    )
                }

                IconButton(
                    onClick  = {
                        isPlaying = !isPlaying
                        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                        onEvent?.onEvent(
                            if (isPlaying) PipEvent.PLAY else PipEvent.PAUSE,
                            mapOf("videoUrl" to videoUrl)
                        )
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint               = Color.White,
                        modifier           = Modifier.size(16.dp),
                    )
                }

                if (expandable) {
                    IconButton(
                        onClick  = onToggleExpand,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector        = if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint               = Color.White,
                            modifier           = Modifier.size(16.dp),
                        )
                    }
                }

                if (showClose) {
                    IconButton(
                        onClick  = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint               = Color.White,
                            modifier           = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
