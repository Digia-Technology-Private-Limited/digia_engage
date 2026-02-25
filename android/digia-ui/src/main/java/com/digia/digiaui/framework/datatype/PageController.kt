package com.digia.digiaui.framework.datatype

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable

/**
 * Flutter-like PageController adapter for Compose pager widgets.
 *
 * This controller is created from variables of type `pageController` and can be used
 * in expressions and actions via method bindings (e.g. `controller.jumpToPage`).
 */
@Stable
class AdaptedPageController {

    private var pagerState: PagerState? = null

    /** Current page index (0-based). */
    val page: Int
        get() = pagerState?.currentPage ?: 0

    /** Current page offset fraction (-0.5..0.5-ish depending on fling). */
    val pageOffsetFraction: Float
        get() = pagerState?.currentPageOffsetFraction ?: 0f

    /** True if currently scrolling. */
    val isScrollInProgress: Boolean
        get() = pagerState?.isScrollInProgress ?: false

    fun attachPagerState(state: PagerState) {
        pagerState = state
    }

    suspend fun jumpToPage(page: Int) {
        pagerState?.scrollToPage(page)
    }

    suspend fun animateToPage(
        page: Int,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        pagerState?.animateScrollToPage(page, animationSpec = animationSpec)
    }
}
