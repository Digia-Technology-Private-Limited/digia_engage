package com.digia.digiaui.framework.widgets



import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.compose.PlayerSurface

//import androidx.media3.ui.PlayerView

data class VideoPlayerProps(
    val videoUrl: ExprOr<String>? = null,
    val showControls: ExprOr<Boolean>? = null,
    val aspectRatio: ExprOr<Double>? = null,
    val autoPlay: ExprOr<Boolean>? = null,
    val looping: ExprOr<Boolean>? = null,
) {
    companion object {
        fun fromJson(json: JsonLike): VideoPlayerProps {
            return VideoPlayerProps(
                videoUrl = ExprOr.fromValue(json["videoUrl"]),
                showControls = ExprOr.fromValue(json["showControls"]),
                aspectRatio = ExprOr.fromValue(json["aspectRatio"]),
                autoPlay = ExprOr.fromValue(json["autoPlay"]),
                looping = ExprOr.fromValue(json["looping"])
            )
        }
    }
}

class VWVideoPlayer(
    refName: String? = null,
    commonProps: CommonProps? = null,
    parent: VirtualNode? = null,
    parentProps: Props? = null,
    props: VideoPlayerProps
) : VirtualLeafNode<VideoPlayerProps>(
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

        val showControls = payload.evalExpr(props.showControls) ?: true
        val aspectRatio = payload.evalExpr(props.aspectRatio) ?: (16.0 / 9.0)
        val autoPlay = payload.evalExpr(props.autoPlay) ?: false
        val looping = payload.evalExpr(props.looping) ?: false

        val context = LocalContext.current

        val exoPlayer = remember(videoUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                repeatMode =
                    if (looping) Player.REPEAT_MODE_ALL
                    else Player.REPEAT_MODE_OFF
                prepare()
            }
        }

        // 🔑 CONTROL PLAYBACK EXPLICITLY
        LaunchedEffect(autoPlay) {
            if (autoPlay) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }

        var modifier = Modifier.buildModifier(payload)
        if (aspectRatio > 0) {
            modifier = modifier.aspectRatio(aspectRatio.toFloat())
        }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = showControls
                    player = exoPlayer
                }
            },
            update = { view ->
                view.useController = showControls
                view.player = exoPlayer
            }
        )
    }

}




fun videoPlayerBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {
    return VWVideoPlayer(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = VideoPlayerProps.fromJson(data.props.value)
    )
}