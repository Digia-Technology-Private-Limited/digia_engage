package com.digia.digiaui.framework.widgets.story

import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.story.StoryController
import com.digia.digiaui.framework.utils.JsonLike

/** Props for the Story widget. Mirrors Flutter's StoryProps from story_props.dart */
data class StoryProps(
        /** Data source for repeated story items */
        val dataSource: ExprOr<List<Any>>? = null,

        /** Controller for external control of playback */
        val controller: ExprOr<StoryController>? = null,

        /** Initial story index to display */
        val initialIndex: ExprOr<Int>? = null,

        /** Whether to restart from beginning after completion */
        val restartOnCompleted: ExprOr<Boolean>? = null,

        /** Default duration for each story item in milliseconds */
        val duration: ExprOr<Int>? = null,

        /** Indicator styling configuration */
        val indicator: JsonLike? = null,

        // Placeholder action props - not fully implemented
        val onCompleted: ActionFlow? = null,
        val onSlideDown: ActionFlow? = null,
        val onSlideStart: ActionFlow? = null,
        val onLeftTap: ActionFlow? = null,
        val onRightTap: ActionFlow? = null,
        val onPreviousCompleted: ActionFlow? = null,
        val onStoryChanged: ActionFlow? = null
) {
    companion object {
        fun fromJson(json: JsonLike): StoryProps {
            return StoryProps(
                    dataSource = ExprOr.fromValue(json["dataSource"]),
                    controller = ExprOr.fromValue(json["controller"]),
                    initialIndex = ExprOr.fromValue(json["initialIndex"]),
                    restartOnCompleted = ExprOr.fromValue(json["restartOnCompleted"]),
                    duration = ExprOr.fromValue(json["duration"]),
                    indicator = json["indicator"] as? JsonLike,
                    onCompleted = (json["onCompleted"] as? JsonLike)?.let(ActionFlow::fromJson),
                    onSlideDown = (json["onSlideDown"] as? JsonLike)?.let(ActionFlow::fromJson),
                    onSlideStart = (json["onSlideStart"] as? JsonLike)?.let(ActionFlow::fromJson),
                    onLeftTap = (json["onLeftTap"] as? JsonLike)?.let(ActionFlow::fromJson),
                    onRightTap = (json["onRightTap"] as? JsonLike)?.let(ActionFlow::fromJson),
                    onPreviousCompleted =
                            (json["onPreviousCompleted"] as? JsonLike)?.let(ActionFlow::fromJson),
                    onStoryChanged =
                            (json["onStoryChanged"] as? JsonLike)?.let(ActionFlow::fromJson)
            )
        }
    }
}
