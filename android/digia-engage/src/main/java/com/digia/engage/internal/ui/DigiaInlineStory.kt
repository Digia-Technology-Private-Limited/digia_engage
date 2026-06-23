package com.digia.engage.internal.ui

import android.graphics.Matrix
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.digia.engage.CEPTriggerPayload
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.model.InlineStoryConfig
import com.digia.engage.internal.model.StoryItemConfig

@Composable
internal fun DigiaInlineStory(
    config: InlineStoryConfig,
    payload: CEPTriggerPayload,
    modifier: Modifier = Modifier,
) {
    val cardHeight = config.card.height.dp
    val cardWidth = (config.card.height * config.card.aspectRatio).dp
    val cornerRadius = config.card.borderRadius.dp
    val spacing = config.card.spacing.dp

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(horizontal = spacing),
    ) {
        itemsIndexed(config.items) { index, item ->
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(cornerRadius))
                    .clickable {
                        // Ring/thumbnail tap opens the story → Digia Experience Clicked (story_open).
                        DigiaInstance.reportStoryOpened(payload)
                        DigiaInstance.controller.showStoryOverlay(config, index, payload)
                    },
            ) {
                when (item.type) {
                    "video" -> StoryThumbnailVideoPlayer(url = item.url)
                    else -> StoryThumbnailImage(url = item.url)
                }
            }
        }
    }
}

@Composable
private fun StoryThumbnailImage(url: String) {
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

@Composable
private fun StoryThumbnailVideoPlayer(url: String) {
    val context = LocalContext.current
    val textureView = remember { mutableStateOf<TextureView?>(null) }
    val videoSize = remember { mutableStateOf(VideoSize.UNKNOWN) }

    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) exoPlayer.play()
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize.value = size
                textureView.value?.centerCrop(size)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer, textureView.value) {
        val view = textureView.value
        view?.let(exoPlayer::setVideoTextureView)
        onDispose { view?.let(exoPlayer::clearVideoTextureView) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            // A TextureView composites inline; PlayerView's default SurfaceView lives in a
            // separate window layer where neighbouring videos in the row overlap each other.
            factory = { ctx ->
                TextureView(ctx).also { view ->
                    // The crop also has to re-run on layout: the video size can resolve before
                    // the view is measured, when its width/height are still zero.
                    view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        view.centerCrop(videoSize.value)
                    }
                    textureView.value = view
                }
            },
        )
    }
}

/** Scales the surface to fill the view while preserving the video's aspect ratio (center crop). */
private fun TextureView.centerCrop(videoSize: VideoSize) {
    if (videoSize.width == 0 || videoSize.height == 0 || width == 0 || height == 0) return
    val videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
    val viewAspect = width.toFloat() / height
    val matrix = Matrix()
    if (videoAspect > viewAspect) {
        matrix.setScale(videoAspect / viewAspect, 1f, width / 2f, height / 2f)
    } else {
        matrix.setScale(1f, viewAspect / videoAspect, width / 2f, height / 2f)
    }
    setTransform(matrix)
}
