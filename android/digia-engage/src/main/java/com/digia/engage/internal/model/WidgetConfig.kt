package com.digia.engage.internal.model

import android.graphics.Color
import org.json.JSONObject

// ── Action types ──────────────────────────────────────────────────────────────

enum class ActionType { DISMISS, NEXT, PREV }

data class GuideAction(
    val id: String,
    val label: String,
    val style: String,          // "filled" | "ghost"
    val actionType: ActionType,
    val backgroundColor: Int,
    val textColor: Int,
    val cornerRadius: Float,
)

// ── Arrow config ──────────────────────────────────────────────────────────────

data class ArrowConfig(
    val visible: Boolean,
    val preferredDirection: String, // "top"|"bottom"|"start"|"end"|"auto"
    val size: Int,
    val color: Int,
)

// ── Bubble config ─────────────────────────────────────────────────────────────

data class BubbleConfig(
    val backgroundColor: Int,
    val cornerRadius: Float,
    val paddingHorizontal: Float,
    val paddingVertical: Float,
    val maxWidthDp: Float,
    val elevation: Float,
    val entranceAnimation: String, // "elastic"|"circular"|"fade"|"overshoot"|"none"
    val arrow: ArrowConfig,
)

// ── Cutout config ─────────────────────────────────────────────────────────────

data class CutoutConfig(
    val shape: String,          // "rounded_rect"|"rect"|"circle"
    val cornerRadius: Float,
    val padding: Float,
)

// ── Overlay config ────────────────────────────────────────────────────────────

data class OverlayConfig(
    val visible: Boolean,       // false = tooltip, true = spotlight
    val color: Int,
    val alpha: Float,
    val dismissOnTap: Boolean,
    val entranceAnimation: String, // "fade"|"none"
    val cutout: CutoutConfig,
)

// ── Content text ──────────────────────────────────────────────────────────────

data class TextContent(
    val text: String,
    val fontFamily: String,
    val fontWeight: String,
    val fontSize: Float,
    val textColor: Int,
)

data class StepIndicatorConfig(
    val visible: Boolean,
    val color: Int,
)

data class ContentConfig(
    val title: TextContent?,
    val body: TextContent?,
    val mediaUrl: String?,
    val stepIndicator: StepIndicatorConfig,
)

// ── Top-level widget config ───────────────────────────────────────────────────

data class GuideStepWidgetConfig(
    val bubble: BubbleConfig,
    val overlay: OverlayConfig,
    val content: ContentConfig,
    val actions: List<GuideAction>,
) {
    companion object {

        // Defaults
        private val DEFAULT_BUBBLE_BG    = Color.parseColor("#1E40AF")
        private val DEFAULT_ARROW_COLOR  = Color.parseColor("#1E40AF")
        private val DEFAULT_OVERLAY      = Color.parseColor("#000000")
        private val DEFAULT_STEP_COLOR   = Color.parseColor("#FFFFFFAA")
        private val DEFAULT_BTN_BG       = Color.parseColor("#FFFFFF")
        private val DEFAULT_BTN_TEXT     = Color.parseColor("#1E40AF")
        private val DEFAULT_BODY_COLOR   = Color.parseColor("#FFFFFFCC")
        private val DEFAULT_TITLE_COLOR  = Color.parseColor("#FFFFFF")

        fun fromJson(json: JSONObject): GuideStepWidgetConfig {
            val bubbleObj  = json.optJSONObject("bubble")  ?: JSONObject()
            val overlayObj = json.optJSONObject("overlay") ?: JSONObject()
            val contentObj = json.optJSONObject("content") ?: JSONObject()

            // ── arrow ──────────────────────────────────────────────────────────
            val arrowObj = bubbleObj.optJSONObject("arrow") ?: JSONObject()
            val arrow = ArrowConfig(
                visible            = arrowObj.optBoolean("visible", true),
                preferredDirection = arrowObj.optString("preferred_direction", "auto"),
                size               = arrowObj.optInt("size", 10),
                color              = parseColor(arrowObj.optString("color"), DEFAULT_ARROW_COLOR),
            )

            // ── bubble ─────────────────────────────────────────────────────────
            val bubble = BubbleConfig(
                backgroundColor    = parseColor(bubbleObj.optString("background_color"), DEFAULT_BUBBLE_BG),
                cornerRadius       = bubbleObj.optDouble("corner_radius", 12.0).toFloat(),
                paddingHorizontal  = bubbleObj.optDouble("padding_horizontal", 16.0).toFloat(),
                paddingVertical    = bubbleObj.optDouble("padding_vertical", 12.0).toFloat(),
                maxWidthDp         = bubbleObj.optDouble("max_width", 280.0).toFloat(),
                elevation          = bubbleObj.optDouble("elevation", 6.0).toFloat(),
                entranceAnimation  = bubbleObj.optString("entrance_animation", "elastic"),
                arrow              = arrow,
            )

            // ── cutout ─────────────────────────────────────────────────────────
            val cutoutObj = overlayObj.optJSONObject("cutout") ?: JSONObject()
            val cutout = CutoutConfig(
                shape        = cutoutObj.optString("shape", "rounded_rect"),
                cornerRadius = cutoutObj.optDouble("corner_radius", 12.0).toFloat(),
                padding      = cutoutObj.optDouble("padding", 8.0).toFloat(),
            )

            // ── overlay ────────────────────────────────────────────────────────
            val overlay = OverlayConfig(
                visible            = overlayObj.optBoolean("visible", false),
                color              = parseColor(overlayObj.optString("color"), DEFAULT_OVERLAY),
                alpha              = overlayObj.optDouble("alpha", 0.6).toFloat(),
                dismissOnTap       = overlayObj.optBoolean("dismiss_on_tap", false),
                entranceAnimation  = overlayObj.optString("entrance_animation", "fade"),
                cutout             = cutout,
            )

            // ── content ────────────────────────────────────────────────────────
            val titleObj = contentObj.optJSONObject("title")
            val bodyObj  = contentObj.optJSONObject("body")
            val mediaObj = contentObj.optJSONObject("media")
            val stepIndObj = contentObj.optJSONObject("step_indicator") ?: JSONObject()

            // Support legacy flat schema: "title"/"body" as top-level strings
            val titleText = titleObj?.optString("text")
                ?: json.optString("title", "").takeIf { it.isNotBlank() }
            val bodyText = bodyObj?.optString("text")
                ?: json.optString("body", "").takeIf { it.isNotBlank() }

            val title = titleText?.let { text ->
                val ts = titleObj?.optJSONObject("textStyle") ?: JSONObject()
                val font = ts.optJSONObject("fontToken")?.optJSONObject("font") ?: JSONObject()
                TextContent(
                    text       = text,
                    fontFamily = font.optString("fontFamily", ""),
                    fontWeight = font.optString("weight", "bold"),
                    fontSize   = font.optDouble("size", 16.0).toFloat(),
                    textColor  = parseColor(ts.optString("textColor"), DEFAULT_TITLE_COLOR),
                )
            }

            val body = bodyText?.let { text ->
                val ts = bodyObj?.optJSONObject("textStyle") ?: JSONObject()
                val font = ts.optJSONObject("fontToken")?.optJSONObject("font") ?: JSONObject()
                TextContent(
                    text       = text,
                    fontFamily = font.optString("fontFamily", ""),
                    fontWeight = font.optString("weight", "regular"),
                    fontSize   = font.optDouble("size", 14.0).toFloat(),
                    textColor  = parseColor(ts.optString("textColor"), DEFAULT_BODY_COLOR),
                )
            }

            val content = ContentConfig(
                title    = title,
                body     = body,
                mediaUrl = mediaObj?.optString("url"),
                stepIndicator = StepIndicatorConfig(
                    visible = stepIndObj.optBoolean("visible", false),
                    color   = parseColor(stepIndObj.optString("color"), DEFAULT_STEP_COLOR),
                ),
            )

            // ── actions ────────────────────────────────────────────────────────
            val actionsArr = json.optJSONArray("actions")
                ?: contentObj.optJSONArray("actions")
            val actions = mutableListOf<GuideAction>()
            if (actionsArr != null) {
                for (i in 0 until actionsArr.length()) {
                    val obj = actionsArr.optJSONObject(i) ?: continue
                    // Support both "action_type" (new schema) and "type" (legacy)
                    val typeStr = obj.optString("action_type", "")
                        .ifBlank { obj.optString("type", "dismiss") }
                        .uppercase()
                    val type = try { ActionType.valueOf(typeStr) } catch (_: Exception) { ActionType.DISMISS }
                    actions.add(
                        GuideAction(
                            id              = obj.optString("id", "btn_$i"),
                            label           = obj.optString("label", ""),
                            style           = obj.optString("style", "filled"),
                            actionType      = type,
                            backgroundColor = parseColor(obj.optString("background_color"), DEFAULT_BTN_BG),
                            textColor       = parseColor(obj.optString("text_color"), DEFAULT_BTN_TEXT),
                            cornerRadius    = obj.optDouble("corner_radius", 8.0).toFloat(),
                        )
                    )
                }
            }

            return GuideStepWidgetConfig(
                bubble  = bubble,
                overlay = overlay,
                content = content,
                actions = actions,
            )
        }

        private fun parseColor(value: String?, default: Int): Int {
            if (value.isNullOrBlank()) return default
            return try { Color.parseColor(value) } catch (_: Exception) { default }
        }
    }
}
