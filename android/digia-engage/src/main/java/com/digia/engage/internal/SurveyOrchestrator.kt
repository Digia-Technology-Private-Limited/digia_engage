package com.digia.engage.internal

import com.digia.engage.internal.model.CampaignModel
import com.digia.engage.internal.model.SurveyConfigModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The survey campaign currently routed for display. [token] is unique per
 * showing so the renderer can key a fresh in-progress state to it.
 */
internal data class ActiveSurveyState(
    val campaign: CampaignModel,
    val token: Long,
) {
    val config: SurveyConfigModel
        get() = campaign.surveyConfig!!
}

/**
 * Holds the active survey, mirroring [GuideOrchestrator]. The in-progress answer
 * state lives in the renderer's `SurveyViewModel`; this only tracks which survey
 * (if any) is on screen.
 */
internal class SurveyOrchestrator {
    private val _state = MutableStateFlow<ActiveSurveyState?>(null)
    val state: StateFlow<ActiveSurveyState?> = _state.asStateFlow()

    private var tokenCounter = 0L

    /** @return true if the survey was started, false if preconditions fail or one is already showing. */
    fun start(campaign: CampaignModel): Boolean {
        val surveyConfig = campaign.surveyConfig
        if (campaign.campaignType != "survey" || surveyConfig == null) return false
        if (surveyConfig.nodes.isEmpty() || surveyConfig.blocks.isEmpty()) return false
        if (_state.value != null) return false
        _state.value = ActiveSurveyState(campaign, ++tokenCounter)
        return true
    }

    fun dismiss() {
        _state.value = null
    }
}
