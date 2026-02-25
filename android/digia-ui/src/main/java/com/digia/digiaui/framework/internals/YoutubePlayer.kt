package com.digia.digiaui.framework.internals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun InternalYoutubePlayer(
        videoUrl: String,
        isMuted: Boolean = false,
        loop: Boolean = false,
        autoPlay: Boolean = false,
        modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackPosition by rememberSaveable { mutableFloatStateOf(0f) }
    var playerInstance by remember { mutableStateOf<YouTubePlayer?>(null) }

    val videoId = remember(videoUrl) { extractVideoId(videoUrl) }

    DisposableEffect(Unit) { onDispose { playerInstance = null } }

    AndroidView(
            modifier = modifier,
            factory = { context ->
                YouTubePlayerView(context).apply {
                    lifecycleOwner.lifecycle.addObserver(this)
                    enableAutomaticInitialization = false

                    val options =
                            IFramePlayerOptions.Builder(context)
                                    .controls(1)
                                    .rel(0)
                                    .ivLoadPolicy(3)
                                    .ccLoadPolicy(0)
                                    .build()

                    initialize(
                            object : AbstractYouTubePlayerListener() {
                                override fun onReady(player: YouTubePlayer) {
                                    playerInstance = player

                                    if (videoId.isNotEmpty()) {
                                        if (autoPlay) {
                                            player.loadVideo(videoId, playbackPosition)
                                        } else {
                                            player.cueVideo(videoId, playbackPosition)
                                        }
                                    }

                                    if (isMuted) {
                                        player.mute()
                                    }
                                }

                                override fun onCurrentSecond(player: YouTubePlayer, second: Float) {
                                    playbackPosition = second
                                }

                                override fun onError(
                                        youTubePlayer: YouTubePlayer,
                                        error:
                                                com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError
                                ) {}

                                override fun onStateChange(
                                        youTubePlayer: YouTubePlayer,
                                        state:
                                                com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState
                                ) {
                                    if (loop &&
                                                    state ==
                                                            com.pierfrancescosoffritti
                                                                    .androidyoutubeplayer.core
                                                                    .player.PlayerConstants
                                                                    .PlayerState.ENDED
                                    ) {
                                        youTubePlayer.seekTo(0f)
                                        youTubePlayer.play()
                                    }
                                }
                            },
                            options
                    )
                }
            }
    )
}

private fun extractVideoId(input: String): String {
    if (input.isBlank()) return ""

    if (!input.contains("/") && input.length in 10..15) {
        return input
    }

    return try {
        val uri = input.toUri()

        when {
            uri.host?.contains("youtu.be") == true -> uri.lastPathSegment ?: ""
            uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1) ?: ""
            uri.path?.startsWith("/embed/") == true -> uri.pathSegments.getOrNull(1) ?: ""
            else -> uri.getQueryParameter("v") ?: ""
        }
    } catch (e: Exception) {
        ""
    }
}
