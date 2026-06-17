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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val payload = slotPayloads[placementKey]

    // Digia's impression fires once, the first time the slot actually renders
    // (deduped per campaign). CEP was already impressed at route time.
    if ((carouselConfig != null || storyConfig != null) && payload != null) {
        LaunchedEffect(payload.cepCampaignId) {
            DigiaInstance.reportSlotFirstRender(payload)
        }
    }

    when {
        carouselConfig != null -> InlineCarouselView(carouselConfig, payload, modifier)
        storyConfig != null -> {
            DigiaInlineStory(
                config = storyConfig,
                payload = payload
                    ?: CEPTriggerPayload(cepCampaignId = placementKey, campaignKey = placementKey),
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun InlineCarouselView(
    config: InlineCarouselConfig,
    payload: CEPTriggerPayload?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val items = config.items.filter { it.imageUrl.isNotBlank() }
    if (items.isEmpty()) return

    val pageCount = items.size
    val pagerState = rememberPagerState(pageCount = {
        if (config.infiniteScroll) Int.MAX_VALUE else pageCount
    })

    // Tracks whether the next settled page change came from autoplay (vs a manual
    // swipe) so the Step Viewed event can carry `auto`.
    var autoAdvancePending by remember { mutableStateOf(false) }

    if (config.autoPlay && pageCount > 1) {
        LaunchedEffect(pagerState) {
            while (true) {
                delay(config.autoPlayInterval)
                autoAdvancePending = true
                val next = pagerState.currentPage + 1
                pagerState.animateScrollToPage(
                    if (config.infiniteScroll) next else next.coerceAtMost(pageCount - 1)
                )
            }
        }
    }

    // Step Viewed fires for each item that settles into view (including the first).
    if (payload != null) {
        LaunchedEffect(pagerState, payload) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                val realIndex = page % pageCount
                DigiaInstance.reportCarouselStepViewed(
                    payload,
                    itemIndex = realIndex + 1,
                    itemTotal = pageCount,
                    auto = autoAdvancePending,
                )
                autoAdvancePending = false
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
            val item = items[realIndex]
            SubcomposeAsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (payload != null) {
                            DigiaInstance.reportCarouselStepClicked(
                                payload,
                                itemIndex = realIndex + 1,
                                actionUrl = item.deepLink,
                            )
                        }
                        item.deepLink?.let { url ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
