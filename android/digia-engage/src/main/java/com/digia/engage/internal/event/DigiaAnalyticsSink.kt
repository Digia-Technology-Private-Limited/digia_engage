package com.digia.engage.internal.event

import com.digia.engage.CEPTriggerPayload
import com.digia.engage.internal.analytics.AnalyticsService
import com.digia.engage.internal.model.CampaignModel

/**
 * Delivers rich [EngageAnalyticsEvent]s to Digia's first-party analytics backend.
 *
 * Resolves the campaign from the store by `campaignKey` for attribution context
 * (campaign id/type live on the [CampaignModel], not the trigger payload), then
 * hands the event to [AnalyticsService], which hoists [EngageAnalyticsEvent.columns]
 * to top level and nests [EngageAnalyticsEvent.properties].
 */
internal class DigiaAnalyticsSink(
    private val getAnalyticsService: () -> AnalyticsService?,
    private val getCampaign: (String) -> CampaignModel?,
) {
    fun deliver(event: EngageAnalyticsEvent, payload: CEPTriggerPayload) {
        val svc = getAnalyticsService() ?: return
        val campaign = getCampaign(payload.campaignKey)
        svc.capture(event, payload, campaignId = campaign?.id, campaignType = campaign?.campaignType)
    }
}
