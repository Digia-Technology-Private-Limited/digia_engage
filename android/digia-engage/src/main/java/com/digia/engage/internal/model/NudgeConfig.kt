package com.digia.engage.internal.model

import com.digia.engage.framework.models.VWData
import org.json.JSONArray
import org.json.JSONObject

/** Whether a nudge presents as a bottom sheet or a centered dialog. */
internal enum class NudgeTemplateType {
    BOTTOM_SHEET,
    DIALOG;

    companion object {
        fun fromString(value: String?): NudgeTemplateType = when (value) {
            "dialog" -> DIALOG
            else -> BOTTOM_SHEET
        }
    }

    /** Analytics `display_style` value carried alongside nudge events. */
    val displayStyle: String
        get() = when (this) {
            BOTTOM_SHEET -> "bottom_sheet"
            DIALOG -> "dialog"
        }
}

/**
 * Container chrome around the rendered widget tree. All fields are optional on the
 * wire and fall back to the defaults below.
 */
internal data class NudgeContainerConfig(
    val bgColor: String = "#FFFFFF",
    val cornerRadius: Float = 16f,
    val padding: Float = 16f,
    val dismissOnOutsideTap: Boolean = true,
    val scrimColor: String = "#66000000",
    /** Bottom-sheet only: max height as a fraction of screen height. */
    val maxHeightRatio: Float = 0.7f,
    /** Bottom-sheet only: show the drag handle + enable drag-to-dismiss. */
    val dragHandle: Boolean = true,
    /** Dialog only: width in dp; null = use a sensible default width. */
    val width: Float? = null,
) {
    companion object {
        fun fromJson(json: JSONObject?): NudgeContainerConfig {
            if (json == null) return NudgeContainerConfig()
            val defaults = NudgeContainerConfig()
            return NudgeContainerConfig(
                bgColor = json.optString("bgColor", defaults.bgColor),
                cornerRadius = json.optDouble("cornerRadius", defaults.cornerRadius.toDouble()).toFloat(),
                padding = json.optDouble("padding", defaults.padding.toDouble()).toFloat(),
                dismissOnOutsideTap = json.optBoolean("dismissOnOutsideTap", defaults.dismissOnOutsideTap),
                scrimColor = json.optString("scrimColor", defaults.scrimColor),
                maxHeightRatio = json.optDouble("maxHeightRatio", defaults.maxHeightRatio.toDouble()).toFloat(),
                dragHandle = json.optBoolean("dragHandle", defaults.dragHandle),
                width = if (json.has("width") && !json.isNull("width")) {
                    json.optDouble("width").toFloat()
                } else {
                    defaults.width
                },
            )
        }
    }
}

/**
 * Parsed `templateConfig` for a `nudge` campaign. [layout] is the exact native DUI
 * `VWData` tree (root `digia/column`); it is fed straight into the recursive renderer.
 */
internal data class NudgeConfig(
    val templateType: NudgeTemplateType,
    val container: NudgeContainerConfig,
    val layout: VWData,
) {
    companion object {
        fun fromJson(json: JSONObject): NudgeConfig? {
            val layoutJson = json.optJSONObject("layout") ?: return null
            val layout = VWData.fromJson(layoutJson.toJsonLike())
            return NudgeConfig(
                templateType = NudgeTemplateType.fromString(json.optString("templateType")),
                container = NudgeContainerConfig.fromJson(json.optJSONObject("container")),
                layout = layout,
            )
        }
    }
}

/** Recursively convert a [JSONObject] to the `Map<String, Any?>` shape `VWData.fromJson` expects. */
private fun JSONObject.toJsonLike(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = unwrapJson(get(key))
    }
    return map
}

private fun JSONArray.toJsonList(): List<Any?> =
    (0 until length()).map { unwrapJson(get(it)) }

private fun unwrapJson(value: Any?): Any? = when (value) {
    is JSONObject -> value.toJsonLike()
    is JSONArray -> value.toJsonList()
    JSONObject.NULL -> null
    else -> value
}
