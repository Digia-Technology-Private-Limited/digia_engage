package com.digia.engage.internal

import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.internal.analytics.AnalyticsService
import com.digia.engage.internal.model.InlineCarouselConfig
import com.digia.engage.internal.model.InlineStoryConfig

internal class DisplayCoordinator(
    private val overlayController: DigiaOverlayController,
    private val pluginRegistry: PluginRegistry,
    private val getAnalyticsService: () -> AnalyticsService?,
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

    fun routeInlineStory(config: InlineStoryConfig, payload: InAppPayload) {
        overlayController.addStorySlotConfig(config.slotKey, config)
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
        android.util.Log.d("DigiaAnalytics", "[DisplayCoordinator] onOverlayEvent: event=${event::class.simpleName} campaignKey=${payload.content["campaign_key"]} campaignId=${payload.content["campaign_id"]}")
        pluginRegistry.notifyEvent(event, payload)
        val svc = getAnalyticsService()
        if (svc == null) {
            android.util.Log.w("DigiaAnalytics", "[DisplayCoordinator] onOverlayEvent: analyticsService is NULL — event not captured")
        } else {
            svc.capture(event, payload)
        }
    }

    fun onSlotEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        android.util.Log.d("DigiaAnalytics", "[DisplayCoordinator] onSlotEvent: event=${event::class.simpleName} campaignKey=${payload.content["campaign_key"]} campaignId=${payload.content["campaign_id"]}")
        pluginRegistry.notifyEvent(event, payload)
        val svc = getAnalyticsService()
        if (svc == null) {
            android.util.Log.w("DigiaAnalytics", "[DisplayCoordinator] onSlotEvent: analyticsService is NULL — event not captured")
        } else {
            svc.capture(event, payload)
        }
    }

    /** Forward only to the active CEP plugin — used for survey Clicked/Dismissed. */
    fun notifyCEP(event: DigiaExperienceEvent, payload: InAppPayload) {
        pluginRegistry.notifyEvent(event, payload)
        val svc = getAnalyticsService()
        if (svc == null) {
            android.util.Log.w("DigiaAnalytics", "[DisplayCoordinator] onSlotEvent: analyticsService is NULL — event not captured")
        } else {
            android.util.Log.w("DigiaAnalytics", "[DisplayCoordinator] notifyCEP: forwarding event to analytics service: event=${event::class.simpleName} campaignKey=${payload.content["campaign_key"]} campaignId=${payload.content["campaign_id"]}")
            svc.capture(event, payload)
        }
    }

    /** Record only into internal analytics — used for survey Answered/Completed. */
    fun trackInternal(event: InternalEngageEvent, payload: InAppPayload) {
        getAnalyticsService()?.captureInternal(event, payload)
    }
}
