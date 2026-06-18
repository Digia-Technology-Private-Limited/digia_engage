package com.digia.engage.internal

import com.digia.engage.CEPTriggerPayload
import com.digia.engage.internal.logging.Logger
import com.digia.engage.internal.model.InlineCarouselConfig
import com.digia.engage.internal.model.InlineStoryConfig
import com.digia.engage.internal.model.NudgeConfig

/**
 * Routes resolved campaigns onto the overlay controller's state flows. Pure presentation wiring —
 * event emission (CEP / analytics) lives in [com.digia.engage.internal.event.EngageEventEmitter],
 * owned by [DigiaInstance].
 */
internal class DisplayCoordinator(
        private val overlayController: DigiaOverlayController,
) {
    fun routeNudge(
            config: NudgeConfig,
            payload: CEPTriggerPayload,
            variables: Map<String, String> = emptyMap()
    ) {
        Logger.verbose(
                "Showing nudge: id=${payload.cepCampaignId} displayStyle=${config.surface.displayType}"
        )
        overlayController.showNudge(config, payload, variables)
    }

    fun routeInline(placementKey: String, payload: CEPTriggerPayload) {
        Logger.verbose("Showing inline: id=${payload.cepCampaignId} slotKey=$placementKey")
        overlayController.addSlot(placementKey, payload)
    }

    fun routeInlineCarousel(config: InlineCarouselConfig, payload: CEPTriggerPayload) {
        Logger.verbose(
                "Showing inline carousel: id=${payload.cepCampaignId} slotKey=${config.slotKey}"
        )
        overlayController.addSlotConfig(config.slotKey, config)
        overlayController.addSlot(config.slotKey, payload)
    }

    fun routeInlineStory(config: InlineStoryConfig, payload: CEPTriggerPayload) {
        Logger.verbose(
                "Showing inline story: id=${payload.cepCampaignId} slotKey=${config.slotKey}"
        )
        overlayController.addStorySlotConfig(config.slotKey, config)
        overlayController.addSlot(config.slotKey, payload)
    }

    fun dismissNudge(campaignId: String) {
        if (overlayController.nudgeOverlay.value?.payload?.cepCampaignId == campaignId) {
            Logger.verbose("Dismissing nudge: id=$campaignId")
            overlayController.dismissNudge()
        }
    }

    fun dismissInline(campaignId: String) {
        overlayController.removeSlotById(campaignId)
    }
}
