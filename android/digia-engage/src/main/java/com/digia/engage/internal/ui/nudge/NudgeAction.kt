package com.digia.engage.internal.ui.nudge

internal sealed class NudgeAction

internal data class OpenUrlAction(val url: String) : NudgeAction()
internal data class OpenDeeplinkAction(val url: String) : NudgeAction()
internal object DismissAction : NudgeAction()

internal class NudgeActionParser {
    fun parse(onClick: org.json.JSONObject?): List<NudgeAction> {
        val steps = onClick?.optJSONArray("steps") ?: return emptyList()
        return (0 until steps.length())
            .mapNotNull { steps.optJSONObject(it)?.let { step -> parseStep(step) } }
    }

    private fun parseStep(step: org.json.JSONObject): NudgeAction? {
        val data = step.optJSONObject("data") ?: org.json.JSONObject()
        return when (step.optString("type")) {
            "Action.openUrl" -> {
                val url = data.optString("url").takeIf { it.isNotBlank() } ?: return null
                if (data.optString("launchMode") == "externalApplication")
                    OpenUrlAction(url) else OpenDeeplinkAction(url)
            }
            "Action.hideBottomSheet", "Action.dismissDialog" -> DismissAction
            else -> null
        }
    }
}
