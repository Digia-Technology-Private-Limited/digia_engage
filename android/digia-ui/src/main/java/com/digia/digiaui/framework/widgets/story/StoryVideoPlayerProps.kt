package com.digia.digiaui.framework.widgets.story

import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Props for the StoryVideoPlayer widget. Mirrors Flutter's StoryVideoPlayerProps from
 * story_video_player_props.dart
 *
 * Focused on video-specific properties only.
 */
data class StoryVideoPlayerProps(
        /** URL of the video to play */
        val videoUrl: ExprOr<String>? = null,

        /** Whether to auto-play the video (default: true for stories) */
        val autoPlay: ExprOr<Boolean>? = null,

        /** Whether to loop the video */
        val looping: ExprOr<Boolean>? = null,

        /** BoxFit equivalent for video scaling (cover, contain, fill, etc.) */
        val fit: ExprOr<String>? = null
) {
    companion object {
        fun fromJson(json: JsonLike): StoryVideoPlayerProps {
            return StoryVideoPlayerProps(
                    videoUrl = ExprOr.fromValue(json["videoUrl"]),
                    autoPlay = ExprOr.fromValue(json["autoPlay"]),
                    looping = ExprOr.fromValue(json["looping"]),
                    fit = ExprOr.fromValue(json["fit"])
            )
        }
    }
}
