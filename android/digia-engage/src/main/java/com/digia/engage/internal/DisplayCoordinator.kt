package com.digia.engage.internal

import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload

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
}
