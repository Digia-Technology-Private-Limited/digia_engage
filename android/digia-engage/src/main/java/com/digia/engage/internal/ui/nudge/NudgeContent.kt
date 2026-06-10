package com.digia.engage.internal.ui.nudge

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

// ─── shared box decoration ────────────────────────────────────────────────────

internal data class NudgeBox(
    val fillWidth: Boolean = false,
    val fixedWidth: Float? = null,
    val fixedHeight: Float? = null,
    val background: Color? = null,
    val paddingLeft: Float = 0f,
    val paddingTop: Float = 0f,
    val paddingRight: Float = 0f,
    val paddingBottom: Float = 0f,
    val marginLeft: Float = 0f,
    val marginTop: Float = 0f,
    val marginRight: Float = 0f,
    val marginBottom: Float = 0f,
    val borderRadius: Float = 0f,
    val borderColor: Color? = null,
    val borderWidth: Float = 0f,
    val selfAlign: NudgeSelfAlign? = null,
) {
    fun withoutFixedHeight() = copy(fixedHeight = null)

    companion object {
        val NONE = NudgeBox()
    }
}

internal enum class NudgeSelfAlign { START, CENTER, END }
internal enum class NudgeButtonVariant { FILL, OUTLINE, TEXT, ELEVATED }
internal enum class NudgeCrossAxisAlignment { START, CENTER, END }
internal enum class NudgeMainAxisAlignment { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }

// ─── sealed node hierarchy ────────────────────────────────────────────────────

internal sealed class NudgeNode(open val box: NudgeBox)

internal data class NudgeText(
    override val box: NudgeBox,
    val text: String,
    val fontSize: Float,
    val fontWeight: FontWeight,
    val color: Color,
    val textAlign: TextAlign,
) : NudgeNode(box)

internal data class NudgeImage(
    override val box: NudgeBox,
    val url: String,
    val fit: ContentScale,
    val aspectRatio: Float,
) : NudgeNode(box)

internal data class NudgeButton(
    override val box: NudgeBox,
    val label: String,
    val variant: NudgeButtonVariant,
    val fontSize: Float,
    val fontWeight: FontWeight,
    val background: Color,
    val textColor: Color,
    val radius: Float,
    val actions: List<NudgeAction>,
    val isPrimary: Boolean,
) : NudgeNode(box)

internal data class NudgeGap(
    override val box: NudgeBox,
    val height: Float,
) : NudgeNode(box)

internal data class NudgeDivider(
    override val box: NudgeBox,
    val thickness: Float,
    val indent: Float,
    val endIndent: Float,
    val color: Color,
) : NudgeNode(box)

internal data class NudgeLottie(
    override val box: NudgeBox,
    val url: String,
    val height: Float,
    val loop: Boolean,
    val autoplay: Boolean,
) : NudgeNode(box)

internal data class NudgeCarousel(
    override val box: NudgeBox,
    val images: List<String>,
    val height: Float,
    val autoPlay: Boolean,
    val autoPlayIntervalMs: Int,
    val loop: Boolean,
    val showIndicator: Boolean,
) : NudgeNode(box)

internal data class NudgeVideo(
    override val box: NudgeBox,
    val url: String,
    val height: Float,
    val autoplay: Boolean,
    val loop: Boolean,
    val showControls: Boolean,
    val muted: Boolean,
) : NudgeNode(box)

// ─── root column ──────────────────────────────────────────────────────────────

internal data class NudgeColumn(
    val crossAxisAlignment: NudgeCrossAxisAlignment,
    val mainAxisAlignment: NudgeMainAxisAlignment,
    val spacing: Float,
    val children: List<NudgeNode>,
)
