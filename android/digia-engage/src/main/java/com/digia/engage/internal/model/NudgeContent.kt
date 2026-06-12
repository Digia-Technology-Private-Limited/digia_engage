package com.digia.engage.internal.model

import org.json.JSONObject

// Mirror of Flutter nudge_content.dart — pure data, zero Android/Compose imports.

internal enum class NudgeSelfAlign {
    START, CENTER, END;
    companion object {
        fun from(v: String?): NudgeSelfAlign? = when (v) {
            "start" -> START; "center" -> CENTER; "end" -> END; else -> null
        }
    }
}

internal enum class NudgeTextAlign { LEFT, CENTER, RIGHT }

internal enum class NudgeButtonVariant { FILL, OUTLINE, TEXT, ELEVATED }

internal data class NudgeBox(
    val fillWidth: Boolean = false,
    val fixedWidth: Float? = null,
    val fixedHeight: Float? = null,
    val background: Int? = null,
    val paddingLeft: Float = 0f,
    val paddingTop: Float = 0f,
    val paddingRight: Float = 0f,
    val paddingBottom: Float = 0f,
    val marginLeft: Float = 0f,
    val marginTop: Float = 0f,
    val marginRight: Float = 0f,
    val marginBottom: Float = 0f,
    val borderRadius: Float = 0f,
    val borderColor: Int? = null,
    val borderWidth: Float = 0f,
    val selfAlign: NudgeSelfAlign? = null,
) {
    fun withoutFixedHeight() = copy(fixedHeight = null)
}

internal sealed class NudgeNode(open val box: NudgeBox)

internal data class NudgeText(
    override val box: NudgeBox,
    val text: String,
    val fontSize: Float,
    val fontWeight: Int,
    val color: Int,
    val align: NudgeTextAlign,
) : NudgeNode(box)

internal data class NudgeImage(
    override val box: NudgeBox,
    val url: String,
    val fit: String,
    val aspectRatio: Float,
) : NudgeNode(box)

// ── Button actions ────────────────────────────────────────────────────────────

internal sealed class NudgeAction
internal object DismissAction : NudgeAction()
internal data class OpenUrlAction(val url: String) : NudgeAction()
internal data class OpenDeeplinkAction(val url: String) : NudgeAction()
internal data class CopyToClipboardAction(val text: String) : NudgeAction()
internal data class ShareAction(val text: String) : NudgeAction()

internal class NudgeActionParser {
    fun parse(onClick: JSONObject?): List<NudgeAction> {
        val steps = onClick?.optJSONArray("steps") ?: return emptyList()
        return (0 until steps.length())
            .mapNotNull { steps.optJSONObject(it)?.let { step -> parseStep(step) } }
    }

    private fun parseStep(step: JSONObject): NudgeAction? {
        val data = step.optJSONObject("data") ?: JSONObject()
        return when (step.optString("type")) {
            "Action.openUrl" -> {
                val url = data.optString("url").takeIf { it.isNotBlank() } ?: return null
                if (data.optString("launchMode") == "externalApplication")
                    OpenUrlAction(url) else OpenDeeplinkAction(url)
            }
            "Action.copyToClipBoard" ->
                data.text()?.let { CopyToClipboardAction(it) }
            "Action.share" ->
                data.text()?.let { ShareAction(it) }
            "Action.hideBottomSheet", "Action.dismissDialog" -> DismissAction
            else -> null
        }
    }

    /** The action's text payload — canonical `message`, with `text`/`value` fallbacks. */
    private fun JSONObject.text(): String? =
        listOf("message", "text", "value")
            .firstNotNullOfOrNull { optString(it).takeIf { s -> s.isNotBlank() } }
}

// ─────────────────────────────────────────────────────────────────────────────

internal data class NudgeButton(
    override val box: NudgeBox,
    val label: String,
    val variant: NudgeButtonVariant,
    val fontSize: Float,
    val fontWeight: Int,
    val background: Int,
    val textColor: Int,
    val radius: Float,
    val isPrimary: Boolean,
    val actions: List<NudgeAction> = emptyList(),
) : NudgeNode(box)

internal data class NudgeGap(override val box: NudgeBox, val height: Float) : NudgeNode(box)

internal data class NudgeDivider(
    override val box: NudgeBox,
    val thickness: Float,
    val indent: Float,
    val endIndent: Float,
    val color: Int,
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
    val autoPlayInterval: Int,
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

internal data class NudgeColumn(
    val crossAxisAlignment: String = "start",
    val mainAxisAlignment: String = "start",
    val spacing: Float = 0f,
    val children: List<NudgeNode> = emptyList(),
)
