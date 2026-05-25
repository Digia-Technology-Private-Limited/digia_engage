package com.digia.engage.internal.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.digia.engage.internal.model.InlineCarouselConfig
import kotlinx.coroutines.delay

@Composable
internal fun DigiaInlineCarousel(
    config: InlineCarouselConfig,
    modifier: Modifier = Modifier,
) {
    if (config.items.isEmpty()) return

    val itemCount = config.items.size
    val useInfinite = config.infiniteScroll && itemCount > 1
    val pageCount = if (useInfinite) Int.MAX_VALUE else itemCount
    val initialPage = if (useInfinite) (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % itemCount else 0

    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val uriHandler = LocalUriHandler.current

    if (config.autoPlay && itemCount > 1) {
        LaunchedEffect(pagerState) {
            while (true) {
                delay(config.autoPlayInterval)
                val next = pagerState.currentPage + 1
                pagerState.animateScrollToPage(
                    page = next,
                    animationSpec = tween(durationMillis = config.animationDuration),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(config.height.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val horizontalPadding = maxWidth * ((1f - config.viewportFraction) / 2f)

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                pageSpacing = 8.dp,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val realIndex = page % itemCount
                val item = config.items[realIndex]

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (!item.deepLink.isNullOrBlank()) {
                                Modifier.clickable {
                                    runCatching { uriHandler.openUri(item.deepLink) }
                                        .onFailure { e ->
                                            android.util.Log.w("DigiaSlot", "deep link failed: ${item.deepLink} — ${e.message}")
                                        }
                                }
                            } else Modifier,
                        ),
                ) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (config.indicator.showIndicator && itemCount > 1) {
            val currentReal = pagerState.currentPage % itemCount
            CarouselIndicator(
                itemCount = itemCount,
                activeIndex = currentReal,
                indicatorConfig = config.indicator,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun CarouselIndicator(
    itemCount: Int,
    activeIndex: Int,
    indicatorConfig: com.digia.engage.internal.model.CarouselIndicatorConfig,
    modifier: Modifier = Modifier,
) {
    val dotColor = parseColor(indicatorConfig.dotColor)
    val activeDotColor = parseColor(indicatorConfig.activeDotColor)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(indicatorConfig.spacing.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            val isActive = index == activeIndex
            val dotWidth = if (isActive) indicatorConfig.dotWidth * 2.5f else indicatorConfig.dotWidth
            Box(
                modifier = Modifier
                    .width(dotWidth.dp)
                    .size(indicatorConfig.dotHeight.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeDotColor else dotColor),
            )
        }
    }
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color.Gray
}
