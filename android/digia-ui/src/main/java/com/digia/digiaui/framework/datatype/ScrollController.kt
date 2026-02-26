package com.digia.digiaui.framework.datatype

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * AdaptedScrollController provides programmatic scroll control for scrollable widgets.
 * 
 * This class wraps Android Compose's scroll states and provides a unified interface
 * compatible with the Digia UI expression system.
 * 
 * In Android Compose, there are three main scroll state types:
 * - ScrollState: For basic scrolling (used with Modifier.verticalScroll/horizontalScroll)
 * - LazyListState: For lazy lists (used with LazyColumn/LazyRow)
 * - LazyGridState: For lazy grids (used with LazyVerticalGrid/LazyHorizontalGrid)
 * 
 * This controller can wrap any of these types and provides common operations.
 * 
 * Example usage:
 * ```kotlin
 * // Create controller
 * val scrollController = AdaptedScrollController()
 * 
 * // Use in widget
 * val scrollState = rememberScrollState()
 * scrollController.attachScrollState(scrollState)
 * 
 * // Programmatic scroll
 * scope.launch {
 *     scrollController.animateScrollTo(500f)
 * }
 * 
 * // Access in expressions
 * val offset = scrollController.offset // @{scrollController.offset}
 * ```
 */
@Stable
class AdaptedScrollController {
    
    /**
     * The underlying ScrollState (for basic scrolling)
     */
    private var scrollState: ScrollState? = null
    
    /**
     * The underlying LazyListState (for lazy lists)
     */
    private var lazyListState: LazyListState? = null
    
    /**
     * The underlying LazyGridState (for lazy grids)
     */
    private var lazyGridState: LazyGridState? = null
    
    /**
     * Get the current scroll offset in pixels.
     * For expressions: @{controller.offset}
     */
    val offset: Float
        get() = when {
            scrollState != null -> scrollState!!.value.toFloat()
            lazyListState != null -> {
                val firstVisibleItemIndex = lazyListState!!.firstVisibleItemIndex
                val firstVisibleItemScrollOffset = lazyListState!!.firstVisibleItemScrollOffset
                // Approximate offset: index * item height + scroll offset
                // Note: This is approximate as we don't know actual item heights
                (firstVisibleItemIndex * 100 + firstVisibleItemScrollOffset).toFloat()
            }
            lazyGridState != null -> {
                val firstVisibleItemIndex = lazyGridState!!.firstVisibleItemIndex
                val firstVisibleItemScrollOffset = lazyGridState!!.firstVisibleItemScrollOffset
                // Approximate offset: index * item height + scroll offset
                (firstVisibleItemIndex * 100 + firstVisibleItemScrollOffset).toFloat()
            }
            else -> 0f
        }
    
    /**
     * Get the maximum scroll value in pixels.
     */
    val maxValue: Float
        get() = when {
            scrollState != null -> scrollState!!.maxValue.toFloat()
            lazyListState != null -> Float.MAX_VALUE // Lazy lists don't have a fixed max
            lazyGridState != null -> Float.MAX_VALUE // Lazy grids don't have a fixed max
            else -> 0f
        }
    
    /**
     * Check if scrolling is in progress.
     */
    val isScrollInProgress: Boolean
        get() = when {
            scrollState != null -> scrollState!!.isScrollInProgress
            lazyListState != null -> lazyListState!!.isScrollInProgress
            lazyGridState != null -> lazyGridState!!.isScrollInProgress
            else -> false
        }
    
    /**
     * Check if we can scroll forward (down/right).
     */
    val canScrollForward: Boolean
        get() = when {
            scrollState != null -> scrollState!!.canScrollForward
            lazyListState != null -> lazyListState!!.canScrollForward
            lazyGridState != null -> lazyGridState!!.canScrollForward
            else -> false
        }
    
    /**
     * Check if we can scroll backward (up/left).
     */
    val canScrollBackward: Boolean
        get() = when {
            scrollState != null -> scrollState!!.canScrollBackward
            lazyListState != null -> lazyListState!!.canScrollBackward
            lazyGridState != null -> lazyGridState!!.canScrollBackward
            else -> false
        }
    
    /**
     * Get the first visible item index (for LazyListState and LazyGridState).
     */
    val firstVisibleItemIndex: Int
        get() = lazyListState?.firstVisibleItemIndex ?: lazyGridState?.firstVisibleItemIndex ?: 0
    
    /**
     * Get the first visible item scroll offset (for LazyListState and LazyGridState).
     */
    val firstVisibleItemScrollOffset: Int
        get() = lazyListState?.firstVisibleItemScrollOffset ?: lazyGridState?.firstVisibleItemScrollOffset ?: 0
    
    /**
     * Attach a ScrollState to this controller.
     */
    fun attachScrollState(state: ScrollState) {
        this.scrollState = state
        this.lazyListState = null
        this.lazyGridState = null
    }
    
    /**
     * Attach a LazyListState to this controller.
     */
    fun attachLazyListState(state: LazyListState) {
        this.lazyListState = state
        this.scrollState = null
        this.lazyGridState = null
    }
    
    /**
     * Attach a LazyGridState to this controller.
     */
    fun attachLazyGridState(state: LazyGridState) {
        this.lazyGridState = state
        this.scrollState = null
        this.lazyListState = null
    }
    
    /**
     * Scroll to a specific pixel position with animation.
     * 
     * @param value The target scroll position in pixels
     */
    suspend fun animateScrollTo(value: Float, animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = androidx.compose.animation.core.spring()) {
        when {
            scrollState != null -> scrollState!!.animateScrollTo(value.toInt(), animationSpec = animationSpec)
            lazyListState != null -> {
                // For LazyList, scroll to item index
                val itemIndex = (value / 100).toInt() // Rough estimation
                lazyListState!!.animateScrollToItem(itemIndex.coerceAtLeast(0))
            }
            lazyGridState != null -> {
                // For LazyGrid, scroll to item index
                val itemIndex = (value / 100).toInt() // Rough estimation
                lazyGridState!!.animateScrollToItem(itemIndex.coerceAtLeast(0))
            }
        }
    }
    
    /**
     * Scroll to a specific pixel position immediately (no animation).
     * 
     * @param value The target scroll position in pixels
     */
    suspend fun scrollTo(value: Float) {
        when {
            scrollState != null -> scrollState!!.scrollTo(value.toInt())
            lazyListState != null -> {
                // For LazyList, scroll to item index
                val itemIndex = (value / 100).toInt() // Rough estimation
                lazyListState!!.scrollToItem(itemIndex.coerceAtLeast(0))
            }
            lazyGridState != null -> {
                // For LazyGrid, scroll to item index
                val itemIndex = (value / 100).toInt() // Rough estimation
                lazyGridState!!.scrollToItem(itemIndex.coerceAtLeast(0))
            }
        }
    }
    
    /**
     * Scroll by a specific pixel delta with animation.
     * 
     * @param delta The amount to scroll (positive = forward, negative = backward)
     */
    suspend fun animateScrollBy(delta: Float) {
        when {
            scrollState != null -> scrollState!!.animateScrollBy(delta)
            lazyListState != null -> {
                // For LazyList, approximate by scrolling items
                val itemDelta = (delta / 100).toInt()
                val targetIndex = (firstVisibleItemIndex + itemDelta).coerceAtLeast(0)
                lazyListState!!.animateScrollToItem(targetIndex)
            }
            lazyGridState != null -> {
                // For LazyGrid, approximate by scrolling items
                val itemDelta = (delta / 100).toInt()
                val targetIndex = (firstVisibleItemIndex + itemDelta).coerceAtLeast(0)
                lazyGridState!!.animateScrollToItem(targetIndex)
            }
        }
    }
    
    /**
     * Scroll by a specific pixel delta immediately (no animation).
     * 
     * @param delta The amount to scroll (positive = forward, negative = backward)
     */
    suspend fun scrollBy(delta: Float) {
        when {
            scrollState != null -> scrollState!!.scrollBy(delta)
            lazyListState != null -> {
                // For LazyList, approximate by scrolling items
                val itemDelta = (delta / 100).toInt()
                val targetIndex = (firstVisibleItemIndex + itemDelta).coerceAtLeast(0)
                lazyListState!!.scrollToItem(targetIndex)
            }
            lazyGridState != null -> {
                // For LazyGrid, approximate by scrolling items
                val itemDelta = (delta / 100).toInt()
                val targetIndex = (firstVisibleItemIndex + itemDelta).coerceAtLeast(0)
                lazyGridState!!.scrollToItem(targetIndex)
            }
        }
    }
    
    /**
     * Scroll to a specific item index (for LazyListState and LazyGridState).
     * 
     * @param index The item index to scroll to
     * @param scrollOffset Optional scroll offset within the item
     */
    suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0) {
        lazyListState?.animateScrollToItem(index, scrollOffset)
        lazyGridState?.animateScrollToItem(index, scrollOffset)
    }
    
    /**
     * Scroll to a specific item index immediately (for LazyListState and LazyGridState).
     * 
     * @param index The item index to scroll to
     * @param scrollOffset Optional scroll offset within the item
     */
    suspend fun scrollToItem(index: Int, scrollOffset: Int = 0) {
        lazyListState?.scrollToItem(index, scrollOffset)
        lazyGridState?.scrollToItem(index, scrollOffset)
    }
    
    /**
     * Convenience method to scroll to top with animation.
     */
    suspend fun animateScrollToTop() {
        when {
            scrollState != null -> scrollState!!.animateScrollTo(0)
            lazyListState != null -> lazyListState!!.animateScrollToItem(0)
            lazyGridState != null -> lazyGridState!!.animateScrollToItem(0)
        }
    }
    
    /**
     * Convenience method to scroll to top immediately.
     */
    suspend fun scrollToTop() {
        when {
            scrollState != null -> scrollState!!.scrollTo(0)
            lazyListState != null -> lazyListState!!.scrollToItem(0)
            lazyGridState != null -> lazyGridState!!.scrollToItem(0)
        }
    }
    
    /**
     * Convenience method to scroll to bottom with animation.
     * Note: For LazyList and LazyGrid, this requires knowing the item count.
     */
    suspend fun animateScrollToBottom(itemCount: Int = 0) {
        when {
            scrollState != null -> scrollState!!.animateScrollTo(scrollState!!.maxValue)
            lazyListState != null && itemCount > 0 -> 
                lazyListState!!.animateScrollToItem(itemCount - 1)
            lazyGridState != null && itemCount > 0 ->
                lazyGridState!!.animateScrollToItem(itemCount - 1)
        }
    }
    
    /**
     * Get field value by name for expression evaluation.
     * Supports accessing scroll properties in expressions.
     * 
     * Available fields:
     * - offset: Current scroll offset in pixels
     * - maxValue: Maximum scroll value
     * - isScrollInProgress: Whether scrolling is in progress
     * - canScrollForward: Can scroll forward
     * - canScrollBackward: Can scroll backward
     * - firstVisibleItemIndex: First visible item index (LazyList only)
     * - firstVisibleItemScrollOffset: First visible item offset (LazyList only)
     */
    fun getField(name: String): Any? = when (name) {
        "offset" -> offset
        "maxValue" -> maxValue
        "isScrollInProgress" -> isScrollInProgress
        "canScrollForward" -> canScrollForward
        "canScrollBackward" -> canScrollBackward
        "firstVisibleItemIndex" -> firstVisibleItemIndex
        "firstVisibleItemScrollOffset" -> firstVisibleItemScrollOffset
        else -> null
    }
}

/**
 * Extension function to perform scroll operations with a coroutine scope.
 */
fun AdaptedScrollController.scrollToWithScope(
    scope: CoroutineScope,
    value: Float,
    animated: Boolean = true
) {
    scope.launch {
        if (animated) {
            animateScrollTo(value)
        } else {
            scrollTo(value)
        }
    }
}

/**
 * Extension function to scroll by delta with a coroutine scope.
 */
fun AdaptedScrollController.scrollByWithScope(
    scope: CoroutineScope,
    delta: Float,
    animated: Boolean = true
) {
    scope.launch {
        if (animated) {
            animateScrollBy(delta)
        } else {
            scrollBy(delta)
        }
    }
}

/**
 * Extension function to scroll to item with a coroutine scope (LazyList only).
 */
fun AdaptedScrollController.scrollToItemWithScope(
    scope: CoroutineScope,
    index: Int,
    scrollOffset: Int = 0,
    animated: Boolean = true
) {
    scope.launch {
        if (animated) {
            animateScrollToItem(index, scrollOffset)
        } else {
            scrollToItem(index, scrollOffset)
        }
    }
}