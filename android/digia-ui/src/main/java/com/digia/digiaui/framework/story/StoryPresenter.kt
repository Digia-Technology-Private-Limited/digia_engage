package com.digia.digiaui.framework.story

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.ExoPlayer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Main Story presenter composable. Mirrors Flutter's FlutterStoryPresenterWidgets from
 * flutter_story_widgets.dart
 *
 * Responsibilities:
 * - Manages current story index and navigation
 * - Coordinates with video players via LocalStoryVideoCallback
 * - Animates progress indicator based on video duration or default duration
 * - Handles tap gestures for navigation and long press for pause
 * - Handles slide gestures for pause/resume
 */
@Composable
fun StoryPresenter(
        contents: List<@Composable () -> Unit>,
        controller: StoryController?,
        initialIndex: Int = 0,
        restartOnCompleted: Boolean = false,
        defaultDuration: Duration = 3000.milliseconds,
        indicatorConfig: StoryIndicatorConfig = StoryIndicatorConfig(),
        header: (@Composable () -> Unit)? = null,
        footer: (@Composable () -> Unit)? = null,
        onCompleted: (() -> Unit)? = null,
        onStoryChanged: ((Int) -> Unit)? = null,
        onPreviousCompleted: (() -> Unit)? = null,
        // Navigation callbacks
        onSlideDown: ((Offset) -> Unit)? = null,
        onSlideStart: ((Offset) -> Unit)? = null,
        onLeftTap: (suspend () -> Boolean)? = null,
        onRightTap: (suspend () -> Boolean)? = null,
        modifier: Modifier = Modifier
) {
    if (contents.isEmpty()) return

    val scope = rememberCoroutineScope()

    // State
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, contents.size - 1)) }
    var isPaused by remember { mutableStateOf(false) }
    var currentVideoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isWaitingForVideo by remember { mutableStateOf(false) }

    // Animation
    val progressAnimation = remember { Animatable(0f) }
    var animationJob by remember { mutableStateOf<Job?>(null) }

    // Functions to control animation
    fun startCountdown(duration: Duration) {

        animationJob?.cancel()
        animationJob =
                scope.launch {
                    progressAnimation.snapTo(0f)
                    progressAnimation.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                    tween(
                                            durationMillis = duration.inWholeMilliseconds.toInt(),
                                            easing = LinearEasing
                                    )
                    )
                    // Animation completed - go to next

                    if (currentIndex < contents.size - 1) {
                        currentIndex++
                        onStoryChanged?.invoke(currentIndex)
                    } else {
                        onCompleted?.invoke()
                        if (restartOnCompleted) {
                            currentIndex = 0
                            onStoryChanged?.invoke(currentIndex)
                        }
                    }
                }
    }

    fun pauseAnimation() {

        animationJob?.cancel()
        currentVideoPlayer?.pause()
    }

    fun resumeAnimation() {

        currentVideoPlayer?.play()
        // Resume from current progress
        val remainingProgress = 1f - progressAnimation.value
        if (remainingProgress > 0 && currentVideoPlayer != null) {
            val remainingDuration = (currentVideoPlayer!!.duration * remainingProgress).toLong()
            animationJob =
                    scope.launch {
                        progressAnimation.animateTo(
                                targetValue = 1f,
                                animationSpec =
                                        tween(
                                                durationMillis = remainingDuration.toInt(),
                                                easing = LinearEasing
                                        )
                        )
                    }
        }
    }

    fun goToNext() {

        animationJob?.cancel()
        currentVideoPlayer?.stop()
        currentVideoPlayer = null
        isWaitingForVideo = false

        if (currentIndex < contents.size - 1) {
            currentIndex++
            onStoryChanged?.invoke(currentIndex)
        } else {
            onCompleted?.invoke()
            if (restartOnCompleted) {
                currentIndex = 0
                onStoryChanged?.invoke(currentIndex)
            }
        }
    }

    fun goToPrevious() {

        animationJob?.cancel()
        currentVideoPlayer?.stop()
        currentVideoPlayer = null
        isWaitingForVideo = false

        if (currentIndex > 0) {
            currentIndex--
            onStoryChanged?.invoke(currentIndex)
        } else {
            // Reset current story
            scope.launch { progressAnimation.snapTo(0f) }
            onPreviousCompleted?.invoke()
        }
    }

    // Video callback - keyed to currentIndex to ensure fresh callback per story
    val onVideoLoad: OnVideoLoad =
            remember(currentIndex) {
                { player: ExoPlayer? ->
                    if (player == null) {
                        // Video is loading
                        isWaitingForVideo = true
                        currentVideoPlayer = null
                        animationJob?.cancel()
                        scope.launch { progressAnimation.snapTo(0f) }
                    } else {
                        // Video is ready
                        currentVideoPlayer = player
                        isWaitingForVideo = false
                        val duration = player.duration.milliseconds

                        startCountdown(duration)
                    }
                }
            }

    // Handle index changes - reset state for new story
    LaunchedEffect(currentIndex) {
        animationJob?.cancel()
        progressAnimation.snapTo(0f)
        currentVideoPlayer = null
        isWaitingForVideo = true // Assume video until proven otherwise

        // Wait a bit for video to register, then fallback to default duration
        kotlinx.coroutines.delay(500)

        if (isWaitingForVideo && currentVideoPlayer == null) {

            isWaitingForVideo = false
            startCountdown(defaultDuration)
        }
    }

    // Handle controller actions
    LaunchedEffect(controller?.storyAction) {
        when (controller?.storyAction) {
            StoryAction.PAUSE -> {
                isPaused = true
                pauseAnimation()
            }
            StoryAction.PLAY -> {
                if (isPaused) {
                    isPaused = false
                    resumeAnimation()
                }
            }
            StoryAction.NEXT -> {
                goToNext()
                controller.resetAction()
            }
            StoryAction.PREVIOUS -> {
                goToPrevious()
                controller.resetAction()
            }
            StoryAction.MUTE -> {
                currentVideoPlayer?.volume = 0f
            }
            StoryAction.UNMUTE -> {
                currentVideoPlayer?.volume = 1f
            }
            null -> {}
        }
    }

    // Handle jump index
    LaunchedEffect(controller?.jumpIndex) {
        controller?.jumpIndex?.let { index ->
            if (index in 0 until contents.size) {
                currentIndex = index
                currentVideoPlayer = null
                onStoryChanged?.invoke(currentIndex)
            }
            controller.clearJumpIndex()
        }
    }

    // Lifecycle handling
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> pauseAnimation()
                Lifecycle.Event.ON_RESUME -> if (!isPaused) resumeAnimation()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // UI
    CompositionLocalProvider(LocalStoryVideoCallback provides onVideoLoad) {
        Box(modifier = modifier.fillMaxSize()) {
            // Current story content - KEY BY INDEX to force recomposition
            key(currentIndex) { contents[currentIndex]() }

            // Progress indicator
            StoryIndicator(
                    currentIndex = currentIndex,
                    progress = progressAnimation.value,
                    totalItems = contents.size,
                    config = indicatorConfig,
                    modifier = Modifier.align(Alignment.TopCenter)
            )

            // Navigation overlay - LEFT (40%) | CENTER (20% long press + slide) | RIGHT (40%)
            NavigationOverlay(
                    onLeftTap = {
                        scope.launch {
                            val shouldPlay = onLeftTap?.invoke() ?: true
                            if (shouldPlay) goToPrevious()
                        }
                    },
                    onRightTap = {
                        scope.launch {
                            val shouldPlay = onRightTap?.invoke() ?: true
                            if (shouldPlay) goToNext()
                        }
                    },
                    onLongPressStart = {
                        isPaused = true
                        pauseAnimation()
                    },
                    onLongPressEnd = {
                        isPaused = false
                        resumeAnimation()
                    },
                    onSlideStart = onSlideStart,
                    onSlideDown = { offset ->
                        // Pause on slide down, this is the default behavior
                        if (!isPaused) {
                            isPaused = true
                            pauseAnimation()
                        }
                        onSlideDown?.invoke(offset)
                    }
            )

            // Header
            header?.let { headerContent ->
                Box(modifier = Modifier.align(Alignment.TopCenter)) { headerContent() }
            }

            // Footer
            footer?.let { footerContent ->
                Box(modifier = Modifier.align(Alignment.BottomCenter)) { footerContent() }
            }
        }
    }
}

/**
 * Navigation overlay using Row layout to avoid z-order issues. LEFT (40% tap) | CENTER (20% long
 * press + slide) | RIGHT (40% tap)
 */
@Composable
private fun NavigationOverlay(
        onLeftTap: () -> Unit,
        onRightTap: () -> Unit,
        onLongPressStart: () -> Unit,
        onLongPressEnd: () -> Unit,
        onSlideStart: ((Offset) -> Unit)? = null,
        onSlideDown: ((Offset) -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left tap area (40%)
        Box(
                modifier =
                        Modifier.weight(0.4f).fillMaxHeight().pointerInput(Unit) {
                            detectTapGestures(onTap = { onLeftTap() })
                        }
        )

        // Center area for long press and slide (20%)
        Box(
                modifier =
                        Modifier.weight(0.2f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectTapGestures(onLongPress = { onLongPressStart() })
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                            onDragStart = { offset ->
                                                onSlideStart?.invoke(offset)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                // Only trigger slide down for downward movement
                                                if (dragAmount.y > 0) {

                                                    onSlideDown?.invoke(
                                                            Offset(dragAmount.x, dragAmount.y)
                                                    )
                                                }
                                            }
                                    )
                                }
        )

        // Right tap area (40%)
        Box(
                modifier =
                        Modifier.weight(0.4f).fillMaxHeight().pointerInput(Unit) {
                            detectTapGestures(onTap = { onRightTap() })
                        }
        )
    }
}
