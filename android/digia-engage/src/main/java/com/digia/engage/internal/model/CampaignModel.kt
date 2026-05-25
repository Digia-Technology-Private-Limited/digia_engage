package com.digia.engage.internal.model

import org.json.JSONObject

internal sealed interface CampaignConfigModel {
    data class Guide(val guideConfig: GuideConfigModel) : CampaignConfigModel
    object Nudge : CampaignConfigModel
    data class Inline(val inlineConfig: InlineCarouselConfig) : CampaignConfigModel
    data class Survey(val surveyConfig: SurveyConfigModel) : CampaignConfigModel
}

data class CampaignModel(
    val id: String,
    val campaignKey: String,
    val campaignType: String,
    val config: CampaignConfigModel,
) {
    val guideConfig: GuideConfigModel?
        get() = (config as? CampaignConfigModel.Guide)?.guideConfig

    val inlineConfig: InlineCarouselConfig?
        get() = (config as? CampaignConfigModel.Inline)?.inlineConfig

    val surveyConfig: SurveyConfigModel?
        get() = (config as? CampaignConfigModel.Survey)?.surveyConfig

    companion object {
        fun fromJson(json: JSONObject): CampaignModel? {
            val id = json.optString("id", "")
                .ifBlank { json.optString("_id", "") }
                .takeIf { it.isNotBlank() } ?: return null
            val campaignKey = json.optString("campaign_key", "")
                .takeIf { it.isNotBlank() } ?: return null
            val campaignType = json.optString("campaign_type", "")
                .takeIf { it.isNotBlank() } ?: return null

            val config = when (campaignType) {
                "guide" -> CampaignConfigModel.Guide(
                    parseGuideConfig(json, fallbackId = id)
                        ?: error("guide campaign '$campaignKey' has no valid guide_config")
                )
                "nudge" -> CampaignConfigModel.Nudge
                "inline" -> CampaignConfigModel.Inline(
                    json.optJSONObject("template_config")
                        ?.let(InlineCarouselConfig::fromJson)
                        ?: error("inline campaign '$campaignKey' has no valid carousel template_config")
                )
                "survey" -> CampaignConfigModel.Survey(
                    parseSurveyConfig(json, fallbackId = id)
                        ?: error("survey campaign '$campaignKey' has no valid survey template_config")
                )
                else -> error("Unknown campaign_type: $campaignType")
            }

            return CampaignModel(
                id = id,
                campaignKey = campaignKey,
                campaignType = campaignType,
                config = config,
            )
        }

        private fun parseGuideConfig(json: JSONObject, fallbackId: String): GuideConfigModel? {
            val guideJson = json.optJSONObject("guide_config")
            if (guideJson != null) return parseGuideSteps(guideJson, fallbackId)

            val templateJson = json.optJSONObject("template_config")?.takeIf {
                val templateType = it.optString("template_type")
                templateType == "tooltip" || templateType == "spotlight"
            }
            return templateJson?.let { parseFlatGuideTemplate(it, fallbackId) }
        }

        private fun parseGuideSteps(guideJson: JSONObject, fallbackId: String): GuideConfigModel? {
            val guideId = guideJson.optString("id", "")
                .ifBlank { guideJson.optString("_id", "") }
                .ifBlank { fallbackId }
            val stepsArr = guideJson.optJSONArray("steps") ?: return null
            return buildGuideConfig(
                guideId = guideId,
                multiStep = guideJson.optBoolean("multi_step", false),
                stepsArr = stepsArr,
                displayStyle = null,
                widgetJsonForStep = { stepJson -> stepJson.optJSONObject("widget_config") },
            )
        }

        private fun parseFlatGuideTemplate(templateJson: JSONObject, fallbackId: String): GuideConfigModel? {
            val stepsArr = templateJson.optJSONArray("steps") ?: return null
            return buildGuideConfig(
                guideId = templateJson.optString("template_id", "").ifBlank { fallbackId },
                multiStep = stepsArr.length() > 1,
                stepsArr = stepsArr,
                displayStyle = templateJson.optString("template_type", "tooltip"),
                widgetJsonForStep = { stepJson -> stepJson },
            )
        }

        private fun buildGuideConfig(
            guideId: String,
            multiStep: Boolean,
            stepsArr: org.json.JSONArray,
            displayStyle: String?,
            widgetJsonForStep: (JSONObject) -> JSONObject?,
        ): GuideConfigModel? {
            val steps = mutableListOf<GuideStepModel>()

            for (i in 0 until stepsArr.length()) {
                val stepJson = stepsArr.optJSONObject(i) ?: continue
                val stepId = stepJson.optString("id", "").ifBlank { stepJson.optString("_id", "") }
                val anchorKey = stepJson.optString("anchor_key", "")
                    .takeIf { it.isNotBlank() } ?: continue
                val widgetJson = widgetJsonForStep(stepJson) ?: continue
                android.util.Log.d(
                    "Digia",
                    "[CampaignModel] step anchorKey='$anchorKey' widget_config=$widgetJson",
                )
                steps.add(
                    GuideStepModel(
                        id = stepId,
                        sequenceOrder = stepJson.optInt("sequence_order", i),
                        anchorKey = anchorKey,
                        displayStyle = displayStyle ?: stepJson.optString("display_style", "tooltip"),
                        widgetConfig = GuideStepWidgetConfig.fromJson(widgetJson),
                        advanceTrigger = stepJson.optString("advance_trigger", "tap"),
                        autoDelayMs = if (stepJson.has("auto_delay_ms")) {
                            stepJson.optInt("auto_delay_ms")
                        } else {
                            null
                        },
                    )
                )
            }

            if (steps.isEmpty()) return null
            return GuideConfigModel(
                id = guideId,
                multiStep = multiStep,
                steps = steps.sortedBy { it.sequenceOrder },
            )
        }

        private fun parseSurveyConfig(json: JSONObject, fallbackId: String): SurveyConfigModel? {
            val surveyJson = json.optJSONObject("survey_config")
                ?: json.optJSONObject("template_config")?.takeIf {
                    it.optString("template_type") == "survey"
                }
            return surveyJson?.let { SurveyConfigModel.fromJson(it, fallbackId = fallbackId) }
        }
    }
}
