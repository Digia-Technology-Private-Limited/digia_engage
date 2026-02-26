package com.digia.digiaui.framework.story

/**
 * Actions that can be performed on a Story.
 * Mirrors Flutter's StoryAction enum from story_utils.dart
 */
enum class StoryAction {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
    MUTE,
    UNMUTE;

    val isPlay: Boolean get() = this == PLAY
    val isPause: Boolean get() = this == PAUSE
    val isNext: Boolean get() = this == NEXT
    val isPrevious: Boolean get() = this == PREVIOUS
    val isMute: Boolean get() = this == MUTE
    val isUnMute: Boolean get() = this == UNMUTE
}

/**
 * Story item types - placeholder for future implementation.
 * Currently only VIDEO is fully implemented.
 */
enum class StoryItemType {
    IMAGE,
    VIDEO,
    TEXT,
    WEB,
    CUSTOM
}

/**
 * Story item source - placeholder for future implementation.
 */
enum class StoryItemSource {
    ASSET,
    NETWORK,
    FILE
}
