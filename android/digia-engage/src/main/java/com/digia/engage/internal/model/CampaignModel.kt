package com.digia.engage.internal.model

import com.digia.engage.internal.VariableSchema
import com.digia.engage.internal.frequency.FrequencyManager
import com.digia.engage.internal.frequency.FrequencyPolicy
import com.digia.engage.internal.normalizeVariable
import org.json.JSONObject

internal sealed interface CampaignConfigModel {
        data class Guide(val guideConfig: GuideConfigModel) : CampaignConfigModel
        data class Nudge(val nudgeConfig: NudgeConfig) : CampaignConfigModel
        data class Inline(val inlineConfig: InlineCarouselConfig) : CampaignConfigModel
        data class Story(val storyConfig: InlineStoryConfig) : CampaignConfigModel
        data class Survey(val surveyConfig: SurveyConfigModel) : CampaignConfigModel
}

internal data class CampaignModel(
        val id: String,
        val campaignKey: String,
        val campaignType: String,
        val config: CampaignConfigModel,
        val variableSchemas: List<VariableSchema> = emptyList(),
        val frequency: FrequencyPolicy? = null,
) {
        val guideConfig: GuideConfigModel?
                get() = (config as? CampaignConfigModel.Guide)?.guideConfig

    val nudgeConfig: NudgeConfig?
        get() = (config as? CampaignConfigModel.Nudge)?.nudgeConfig

        val surveyConfig: SurveyConfigModel?
                get() = (config as? CampaignConfigModel.Survey)?.surveyConfig

        companion object {
                fun fromJson(json: JSONObject): CampaignModel? {
                        val id =
                                json.optString("id", "")
                                        .ifBlank { json.optString("_id", "") }
                                        .takeIf { it.isNotBlank() }
                                        ?: return null
                        val campaignKey =
                                json.optString("campaignKey", "").takeIf { it.isNotBlank() }
                                        ?: return null
                        val campaignType =
                                json.optString("campaignType", "").takeIf { it.isNotBlank() }
                                        ?: return null

                        val config =
                                when (campaignType) {
                                        "guide" ->
                                                CampaignConfigModel.Guide(
                                                        parseGuideConfig(json, fallbackId = id)
                                                                ?: error(
                                                                        "guide campaign '$campaignKey' has no valid guide_config"
                                                                )
                                                )
                                        "nudge" -> {
                    val templateConfig = json.optJSONObject("templateConfig")
                        ?: error("nudge campaign '$campaignKey' has no templateConfig")
                    CampaignConfigModel.Nudge(
                        NudgeConfig.fromJson(templateConfig)
                            ?: error("nudge campaign '$campaignKey' has no valid nudge templateConfig")
                    )
                }
                                        "inline" -> {
                                                val templateConfig =
                                                        json.optJSONObject("templateConfig")
                                                                ?: error(
                                                                        "inline campaign '$campaignKey' has no templateConfig"
                                                                )
                                                when (templateConfig.optString(
                                                                "templateType",
                                                                "carousel"
                                                        )
                                                ) {
                                                        "story" -> {
                                                                val schemas = parseVariableSchemas(templateConfig)
                                                                val storyCfg = InlineStoryConfig.fromJson(templateConfig)
                                                                        ?: error("story campaign '$campaignKey' has no valid templateConfig")
                                                                CampaignConfigModel.Story(storyCfg.copy(variableSchemas = schemas))
                                                        }
                                                        else ->
                                                                CampaignConfigModel.Inline(
                                                                        InlineCarouselConfig
                                                                                .fromJson(
                                                                                        templateConfig
                                                                                )
                                                                                ?: error(
                                                                                        "inline campaign '$campaignKey' has no valid carousel templateConfig"
                                                                                )
                                                                )
                                                }
                                        }
                                        "survey" ->
                                                CampaignConfigModel.Survey(
                                                        parseSurveyConfig(json, fallbackId = id)
                                                                ?: error(
                                                                        "survey campaign '$campaignKey' has no valid survey templateConfig"
                                                                )
                                                )
                                        else -> error("Unknown campaignType: $campaignType")
                                }

                        return CampaignModel(
                                id = id,
                                campaignKey = campaignKey,
                                campaignType = campaignType,
                                config = config,
                                variableSchemas = parseVariableSchemas(json.optJSONObject("templateConfig")),
                                frequency = FrequencyManager.parsePolicy(json.optJSONObject("frequency")),
                        )
                }

                private fun parseVariableSchemas(templateConfig: JSONObject?): List<VariableSchema> {
                        val raw = templateConfig?.opt("variables") ?: return emptyList()
                        val result = mutableListOf<VariableSchema>()
                        when (raw) {
                                // List format: [{name, type, fallbackValue, sampleValue}] — matches Flutter wire
                                is org.json.JSONArray -> {
                                        for (i in 0 until raw.length()) {
                                                val entry = raw.optJSONObject(i) ?: continue
                                                val schema = normalizeVariable(entry) ?: continue
                                                result += schema
                                        }
                                }
                                // Dict format: {key: value} — legacy or alternate representation
                                is JSONObject -> {
                                        raw.keys().forEach { k ->
                                                when (val v = raw.opt(k)) {
                                                        is String -> result += VariableSchema(k, "string", v)
                                                        is Number -> result += VariableSchema(k, "string", v.toString())
                                                        is Boolean -> result += VariableSchema(k, "string", v.toString())
                                                }
                                        }
                                }
                        }
                        return result
                }

                private fun parseGuideConfig(
                        json: JSONObject,
                        fallbackId: String
                ): GuideConfigModel? {
                        val guideJson = json.optJSONObject("guideConfig")
                        if (guideJson != null) return parseGuideSteps(guideJson, fallbackId)

                        val templateJson =
                                json.optJSONObject("templateConfig")?.takeIf {
                                        val templateType = it.optString("templateType")
                                        templateType == "tooltip" || templateType == "spotlight"
                                }
                        return templateJson?.let { parseFlatGuideTemplate(it, fallbackId) }
                }

                private fun parseGuideSteps(
                        guideJson: JSONObject,
                        fallbackId: String
                ): GuideConfigModel? {
                        val guideId =
                                guideJson
                                        .optString("id", "")
                                        .ifBlank { guideJson.optString("_id", "") }
                                        .ifBlank { fallbackId }
                        val stepsArr = guideJson.optJSONArray("steps") ?: return null
                        return buildGuideConfig(
                                guideId = guideId,
                                multiStep = guideJson.optBoolean("multiStep", false),
                                stepsArr = stepsArr,
                                displayStyle = null,
                                widgetJsonForStep = { stepJson ->
                                        stepJson.optJSONObject("widgetConfig")
                                },
                        )
                }

                private fun parseFlatGuideTemplate(
                        templateJson: JSONObject,
                        fallbackId: String
                ): GuideConfigModel? {
                        val stepsArr = templateJson.optJSONArray("steps") ?: return null
                        return buildGuideConfig(
                                guideId =
                                        templateJson.optString("templateId", "").ifBlank {
                                                fallbackId
                                        },
                                multiStep = stepsArr.length() > 1,
                                stepsArr = stepsArr,
                                displayStyle = templateJson.optString("templateType", "tooltip"),
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
                                val stepId =
                                        stepJson.optString("id", "").ifBlank {
                                                stepJson.optString("_id", "")
                                        }
                                val anchorKey =
                                        stepJson.optString("anchorKey", "").takeIf {
                                                it.isNotBlank()
                                        }
                                                ?: continue
                                val widgetJson = widgetJsonForStep(stepJson) ?: continue
                                steps.add(
                                        GuideStepModel(
                                                id = stepId,
                                                sequenceOrder = stepJson.optInt("sequenceOrder", i),
                                                anchorKey = anchorKey,
                                                displayStyle = displayStyle
                                                                ?: stepJson.optString(
                                                                        "displayStyle",
                                                                        "tooltip"
                                                                ),
                                                widgetConfig =
                                                        GuideStepWidgetConfig.fromJson(widgetJson),
                                                advanceTrigger =
                                                        stepJson.optString("advanceTrigger", "tap"),
                                                autoDelayMs =
                                                        if (stepJson.has("autoDelayMs")) {
                                                                stepJson.optInt("autoDelayMs")
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

                private fun parseSurveyConfig(
                        json: JSONObject,
                        fallbackId: String
                ): SurveyConfigModel? {
                        val surveyJson =
                                json.optJSONObject("surveyConfig")
                                        ?: json.optJSONObject("templateConfig")?.takeIf {
                                                it.optString("templateType") == "survey"
                                        }
                        return surveyJson?.let {
                                SurveyConfigModel.fromJson(it, fallbackId = fallbackId)
                        }
                }
        }
}
