package com.digia.engage.internal.model

import org.json.JSONObject

internal enum class NudgeDisplayType {
    BOTTOM_SHEET,
    DIALOG;

    companion object {
        fun fromString(value: String?): NudgeDisplayType =
            if (value == "dialog") DIALOG else BOTTOM_SHEET
    }

    val displayStyle: String
        get() = when (this) {
            BOTTOM_SHEET -> "bottom_sheet"
            DIALOG -> "dialog"
        }
}

internal data class NudgeSurface(
    val displayType: NudgeDisplayType = NudgeDisplayType.BOTTOM_SHEET,
    val backgroundColor: String? = null,
    val barrierColor: String? = null,
    val cornerRadius: Float = 18f,
    val padding: Float = 20f,
    val backdropDismissible: Boolean = true,
    val showCloseButton: Boolean = false,
    val showHandle: Boolean = true,
    val draggable: Boolean = true,
    val widthFraction: Float = 0.86f,
) {
    companion object {
        fun fromJson(json: JSONObject?): NudgeSurface {
            val j = json ?: JSONObject()
            val widthPct = j.optDouble("widthPct", 86.0)
            return NudgeSurface(
                displayType = NudgeDisplayType.fromString(j.optString("displayType")),
                backgroundColor = j.optString("backgroundColor").ifBlank { null },
                barrierColor = j.optString("barrierColor").ifBlank { null },
                cornerRadius = j.optDouble("cornerRadius", 18.0).toFloat(),
                padding = j.optDouble("padding", 20.0).toFloat(),
                backdropDismissible = j.optBoolean("backdropDismissible", true),
                showCloseButton = j.optBoolean("showCloseButton", false),
                showHandle = j.optBoolean("showHandle", true),
                draggable = j.optBoolean("draggable", true),
                widthFraction = (widthPct / 100.0).toFloat().coerceIn(0.3f, 1.0f),
            )
        }
    }
}

internal data class NudgeConfig(
    val surface: NudgeSurface,
    val content: NudgeColumn,
) {
    companion object {
        fun fromJson(json: JSONObject): NudgeConfig? = NudgeParser().parse(json)
    }
}
