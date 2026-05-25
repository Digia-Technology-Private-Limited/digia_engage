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
    val autoPlay: Boolean = true,
    val autoPlayInterval: Long = 3000L,
    val animationDuration: Int = 700,
    val infiniteScroll: Boolean = true,
    val viewportFraction: Float = 0.88f,
    val indicator: CarouselIndicatorConfig = CarouselIndicatorConfig(),
) {
    companion object {
        fun fromJson(json: JSONObject): InlineCarouselConfig? {
            val slotKey = json.optString("slot_key", "").takeIf { it.isNotBlank() } ?: return null

            val itemsArr = json.optJSONArray("items") ?: return null
            val items = mutableListOf<CarouselItem>()
            for (i in 0 until itemsArr.length()) {
                val itemJson = itemsArr.optJSONObject(i) ?: continue
                val imageUrl = itemJson.optString("image_url", "").takeIf { it.isNotBlank() } ?: continue
                val deepLink = itemJson.optString("deep_link", "").takeIf { it.isNotBlank() }
                items.add(CarouselItem(imageUrl = imageUrl, deepLink = deepLink))
            }
            if (items.isEmpty()) return null

            val indicatorJson = json.optJSONObject("indicator")
            val indicator = if (indicatorJson != null) {
                CarouselIndicatorConfig(
                    showIndicator = indicatorJson.optBoolean("show_indicator", true),
                    dotHeight = indicatorJson.optDouble("dot_height", 8.0).toFloat(),
                    dotWidth = indicatorJson.optDouble("dot_width", 8.0).toFloat(),
                    spacing = indicatorJson.optDouble("spacing", 12.0).toFloat(),
                    dotColor = indicatorJson.optString("dot_color", "#CBD5E1"),
                    activeDotColor = indicatorJson.optString("active_dot_color", "#4945FF"),
                    indicatorEffectType = indicatorJson.optString("indicator_effect_type", "slide"),
                )
            } else {
                CarouselIndicatorConfig()
            }

            return InlineCarouselConfig(
                slotKey = slotKey,
                items = items,
                height = json.optInt("height", 180),
                autoPlay = json.optBoolean("auto_play", true),
                autoPlayInterval = json.optLong("auto_play_interval", 3000L),
                animationDuration = json.optInt("animation_duration", 700),
                infiniteScroll = json.optBoolean("infinite_scroll", true),
                viewportFraction = json.optDouble("viewport_fraction", 0.88).toFloat(),
                indicator = indicator,
            )
        }
    }
}
