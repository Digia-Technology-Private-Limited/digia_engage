package com.digia.engage.internal

import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.internal.model.InlineCarouselConfig

internal class DisplayCoordinator(
    private val overlayController: DigiaOverlayController,
    private val pluginRegistry: PluginRegistry,
    private val analyticsClient: AnalyticsClient,
) {
    fun routeNudge(payload: InAppPayload) {
        overlayController.show(payload)
    }

    fun routeInline(placementKey: String, payload: InAppPayload) {
        overlayController.addSlot(placementKey, payload)
    }

    fun routeInlineCarousel(config: InlineCarouselConfig, payload: InAppPayload) {
        overlayController.addSlotConfig(config.slotKey, config)
        overlayController.addSlot(config.slotKey, payload)
    }

    fun dismissNudge(campaignId: String) {
        if (overlayController.activePayload.value?.id == campaignId) {
            overlayController.dismiss()
        }
    }

    fun dismissInline(campaignId: String) {
        overlayController.removeSlotById(campaignId)
    }

    fun onOverlayEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        pluginRegistry.notifyEvent(event, payload)
        analyticsClient.track(event, payload)
    }

    fun onSlotEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        pluginRegistry.notifyEvent(event, payload)
        analyticsClient.track(event, payload)
    }

    /** Forward only to the active CEP plugin — used for survey Clicked/Dismissed. */
    fun notifyCEP(event: DigiaExperienceEvent, payload: InAppPayload) {
        pluginRegistry.notifyEvent(event, payload)
    }

    /** Record only into internal analytics — used for survey Answered/Completed. */
    fun trackInternal(event: InternalEngageEvent, payload: InAppPayload) {
        analyticsClient.trackInternal(event, payload)
    }
}
