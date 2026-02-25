package com.digia.digiaui.framework.story

import androidx.compose.runtime.compositionLocalOf
import androidx.media3.exoplayer.ExoPlayer

/**
 * Callback type for video player registration.
 *
 * - null: Video is loading, presenter should wait
 * - ExoPlayer: Video is ready, presenter should use duration for timing
 *
 * Mirrors Flutter's StoryVideoCallbackProvider InheritedWidget.
 */
typealias OnVideoLoad = (ExoPlayer?) -> Unit

/**
 * CompositionLocal for providing video load callback to child video players.
 *
 * This is the Compose equivalent of Flutter's InheritedWidget pattern. Video players use this to
 * notify the StoryPresenter when they're ready.
 *
 * Usage in StoryPresenter:
 * ```kotlin
 * CompositionLocalProvider(LocalStoryVideoCallback provides ::onVideoLoad) {
 *     // Child content including video players
 * }
 * ```
 *
 * Usage in StoryVideoPlayer:
 * ```kotlin
 * val onVideoLoad = LocalStoryVideoCallback.current
 * onVideoLoad?.invoke(null)  // Signal loading started
 * // ... initialize player ...
 * onVideoLoad?.invoke(exoPlayer)  // Signal ready
 * ```
 */
val LocalStoryVideoCallback = compositionLocalOf<OnVideoLoad?> { null }
