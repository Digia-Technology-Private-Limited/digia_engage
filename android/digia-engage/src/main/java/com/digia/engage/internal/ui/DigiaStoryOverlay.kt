package com.digia.engage.internal.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.StoryOverlayState
import com.digia.engage.internal.extractVariables
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

    Dialog(
        onDismissRequest = { DigiaInstance.controller.dismissStoryOverlay() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        DigiaStoryOverlayContent(state = state)
    }
}

@Composable
private fun DigiaStoryOverlayContent(state: StoryOverlayState) {
    val context = LocalContext.current
    val variables = remember(state.payload) { extractVariables(state.payload.content) }
    var currentStoryIndex by remember(state.initialIndex) { mutableIntStateOf(state.initialIndex) }

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
            onCompleted = { DigiaInstance.controller.dismissStoryOverlay() },
            onStoryChanged = { index -> currentStoryIndex = index },
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
                            text = interpolate(item.ctaText ?: "", variables),
                            textColor = parseColor(item.ctaTextColor),
                            backgroundColor = parseColor(item.ctaBackgroundColor),
                            cornerRadius = item.ctaCornerRadius,
                            onTap = {
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
        contentScale = ContentScale.Crop,
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

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    LaunchedEffect(url) {
        onVideoLoad?.invoke(null)
    }

    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !isInitialized) {
                    isInitialized = true
                    onVideoLoad?.invoke(exoPlayer)
                    exoPlayer.play()
                }
            }
        }
        exoPlayer.addListener(listener)
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        },
        update = { view -> view.player = exoPlayer },
    )
}

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color.White)
