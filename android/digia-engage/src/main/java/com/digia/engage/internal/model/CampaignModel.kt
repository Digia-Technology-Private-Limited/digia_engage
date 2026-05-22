package com.digia.engage.internal.model

import org.json.JSONObject

data class CampaignModel(
    val id: String,
    val campaignKey: String,
    val campaignType: String,
    val guideConfig: GuideConfigModel?,
    val surveyConfig: SurveyConfigModel? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): CampaignModel? {
            val id = json.optString("id", "")
                .ifBlank { json.optString("_id", "") }
                .takeIf { it.isNotBlank() } ?: return null
            val campaignKey = json.optString("campaign_key", "").takeIf { it.isNotBlank() } ?: return null
            val campaignType = json.optString("campaign_type", "").takeIf { it.isNotBlank() } ?: return null

            val guideConfig = json.optJSONObject("guide_config")?.let { gc ->
                val gcId = gc.optString("id", "")
                    .ifBlank { gc.optString("_id", "") }
                    .ifBlank { id }
                val multiStep = gc.optBoolean("multi_step", false)
                val stepsArr = gc.optJSONArray("steps") ?: return@let null
                val steps = mutableListOf<GuideStepModel>()
                for (i in 0 until stepsArr.length()) {
                    val s = stepsArr.optJSONObject(i) ?: continue
                    val stepId = s.optString("id", "").ifBlank { s.optString("_id", "") }
                    val anchorKey = s.optString("anchor_key", "").takeIf { it.isNotBlank() } ?: continue
                    val widgetJson = s.optJSONObject("widget_config") ?: continue
                    android.util.Log.d("Digia", "[CampaignModel] step anchorKey='$anchorKey' widget_config=$widgetJson")
                    val widgetConfig = GuideStepWidgetConfig.fromJson(widgetJson)
                    steps.add(
                        GuideStepModel(
                            id = stepId,
                            sequenceOrder = s.optInt("sequence_order", i),
                            anchorKey = anchorKey,
                            displayStyle = s.optString("display_style", "tooltip"),
                            widgetConfig = widgetConfig,
                            advanceTrigger = s.optString("advance_trigger", "tap"),
                            autoDelayMs = if (s.has("auto_delay_ms")) s.optInt("auto_delay_ms") else null,
                        )
                    )
                }
                if (steps.isEmpty()) null
                else GuideConfigModel(id = gcId, multiStep = multiStep, steps = steps.sortedBy { it.sequenceOrder })
            }

            // Survey campaigns carry a `survey_config` instead of `guide_config`.
            val surveyConfig = json.optJSONObject("survey_config")
                ?.let { SurveyConfigModel.fromJson(it, fallbackId = id) }

            return CampaignModel(
                id = id,
                campaignKey = campaignKey,
                campaignType = campaignType,
                guideConfig = guideConfig,
                surveyConfig = surveyConfig,
            )
        }
    }
}
