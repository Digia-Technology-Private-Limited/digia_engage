package com.digia.engage.internal

import com.digia.engage.internal.model.CampaignModel
import com.digia.engage.internal.model.GuideStepModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ActiveGuideState(
    val campaign: CampaignModel,
    val stepIndex: Int,
) {
    val currentStep: GuideStepModel
        get() = campaign.guideConfig!!.steps[stepIndex]

    val hasNext: Boolean
        get() = stepIndex < campaign.guideConfig!!.steps.lastIndex
}

internal class GuideOrchestrator {
    private val _state = MutableStateFlow<ActiveGuideState?>(null)
    val state: StateFlow<ActiveGuideState?> = _state.asStateFlow()

    fun start(campaign: CampaignModel) {
        require(campaign.campaignType == "guide" && campaign.guideConfig != null)
        require(campaign.guideConfig.steps.isNotEmpty())
        android.util.Log.d("Digia", "[GuideOrchestrator] starting campaign='${campaign.campaignKey}' steps=${campaign.guideConfig.steps.size}")
        _state.value = ActiveGuideState(campaign, 0)
        android.util.Log.d("Digia", "[GuideOrchestrator] state set → step[0] anchorKey='${campaign.guideConfig.steps[0].anchorKey}'")
    }

    fun advance() {
        val current = _state.value ?: return
        _state.value = if (current.hasNext) current.copy(stepIndex = current.stepIndex + 1)
        else null
    }

    fun dismiss() { _state.value = null }
}
