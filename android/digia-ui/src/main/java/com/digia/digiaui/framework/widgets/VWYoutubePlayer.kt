package com.digia.digiaui.framework.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.internals.InternalYoutubePlayer
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike

data class YoutubePlayerProps(
        val videoUrl: ExprOr<String>? = null,
        val isMuted: ExprOr<Boolean>? = null,
        val loop: ExprOr<Boolean>? = null,
        val autoPlay: ExprOr<Boolean>? = null,
) {
    companion object {
        fun fromJson(json: JsonLike): YoutubePlayerProps {
            return YoutubePlayerProps(
                    videoUrl = ExprOr.fromValue(json["videoUrl"]),
                    isMuted = ExprOr.fromValue(json["isMuted"]),
                    loop = ExprOr.fromValue(json["loop"]),
                    autoPlay = ExprOr.fromValue(json["autoPlay"]),
            )
        }
    }
}

class VWYoutubePlayer(
        refName: String? = null,
        commonProps: CommonProps? = null,
        parent: VirtualNode? = null,
        parentProps: Props? = null,
        props: YoutubePlayerProps,
) :
        VirtualLeafNode<YoutubePlayerProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps,
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val evaluated = payload.evalExpr(props.videoUrl) ?: ""
        val videoUrl = evaluated.trim()

        InternalYoutubePlayer(
                videoUrl = videoUrl,
                isMuted = payload.evalExpr(props.isMuted) ?: false,
                loop = payload.evalExpr(props.loop) ?: false,
                autoPlay = payload.evalExpr(props.autoPlay) ?: false,
                modifier = Modifier.buildModifier(payload),
        )
    }
}

fun youtubePlayerBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWYoutubePlayer(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = YoutubePlayerProps.fromJson(data.props.value),
    )
}
