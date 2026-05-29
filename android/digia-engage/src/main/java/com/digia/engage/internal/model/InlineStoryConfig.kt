package com.digia.engage.internal.model

import org.json.JSONObject

internal data class StoryCtaAction(
    val type: String,
    val url: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): StoryCtaAction = StoryCtaAction(
            type = json.optString("type", "dismiss"),
            url = json.optString("url", "").takeIf { it.isNotBlank() },
        )
    }
}

internal data class StoryItemConfig(
    val type: String,
    val url: String,
    val duration: Int?,
    val ctaEnabled: Boolean = false,
    val ctaText: String? = null,
    val ctaTextColor: String = "#FFFFFF",
    val ctaBackgroundColor: String = "#4945FF",
    val ctaCornerRadius: Int = 8,
    val ctaAction: StoryCtaAction? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): StoryItemConfig? {
            val url = json.optString("url", "").takeIf { it.isNotBlank() } ?: return null
            return StoryItemConfig(
                type = json.optString("type", "image"),
                url = url,
                duration = json.optInt("duration", 0).takeIf { it > 0 },
                ctaEnabled = json.optBoolean("ctaEnabled", false),
                ctaText = json.optString("ctaText", "").takeIf { it.isNotBlank() },
                ctaTextColor = json.optString("ctaTextColor", "#FFFFFF").ifBlank { "#FFFFFF" },
                ctaBackgroundColor = json.optString("ctaBackgroundColor", "#4945FF").ifBlank { "#4945FF" },
                ctaCornerRadius = json.optInt("ctaCornerRadius", 8),
                ctaAction = json.optJSONObject("ctaAction")?.let { StoryCtaAction.fromJson(it) },
            )
        }
    }
}

internal data class StoryCardConfig(
    val height: Int = 220,
    val aspectRatio: Float = 0.6f,
    val borderRadius: Float = 12f,
    val spacing: Int = 8,
) {
    companion object {
        fun fromJson(json: JSONObject?): StoryCardConfig {
            if (json == null) return StoryCardConfig()
            return StoryCardConfig(
                height = json.optInt("height", 220).takeIf { it > 0 } ?: 220,
                aspectRatio = json.optDouble("aspectRatio", 0.6).toFloat().takeIf { it > 0f } ?: 0.6f,
                borderRadius = json.optDouble("borderRadius", 12.0).toFloat(),
                spacing = json.optInt("spacing", 8),
            )
        }
    }
}

internal data class StoryIndicatorDisplayConfig(
    val activeColor: String = "#FFFFFF",
    val completedColor: String = "#AAAAAA",
    val disabledColor: String = "#555555",
    val height: Float = 3.5f,
    val borderRadius: Float = 4f,
    val horizontalGap: Float = 4f,
    val topPadding: Float = 14f,
    val horizontalPadding: Float = 10f,
) {
    companion object {
        fun fromJson(json: JSONObject?): StoryIndicatorDisplayConfig {
            if (json == null) return StoryIndicatorDisplayConfig()
            return StoryIndicatorDisplayConfig(
                activeColor = json.optString("activeColor", "#FFFFFF").ifBlank { "#FFFFFF" },
                completedColor = json.optString("completedColor", "#AAAAAA").ifBlank { "#AAAAAA" },
                disabledColor = json.optString("disabledColor", "#555555").ifBlank { "#555555" },
                height = json.optDouble("height", 3.5).toFloat(),
                borderRadius = json.optDouble("borderRadius", 4.0).toFloat(),
                horizontalGap = json.optDouble("horizontalGap", 4.0).toFloat(),
                topPadding = json.optDouble("topPadding", 14.0).toFloat(),
                horizontalPadding = json.optDouble("horizontalPadding", 10.0).toFloat(),
            )
        }
    }
}

internal data class InlineStoryConfig(
    val slotKey: String,
    val defaultDuration: Int = 5000,
    val restartOnCompleted: Boolean = false,
    val card: StoryCardConfig = StoryCardConfig(),
    val indicator: StoryIndicatorDisplayConfig = StoryIndicatorDisplayConfig(),
    val items: List<StoryItemConfig>,
) {
    companion object {
        fun fromJson(json: JSONObject): InlineStoryConfig? {
            val slotKey = json.optString("slotKey", "").takeIf { it.isNotBlank() } ?: return null
            val itemsArr = json.optJSONArray("items") ?: return null
            val items = (0 until itemsArr.length())
                .mapNotNull { i -> itemsArr.optJSONObject(i)?.let { StoryItemConfig.fromJson(it) } }
            if (items.isEmpty()) return null
            return InlineStoryConfig(
                slotKey = slotKey,
                defaultDuration = json.optInt("defaultDuration", 5000).takeIf { it > 0 } ?: 5000,
                restartOnCompleted = json.optBoolean("restartOnCompleted", false),
                card = StoryCardConfig.fromJson(json.optJSONObject("card")),
                indicator = StoryIndicatorDisplayConfig.fromJson(json.optJSONObject("indicator")),
                items = items,
            )
        }
    }
}
