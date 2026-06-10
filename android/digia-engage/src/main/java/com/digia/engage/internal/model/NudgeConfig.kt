package com.digia.engage.internal.model

/** Whether a nudge presents as a bottom sheet or a centered dialog. */
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

/**
 * The presentation chrome for a nudge — mirrors Flutter NudgeSurface.
 * All fields match the dashboard wire format exactly.
 */
internal data class NudgeSurface(
    val displayType: NudgeDisplayType = NudgeDisplayType.BOTTOM_SHEET,
    val backgroundColor: Int? = null,
    val barrierColor: Int? = null,
    val cornerRadius: Float = 18f,
    val padding: Float = 20f,
    val backdropDismissible: Boolean = true,
    val showCloseButton: Boolean = false,
    val showHandle: Boolean = true,
    val draggable: Boolean = true,
    val widthFraction: Float = 0.86f,
)

/**
 * Parsed `templateConfig` for a nudge campaign.
 * [surface] is the presentation chrome; [content] is the typed widget tree.
 */
internal data class NudgeConfig(
    val surface: NudgeSurface,
    val content: NudgeColumn,
)
