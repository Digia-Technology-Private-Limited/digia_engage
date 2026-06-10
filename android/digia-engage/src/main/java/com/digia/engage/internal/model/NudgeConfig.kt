package com.digia.engage.internal.model

import com.digia.engage.internal.ui.nudge.NudgeColumn
import com.digia.engage.internal.ui.nudge.NudgeParser
import org.json.JSONObject

internal enum class NudgeTemplateType {
    BOTTOM_SHEET,
    DIALOG;

    companion object {
        fun fromString(value: String?): NudgeTemplateType = when (value) {
            "dialog" -> DIALOG
            else -> BOTTOM_SHEET
        }
    }

    val displayStyle: String
        get() = when (this) {
            BOTTOM_SHEET -> "bottom_sheet"
            DIALOG -> "dialog"
        }
}

internal data class NudgeContainerConfig(
    val bgColor: String = "#FFFFFF",
    val cornerRadius: Float = 16f,
    val padding: Float = 16f,
    val dismissOnOutsideTap: Boolean = true,
    val scrimColor: String = "#66000000",
    val maxHeightRatio: Float = 0.7f,
    val dragHandle: Boolean = true,
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

internal data class NudgeConfig(
    val templateType: NudgeTemplateType,
    val container: NudgeContainerConfig,
    val layout: NudgeColumn,
) {
    companion object {
        fun fromJson(json: JSONObject): NudgeConfig? {
            val layout = NudgeParser().parse(json) ?: return null
            return NudgeConfig(
                templateType = NudgeTemplateType.fromString(json.optString("templateType")),
                container = NudgeContainerConfig.fromJson(json.optJSONObject("container")),
                layout = layout,
            )
        }
    }
}
