package com.digia.digiaui.framework.story

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for Story progress indicator styling. Mirrors Flutter's StoryViewIndicatorConfig
 * from story_view_indicator_config.dart
 */
data class StoryIndicatorConfig(
        /** Color of the active/current progress bar */
        val activeColor: Color = Color.Blue,

        /** Color of completed (visited) progress bars */
        val completedColor: Color = Color.White,

        /** Color of disabled (not yet visited) progress bars */
        val disabledColor: Color = Color.Gray,

        /** Height of each progress bar */
        val height: Dp = 3.5.dp,

        /** Corner radius of progress bars */
        val borderRadius: Dp = 4.dp,

        /** Horizontal gap between progress bars */
        val horizontalGap: Dp = 4.dp,

        /** Alignment of the indicator within the story */
        val alignment: Alignment = Alignment.TopCenter,

        /** Top padding for the indicator */
        val topPadding: Dp = 14.dp,

        /** Horizontal padding for the indicator */
        val horizontalPadding: Dp = 10.dp,

        /** Bottom padding for the indicator */
        val bottomPadding: Dp = 0.dp
) {
    companion object {
        /** Create config from JSON-like map with pre-resolved colors. */
        fun fromJson(
                json: Map<String, Any?>?,
                activeColor: Color? = null,
                completedColor: Color? = null,
                disabledColor: Color? = null
        ): StoryIndicatorConfig {
            if (json == null) return StoryIndicatorConfig()

            // Parse margin string format: "top,left,bottom,right" or "14,10,0,10"
            val margin = parseMargin(json["margin"] as? String)

            return StoryIndicatorConfig(
                    activeColor = activeColor ?: Color.Blue,
                    completedColor = completedColor ?: Color.White,
                    disabledColor = disabledColor ?: Color.Gray,
                    height = (json["height"] as? Number)?.toFloat()?.dp ?: 3.5.dp,
                    borderRadius = (json["borderRadius"] as? Number)?.toFloat()?.dp ?: 4.dp,
                    horizontalGap = (json["horizontalGap"] as? Number)?.toFloat()?.dp ?: 4.dp,
                    alignment = parseAlignment(json["alignment"] as? String),
                    topPadding = margin?.top ?: 14.dp,
                    horizontalPadding = margin?.horizontal ?: 10.dp,
                    bottomPadding = margin?.bottom ?: 0.dp
            )
        }

        private fun parseAlignment(value: String?): Alignment {
            return when (value?.lowercase()) {
                "topleft", "topstart" -> Alignment.TopStart
                "topcenter" -> Alignment.TopCenter
                "topright", "topend" -> Alignment.TopEnd
                "centerleft", "centerstart" -> Alignment.CenterStart
                "center" -> Alignment.Center
                "centerright", "centerend" -> Alignment.CenterEnd
                "bottomleft", "bottomstart" -> Alignment.BottomStart
                "bottomcenter" -> Alignment.BottomCenter
                "bottomright", "bottomend" -> Alignment.BottomEnd
                else -> Alignment.TopCenter
            }
        }

        /** Parse margin in format "top,left,bottom,right" */
        private fun parseMargin(value: String?): MarginValues? {
            if (value.isNullOrBlank()) return null
            val parts = value.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size < 4) return null
            return MarginValues(
                    top = parts[0].dp,
                    horizontal = parts[1].dp, // left
                    bottom = parts[2].dp
                    // parts[3] is right, using same as left for horizontal
                    )
        }
    }
}

private data class MarginValues(val top: Dp, val horizontal: Dp, val bottom: Dp)
