package com.digia.digiaui.framework.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

/**
 * Story progress indicator showing bars for each story item. Mirrors Flutter's StoryViewIndicator
 * from story_view_indicator.dart
 *
 * Each bar represents one story:
 * - Completed: filled with completedColor
 * - Current: partially filled based on progress (0.0 to 1.0)
 * - Upcoming: filled with disabledColor
 */
@Composable
fun StoryIndicator(
        currentIndex: Int,
        progress: Float,
        totalItems: Int,
        config: StoryIndicatorConfig,
        modifier: Modifier = Modifier
) {
    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .padding(
                                    horizontal = config.horizontalPadding,
                                    vertical = config.topPadding
                            ),
            horizontalArrangement = Arrangement.spacedBy(config.horizontalGap)
    ) {
        repeat(totalItems) { index ->
            StoryIndicatorBar(
                    fillProgress =
                            when {
                                index < currentIndex -> 1f // completed
                                index == currentIndex -> progress // current
                                else -> 0f // upcoming
                            },
                    activeColor = config.activeColor,
                    backgroundColor =
                            when {
                                index < currentIndex -> config.completedColor
                                else -> config.disabledColor
                            },
                    height = config.height,
                    borderRadius = config.borderRadius,
                    modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Single indicator bar with fill animation. */
@Composable
private fun StoryIndicatorBar(
        fillProgress: Float,
        activeColor: Color,
        backgroundColor: Color,
        height: androidx.compose.ui.unit.Dp,
        borderRadius: androidx.compose.ui.unit.Dp,
        modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(borderRadius)

    Box(modifier = modifier.height(height).clip(shape).background(backgroundColor)) {
        // Active fill overlay
        if (fillProgress > 0f) {
            Box(
                    modifier =
                            Modifier.fillMaxWidth(fillProgress)
                                    .height(height)
                                    .background(activeColor)
            )
        }
    }
}
