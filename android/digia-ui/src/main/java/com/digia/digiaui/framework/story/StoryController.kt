package com.digia.digiaui.framework.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Controller for managing Story playback state. Mirrors Flutter's FlutterStoryController from
 * flutter_story_controller.dart
 *
 * Usage:
 * ```kotlin
 * val controller = StoryController()
 * controller.pause()  // Pause current story
 * controller.play()   // Resume playback
 * controller.next()   // Go to next story
 * ```
 */
class StoryController {

    /** Current action state of the story */
    var storyAction: StoryAction by mutableStateOf(StoryAction.PLAY)
        private set

    /** Index to jump to, null if no jump requested */
    var jumpIndex: Int? by mutableStateOf(null)
        private set

    /** Play the current story */
    fun play() {
        storyAction = StoryAction.PLAY
    }

    /** Pause the current story */
    fun pause() {
        storyAction = StoryAction.PAUSE
    }

    /** Move to the next story */
    fun next() {
        storyAction = StoryAction.NEXT
    }

    /** Move to the previous story */
    fun previous() {
        storyAction = StoryAction.PREVIOUS
    }

    /** Mute audio */
    fun mute() {
        storyAction = StoryAction.MUTE
    }

    /** Unmute audio */
    fun unMute() {
        storyAction = StoryAction.UNMUTE
    }

    /** Jump to a specific story index */
    fun jumpTo(index: Int) {
        jumpIndex = index
    }

    /** Clear the jump index after handling */
    internal fun clearJumpIndex() {
        jumpIndex = null
    }

    /** Reset action after handling */
    internal fun resetAction() {
        storyAction = StoryAction.PLAY
    }
}
