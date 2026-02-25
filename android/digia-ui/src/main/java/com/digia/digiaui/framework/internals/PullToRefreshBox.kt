package com.digia.digiaui.framework.internals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt

/**
 * `PullToRefreshBox` wrapper used by Digia widgets.
 *
 * Material3's pull-to-refresh API is version-dependent; this wrapper keeps our widget API stable.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullToRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorColor: Color = Color.Unspecified,
    indicatorBackgroundColor: Color = Color.Transparent,
    indicatorTopPadding: Dp = 0.dp,
    refreshingOffset: Dp = 80.dp,
    refreshThreshold: Dp = 80.dp,
    strokeWidth: Dp = 2.dp,
    enabled: Boolean = true,
    // Flutter parity note:
    // - `onEdge` vs `anywhere` depends on scrollable/nested-scroll behavior.
    // - Compose Material pull-refresh behaves closest to Flutter's `anywhere`.
    triggerMode: String? = null,
    content: @Composable () -> Unit,
) {
    val pullState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = onRefresh,
        refreshThreshold = refreshThreshold,
        refreshingOffset = refreshingOffset,
    )

    // Currently `triggerMode` is accepted for API parity; behavior is governed by nested scroll.
    val effectiveEnabled = enabled && triggerMode != "disabled"

    Box(modifier = modifier.pullRefresh(pullState, enabled = effectiveEnabled)) {
        content()

        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = if (indicatorColor == Color.Unspecified) Color.Blue else indicatorColor,
            backgroundColor = if (indicatorBackgroundColor == Color.Unspecified) Color.White else indicatorBackgroundColor,
        )
    }
}
