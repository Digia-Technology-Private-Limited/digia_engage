package com.digia.engage

import LocalUIResources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.digia.engage.framework.RenderPayload
import com.digia.engage.framework.UIResources
import com.digia.engage.framework.models.CommonProps
import com.digia.engage.framework.models.CommonStyle
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.widgets.CarouselProps
import com.digia.engage.framework.widgets.ImageProps
import com.digia.engage.framework.widgets.VWCarousel
import com.digia.engage.framework.widgets.VWImage
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.model.InlineCarouselConfig
import com.digia.engage.internal.ui.GuideRenderer
import com.digia.engage.internal.ui.SurveyRenderer

@Composable
fun DigiaHost(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        GuideRenderer()
        SurveyRenderer()
    }
}

@Composable
fun DigiaScreen(name: String) {
    LaunchedEffect(name) { Digia.setCurrentScreen(name) }
}

@Composable
fun DigiaSlot(placementKey: String, modifier: Modifier = Modifier) {
    val slotConfigs by DigiaInstance.controller.slotConfigs.collectAsState()
    val config = slotConfigs[placementKey] ?: return

    android.util.Log.d("DigiaSlot", "rendering carousel for slot '$placementKey' items=${config.items.size}")

    CompositionLocalProvider(LocalUIResources provides UIResources()) {
        val carousel = remember(config) { buildCarouselWidget(config) }
        carousel.ToWidget(RenderPayload(scopeContext = null))
    }
}

private fun buildCarouselWidget(config: InlineCarouselConfig): VWCarousel {
    val dataSource: List<Map<String, Any?>> = config.items.map { item ->
        mapOf("image_url" to item.imageUrl, "deep_link" to item.deepLink)
    }

    val imageNode = VWImage(
        refName = null,
        commonProps = CommonProps(
            visibility = null,
            align = null,
            style = CommonStyle(width = "100%", height = "100%"),
            onClick = null,
        ),
        parent = null,
        props = ImageProps(
            imageSrc = ExprOr.fromValue(mapOf("expr" to "currentItem.image_url")),
            sourceType = "network",
            fit = "cover",
        ),
    )

    val ind = config.indicator
    val carouselProps = CarouselProps(
        height = "${config.height}px",
        width = config.width?.let { "${it}px" },
        autoPlay = config.autoPlay,
        autoPlayInterval = config.autoPlayInterval.toInt(),
        animationDuration = config.animationDuration,
        infiniteScroll = config.infiniteScroll,
        viewportFraction = config.viewportFraction.toDouble(),
        padEnds = true,
        showIndicator = ind.showIndicator,
        dotHeight = ind.dotHeight.toDouble(),
        dotWidth = ind.dotWidth.toDouble(),
        spacing = ind.spacing.toDouble(),
        dotColor = ExprOr.fromValue(ind.dotColor),
        activeDotColor = ExprOr.fromValue(ind.activeDotColor),
        indicatorEffectType = ind.indicatorEffectType,
        dataSource = dataSource,
    )

    return VWCarousel(
        props = carouselProps,
        slots = { mapOf("child" to listOf(imageNode)) },
    )
}
