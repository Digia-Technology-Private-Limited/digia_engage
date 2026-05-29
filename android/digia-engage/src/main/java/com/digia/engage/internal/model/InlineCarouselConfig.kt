package com.digia.engage.internal.model

import org.json.JSONObject

data class CarouselItem(
    val imageUrl: String,
    val deepLink: String? = null,
)

data class CarouselIndicatorConfig(
    val showIndicator: Boolean = true,
    val dotHeight: Float = 8f,
    val dotWidth: Float = 8f,
    val spacing: Float = 12f,
    val dotColor: String = "#CBD5E1",
    val activeDotColor: String = "#4945FF",
    val indicatorEffectType: String = "slide",
)

data class InlineCarouselConfig(
    val slotKey: String,
    val items: List<CarouselItem>,
    val height: Int = 180,
    val width: Int? = null,
    val autoPlay: Boolean = true,
    val autoPlayInterval: Long = 3000L,
    val animationDuration: Int = 700,
    val infiniteScroll: Boolean = true,
    val viewportFraction: Float = 0.88f,
    val indicator: CarouselIndicatorConfig = CarouselIndicatorConfig(),
) {
    companion object {
        fun fromJson(json: JSONObject): InlineCarouselConfig? {
            val slotKey = json.optString("slotKey", "").takeIf { it.isNotBlank() } ?: return null

            val itemsArr = json.optJSONArray("items") ?: return null
            val items = mutableListOf<CarouselItem>()
            for (i in 0 until itemsArr.length()) {
                val itemJson = itemsArr.optJSONObject(i) ?: continue
                val imageUrl = itemJson.optString("imageUrl", "").takeIf { it.isNotBlank() } ?: continue
                val deepLink = itemJson.optString("deepLink", "").takeIf { it.isNotBlank() }
                items.add(CarouselItem(imageUrl = imageUrl, deepLink = deepLink))
            }
            if (items.isEmpty()) return null

            val indicatorJson = json.optJSONObject("indicator")
            val indicator = if (indicatorJson != null) {
                CarouselIndicatorConfig(
                    showIndicator = indicatorJson.optBoolean("showIndicator", true),
                    dotHeight = indicatorJson.optDouble("dotHeight", 8.0).toFloat(),
                    dotWidth = indicatorJson.optDouble("dotWidth", 8.0).toFloat(),
                    spacing = indicatorJson.optDouble("spacing", 12.0).toFloat(),
                    dotColor = indicatorJson.optString("dotColor", "#CBD5E1"),
                    activeDotColor = indicatorJson.optString("activeDotColor", "#4945FF"),
                    indicatorEffectType = indicatorJson.optString("indicatorEffectType", "slide"),
                )
            } else {
                CarouselIndicatorConfig()
            }

            return InlineCarouselConfig(
                slotKey = slotKey,
                items = items,
                height = json.optInt("height", 180),
                width = json.opt("width").let { v -> (v as? Number)?.toInt()?.takeIf { it > 0 } },
                autoPlay = json.optBoolean("autoPlay", true),
                autoPlayInterval = json.optLong("autoPlayInterval", 3000L),
                animationDuration = json.optInt("animationDuration", 700),
                infiniteScroll = json.optBoolean("infiniteScroll", true),
                viewportFraction = json.optDouble("viewportFraction", 0.88).toFloat(),
                indicator = indicator,
            )
        }
    }
}
