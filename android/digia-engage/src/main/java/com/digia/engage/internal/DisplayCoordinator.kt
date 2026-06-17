package com.digia.engage.internal

import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.internal.analytics.AnalyticsService
import com.digia.engage.internal.logging.Logger
import com.digia.engage.internal.model.InlineCarouselConfig
import com.digia.engage.internal.model.InlineStoryConfig
import com.digia.engage.internal.model.NudgeConfig

internal class DisplayCoordinator(
    private val overlayController: DigiaOverlayController,
    private val pluginRegistry: PluginRegistry,
    private val getAnalyticsService: () -> AnalyticsService?,
) {
    fun routeNudge(config: NudgeConfig, payload: InAppPayload, variables: Map<String, String> = emptyMap()) {
        Logger.verbose("Showing nudge: id=${payload.id} displayStyle=${config.surface.displayType}")
        overlayController.showNudge(config, payload, variables)
    }

    fun routeInline(placementKey: String, payload: InAppPayload) {
        Logger.verbose("Showing inline: id=${payload.id} slotKey=$placementKey")
        overlayController.addSlot(placementKey, payload)
    }

    fun routeInlineCarousel(config: InlineCarouselConfig, payload: InAppPayload) {
        Logger.verbose("Showing inline carousel: id=${payload.id} slotKey=${config.slotKey}")
        overlayController.addSlotConfig(config.slotKey, config)
        overlayController.addSlot(config.slotKey, payload)
    }

    fun routeInlineStory(config: InlineStoryConfig, payload: InAppPayload) {
        Logger.verbose("Showing inline story: id=${payload.id} slotKey=${config.slotKey}")
        overlayController.addStorySlotConfig(config.slotKey, config)
        overlayController.addSlot(config.slotKey, payload)
    }

    fun dismissNudge(campaignId: String) {
        if (overlayController.nudgeOverlay.value?.payload?.id == campaignId) {
            Logger.verbose("Dismissing nudge: id=$campaignId")
            overlayController.dismissNudge()
        }
    }

    fun dismissInline(campaignId: String) {
        overlayController.removeSlotById(campaignId)
    }

    fun onOverlayEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        Logger.verbose("Experience event: ${event::class.simpleName} id=${payload.id}")
        pluginRegistry.notifyEvent(event, payload)
        val svc = getAnalyticsService()
        if (svc == null) {
            android.util.Log.w("DigiaAnalytics", "[DisplayCoordinator] onOverlayEvent: analyticsService is NULL — event not captured")
        } else {
            svc.capture(event, payload)
        }
    }

    fun onOverlayAction(actionType: String, url: String, payload: InAppPayload): Boolean {
        Logger.verbose("Overlay action: type=$actionType id=${payload.id}")
        return pluginRegistry.notifyAction(actionType, url, payload)
    }

    fun onSlotEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        Logger.verbose("Slot event: ${event::class.simpleName} id=${payload.id}")
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
