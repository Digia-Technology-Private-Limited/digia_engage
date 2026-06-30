package com.digia.engage.internal.ui

import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.digia.engage.internal.buildVariableContext
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.StoryOverlayState
import com.digia.engage.internal.VariableContext
import com.digia.engage.internal.interpolate
import com.digia.engage.internal.model.StoryCtaAction
import com.digia.engage.internal.model.StoryItemConfig
import com.digia.engage.internal.story.LocalStoryVideoCallback
import com.digia.engage.internal.story.StoryIndicatorConfig
import com.digia.engage.internal.story.StoryPresenter
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun DigiaStoryOverlay() {
    val overlayState by DigiaInstance.controller.storyOverlay.collectAsState()
    val state = overlayState ?: return

    val itemTotal = state.config.items.size
    var currentStoryIndex by remember(state) { mutableIntStateOf(state.initialIndex) }
    var completed by remember(state) { mutableStateOf(false) }
    val openedAtMs = remember(state) { System.currentTimeMillis() }

    Dialog(
        onDismissRequest = {
            // User-closed (back / system) before the last frame → Step Dismissed.
            if (!completed) {
                DigiaInstance.reportStoryStepDismissed(state.payload, currentStoryIndex + 1)
            }
            DigiaInstance.controller.dismissStoryOverlay()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        DigiaStoryOverlayContent(
            state = state,
            currentStoryIndex = currentStoryIndex,
            onStoryIndexChanged = { currentStoryIndex = it },
            onCompleted = {
                completed = true
                DigiaInstance.reportStoryCompleted(
                    state.payload,
                    itemTotal = itemTotal,
                    timeToCompleteMs = System.currentTimeMillis() - openedAtMs,
                )
                DigiaInstance.controller.dismissStoryOverlay()
            },
        )
    }
}

@Composable
private fun DigiaStoryOverlayContent(
    state: StoryOverlayState,
    currentStoryIndex: Int,
    onStoryIndexChanged: (Int) -> Unit,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current
    // Build a VariableContext from campaign schemas (type info) + CEP runtime variables.
    val varContext = remember(state.variableSchemas, state.payload) {
        buildVariableContext(state.variableSchemas, state.payload.variables)
    }

    // Step Viewed fires for each frame that becomes visible (including the first).
    LaunchedEffect(currentStoryIndex) {
        DigiaInstance.reportStoryStepViewed(
            state.payload,
            itemIndex = currentStoryIndex + 1,
            itemTotal = state.config.items.size,
        )
    }

    val contents: List<@Composable () -> Unit> = remember(state.config) {
        state.config.items.map { item ->
            {
                FullScreenStoryItem(item = item)
            }
        }
    }

    val indicatorConfig = remember(state.config.indicator) {
        val ind = state.config.indicator
        StoryIndicatorConfig(
            activeColor = parseColor(ind.activeColor),
            completedColor = parseColor(ind.completedColor),
            disabledColor = parseColor(ind.disabledColor),
            height = ind.height.dp,
            borderRadius = ind.borderRadius.dp,
            horizontalGap = ind.horizontalGap.dp,
            topPadding = ind.topPadding.dp,
            horizontalPadding = ind.horizontalPadding.dp,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        StoryPresenter(
            contents = contents,
            controller = null,
            initialIndex = state.initialIndex,
            restartOnCompleted = state.config.restartOnCompleted,
            defaultDuration = state.config.defaultDuration.milliseconds,
            indicatorConfig = indicatorConfig,
            onCompleted = onCompleted,
            onStoryChanged = onStoryIndexChanged,
            footer = {
                val item = state.config.items.getOrNull(currentStoryIndex)
                if (item != null && item.ctaEnabled && !item.ctaText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        StoryCtaButton(
                            text = interpolate(item.ctaText ?: "", varContext),
                            textColor = parseColor(item.ctaTextColor),
                            backgroundColor = parseColor(item.ctaBackgroundColor),
                            cornerRadius = item.ctaCornerRadius,
                            onTap = {
                                // CTA inside a story frame tapped → Step Clicked.
                                DigiaInstance.reportStoryStepClicked(
                                    state.payload,
                                    itemIndex = currentStoryIndex + 1,
                                    ctaLabel = item.ctaText,
                                    actionType = item.ctaAction?.type,
                                    actionUrl = item.ctaAction?.url,
                                )
                                handleCtaAction(
                                    action = item.ctaAction,
                                    onDismiss = { DigiaInstance.controller.dismissStoryOverlay() },
                                    onOpenUrl = { url ->
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun StoryCtaButton(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    cornerRadius: Int,
    onTap: () -> Unit,
) {
    Button(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

private fun handleCtaAction(
    action: StoryCtaAction?,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    when (action?.type) {
        "deepLink", "openUrl" -> {
            action.url?.let { onOpenUrl(it) }
            onDismiss()
        }
        else -> onDismiss()
    }
}

@Composable
private fun FullScreenStoryItem(item: StoryItemConfig) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (item.type) {
            "video" -> FullScreenStoryVideo(url = item.url)
            else -> FullScreenStoryImage(url = item.url)
        }
    }
}

@Composable
private fun FullScreenStoryImage(url: String) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        // Letterbox (never crop): show the whole image with bars where aspect differs.
        contentScale = ContentScale.Fit,
        loading = {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        },
        error = {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A)))
        },
        success = { SubcomposeAsyncImageContent() },
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullScreenStoryVideo(url: String) {
    val context = LocalContext.current
    val onVideoLoad = LocalStoryVideoCallback.current
    var isInitialized by remember { mutableStateOf(false) }
    val textureView = remember { mutableStateOf<TextureView?>(null) }
    val videoSize = remember { mutableStateOf(VideoSize.UNKNOWN) }

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            playWhenReady = true
            prepare()
        }
    }

    LaunchedEffect(url) {
        onVideoLoad?.invoke(null)
    }

    DisposableEffect(exoPlayer) {
        // Notify the presenter once the player is ready so it can time the progress bar off the real
        // duration. Also check the current state in case READY was reached before the listener attached.
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !isInitialized) {
                    isInitialized = true
                    onVideoLoad?.invoke(exoPlayer)
                }
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize.value = size
                textureView.value?.fitCenter(size)
            }
        }
        exoPlayer.addListener(listener)
        if (exoPlayer.playbackState == Player.STATE_READY && !isInitialized) {
            isInitialized = true
            onVideoLoad?.invoke(exoPlayer)
        }
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(exoPlayer, textureView.value) {
        val view = textureView.value
        view?.let(exoPlayer::setVideoTextureView)
        onDispose { view?.let(exoPlayer::clearVideoTextureView) }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        // A TextureView composites inside the Dialog window; a SurfaceView/PlayerView punches through
        // to a separate layer and renders black here. Start hidden and reveal once the letterbox
        // transform is applied, so the first un-letterboxed (stretched) frame never flashes.
        factory = { ctx ->
            TextureView(ctx).apply {
                alpha = 0f
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    fitCenter(videoSize.value)
                }
                textureView.value = this
            }
        },
    )
}

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color.White)

/** Scales the surface to FIT inside the view, preserving aspect ratio (letterbox — never crops). */
private fun TextureView.fitCenter(videoSize: androidx.media3.common.VideoSize) {
    if (videoSize.width == 0 || videoSize.height == 0 || width == 0 || height == 0) return
    val videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
    val viewAspect = width.toFloat() / height
    val matrix = Matrix()
    if (videoAspect > viewAspect) {
        // Video wider than the view → fit to width, bars top & bottom.
        matrix.setScale(1f, viewAspect / videoAspect, width / 2f, height / 2f)
    } else {
        // Video taller than the view → fit to height, bars left & right.
        matrix.setScale(videoAspect / viewAspect, 1f, width / 2f, height / 2f)
    }
    setTransform(matrix)
    // Reveal only now that the surface is correctly letterboxed (see factory: starts at alpha 0).
    alpha = 1f
}
