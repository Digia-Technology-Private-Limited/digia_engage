package com.digia.engage

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
        carouselConfig != null -> {
            val payload = slotPayloads[placementKey]
                ?: InAppPayload(id = placementKey, content = emptyMap(), cepContext = emptyMap())
            InlineCarouselView(carouselConfig, payload, modifier)
        }
        storyConfig != null -> {
            val payload = slotPayloads[placementKey]
                ?: InAppPayload(id = placementKey, content = emptyMap(), cepContext = emptyMap())
            DigiaInlineStory(config = storyConfig, payload = payload, modifier = modifier)
        }
    }
}

@Composable
private fun InlineCarouselView(
    config: InlineCarouselConfig,
    payload: InAppPayload,
    modifier: Modifier = Modifier,
) {
    val items = config.items.filter { it.imageUrl.isNotBlank() }
    if (items.isEmpty()) return
    val images = items.map { it.imageUrl }

    val pageCount = images.size
    val pagerState = rememberPagerState(pageCount = {
        if (config.infiniteScroll) Int.MAX_VALUE else pageCount
    })
    val context = LocalContext.current

    // First render: rich `Digia Experience Viewed` (coarse Impressed → CEP) plus the
    // initial `Digia Step Viewed` for the visible slide. Keyed on payload id so a
    // re-triggered campaign impresses afresh and the same render does not double-fire.
    LaunchedEffect(payload.id) {
        DigiaInstance.reportInlineViewed(payload, displayStyle = "carousel", itemTotal = pageCount)
        DigiaInstance.reportInlineStep(payload, displayStyle = "carousel", itemIndex = 0, itemTotal = pageCount)
    }
    // Subsequent slide changes → `Digia Step Viewed` (the initial 0 is already sent).
    LaunchedEffect(pagerState, payload.id) {
        snapshotFlow { pagerState.currentPage % pageCount }
            .distinctUntilChanged()
            .drop(1)
            .collect { index ->
                DigiaInstance.reportInlineStep(payload, displayStyle = "carousel", itemIndex = index, itemTotal = pageCount)
            }
    }

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
            val deepLink = items[realIndex].deepLink
            SubcomposeAsyncImage(
                model = images[realIndex],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        // The tapped slide is the matrix `Digia Step Clicked`; open its
                        // deeplink (if any) the same way nudge OpenDeeplinkAction does.
                        DigiaInstance.emitInlineStepClick(
                            payload = payload,
                            displayStyle = "carousel",
                            itemIndex = realIndex,
                            itemTotal = pageCount,
                            deepLink = deepLink,
                        )
                        if (!deepLink.isNullOrBlank()) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    },
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
