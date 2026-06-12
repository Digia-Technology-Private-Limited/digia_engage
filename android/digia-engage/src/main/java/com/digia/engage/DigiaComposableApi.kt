package com.digia.engage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.model.InlineCarouselConfig
import com.digia.engage.internal.ui.DigiaInlineStory
import com.digia.engage.internal.ui.DigiaStoryOverlay
import com.digia.engage.internal.ui.GuideRenderer
import com.digia.engage.internal.ui.NudgeRenderer
import com.digia.engage.internal.ui.SurveyRenderer
import kotlinx.coroutines.delay

@Composable
fun DigiaHost(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        GuideRenderer()
        SurveyRenderer()
        NudgeRenderer()
        DigiaStoryOverlay()
    }
}

@Composable
fun DigiaScreen(name: String) {
    LaunchedEffect(name) { Digia.setCurrentScreen(name) }
}

@Composable
fun DigiaSlot(placementKey: String, modifier: Modifier = Modifier) {
    val slotConfigs by DigiaInstance.controller.slotConfigs.collectAsState()
    val storySlotConfigs by DigiaInstance.controller.storySlotConfigs.collectAsState()
    val slotPayloads by DigiaInstance.controller.slotPayloads.collectAsState()

    val carouselConfig = slotConfigs[placementKey]
    val storyConfig = storySlotConfigs[placementKey]

    when {
        carouselConfig != null -> InlineCarouselView(carouselConfig, modifier)
        storyConfig != null -> {
            val payload = slotPayloads[placementKey]
                ?: InAppPayload(id = placementKey, content = emptyMap(), cepContext = emptyMap())
            DigiaInlineStory(config = storyConfig, payload = payload, modifier = modifier)
        }
    }
}

@Composable
private fun InlineCarouselView(config: InlineCarouselConfig, modifier: Modifier = Modifier) {
    val images = config.items.map { it.imageUrl }.filter { it.isNotBlank() }
    if (images.isEmpty()) return

    val pageCount = images.size
    val pagerState = rememberPagerState(pageCount = {
        if (config.infiniteScroll) Int.MAX_VALUE else pageCount
    })

    if (config.autoPlay && pageCount > 1) {
        LaunchedEffect(pagerState) {
            while (true) {
                delay(config.autoPlayInterval)
                val next = pagerState.currentPage + 1
                pagerState.animateScrollToPage(
                    if (config.infiniteScroll) next else next.coerceAtMost(pageCount - 1)
                )
            }
        }
    }

    val ind = config.indicator
    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(config.height.dp),
        ) { page ->
            val realIndex = page % pageCount
            SubcomposeAsyncImage(
                model = images[realIndex],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) { SubcomposeAsyncImageContent() }
        }
        if (ind.showIndicator && pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                val currentIndex = pagerState.currentPage % pageCount
                repeat(pageCount) { i ->
                    val isActive = i == currentIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = (ind.spacing / 2).dp)
                            .size(width = ind.dotWidth.dp, height = ind.dotHeight.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) parseColor(ind.activeDotColor) else parseColor(ind.dotColor)
                            ),
                    )
                }
            }
        }
    }
}

private fun parseColor(hex: String, fallback: Color = Color(0xFFCBD5E1.toInt())): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { fallback }
