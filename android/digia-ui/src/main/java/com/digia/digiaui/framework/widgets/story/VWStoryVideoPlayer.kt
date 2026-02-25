package com.digia.digiaui.framework.widgets.story

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.story.LocalStoryVideoCallback

/**
 * Virtual Story Video Player widget. Mirrors Flutter's VWStoryVideoPlayer +
 * InternalStoryVideoPlayer
 *
 * Key behaviors:
 * - Creates ExoPlayer for video playback
 * - Notifies StoryPresenter via LocalStoryVideoCallback when ready
 * - Handles auto-play and looping
 * - Disposes player on unmount
 */
class VWStoryVideoPlayer(
        refName: String? = null,
        commonProps: CommonProps? = null,
        parent: VirtualNode? = null,
        parentProps: Props? = null,
        props: StoryVideoPlayerProps
) :
        VirtualLeafNode<StoryVideoPlayerProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {

        val videoUrl = payload.evalExpr(props.videoUrl)

        if (videoUrl.isNullOrBlank()) {

            Empty()
            return
        }

        val autoPlay = payload.evalExpr(props.autoPlay) ?: true
        val looping = payload.evalExpr(props.looping) ?: false
        val fit = payload.evalExpr(props.fit) ?: "cover"

        val context = LocalContext.current
        val onVideoLoad = LocalStoryVideoCallback.current

        var isInitialized by remember { mutableStateOf(false) }

        // Create ExoPlayer
        val exoPlayer =
                remember(videoUrl) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(videoUrl))
                        repeatMode = if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

                        prepare()
                    }
                }

        // Notify presenter that video is loading
        LaunchedEffect(videoUrl) {
            onVideoLoad?.invoke(null) // Signal loading
        }

        // Notify presenter when ready and handle auto-play
        LaunchedEffect(exoPlayer) {

            // Wait for player to be ready
            val listener =
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val stateName =
                                    when (playbackState) {
                                        Player.STATE_IDLE -> "IDLE"
                                        Player.STATE_BUFFERING -> "BUFFERING"
                                        Player.STATE_READY -> "READY"
                                        Player.STATE_ENDED -> "ENDED"
                                        else -> "UNKNOWN($playbackState)"
                                    }

                            if (playbackState == Player.STATE_READY && !isInitialized) {
                                isInitialized = true

                                onVideoLoad?.invoke(exoPlayer)
                                if (autoPlay) {

                                    exoPlayer.play()
                                }
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {}

                        override fun onIsPlayingChanged(isPlaying: Boolean) {}
                    }
            exoPlayer.addListener(listener)
        }

        // Cleanup
        DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

        // Build modifier
        var modifier = Modifier.buildModifier(payload)

        // Render video
        val resizeMode =
                when (fit.lowercase()) {
                    "cover" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    "contain" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }

        AndroidView(
                modifier = modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // No controls for story videos
                        setResizeMode(resizeMode)
                        layoutParams =
                                ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                )
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.setResizeMode(resizeMode)
                }
        )
    }
}

/** Builder function for StoryVideoPlayer widget registration. */
fun storyVideoPlayerBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {

    return VWStoryVideoPlayer(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = StoryVideoPlayerProps.fromJson(data.props.value)
    )
}
