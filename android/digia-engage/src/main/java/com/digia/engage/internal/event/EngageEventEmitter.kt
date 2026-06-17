package com.digia.engage.internal.event

import com.digia.engage.CEPTriggerPayload
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.internal.logging.Logger

/**
 * The SDK's single entry point for emitting events, and the one place every
 * emission is logged.
 *
 * Facade over the two delivery channels, which carry deliberately different event
 * models: the CEP plugin gets the coarse [DigiaExperienceEvent] protocol via
 * [toCep]; Digia analytics gets the rich, campaign-grouped [EngageAnalyticsEvent]
 * via [toDigia]. [toBoth] fires a dual signal (e.g. nudge impression). Also owns
 * the first-render impression dedup, an emission concern rather than widget state.
 */
internal class EngageEventEmitter(
    private val cep: CepPluginSink,
    private val digia: DigiaAnalyticsSink,
) {
    /** `cepCampaignId`s that have already fired a Digia first-render impression. */
    private val digiaImpressed = mutableSetOf<String>()

    /** `cepCampaignId`s that have already fired a Digia first-engagement click. */
    private val digiaClicked = mutableSetOf<String>()

    /** Coarse signal to the CEP plugin only. */
    fun toCep(event: DigiaExperienceEvent, payload: CEPTriggerPayload) {
        Logger.info("Event fired → CEP: $event | campaignKey=${payload.campaignKey} cepCampaignId=${payload.cepCampaignId}")
        cep.deliver(event, payload)
    }

    /** Rich analytics signal to Digia only. */
    fun toDigia(event: EngageAnalyticsEvent, payload: CEPTriggerPayload) {
        Logger.info(
            "Event fired → DIGIA: '${event.eventName}' (${event::class.simpleName}) | " +
                "campaignKey=${payload.campaignKey} cepCampaignId=${payload.cepCampaignId} " +
                "columns=${event.columns} properties=${event.properties}",
        )
        digia.deliver(event, payload)
    }

    /** Fires a coarse CEP signal and its rich Digia counterpart together. */
    fun toBoth(cepEvent: DigiaExperienceEvent, digiaEvent: EngageAnalyticsEvent, payload: CEPTriggerPayload) {
        toCep(cepEvent, payload)
        toDigia(digiaEvent, payload)
    }

    /**
     * Records [event] (a campaign "Viewed") to Digia the first time its campaign
     * renders, deduped by `cepCampaignId`. CEP is impressed separately and
     * instantly at route time.
     */
    fun digiaImpressionOnce(payload: CEPTriggerPayload, event: EngageAnalyticsEvent) {
        if (!digiaImpressed.add(payload.cepCampaignId)) return
        toDigia(event, payload)
    }

    /**
     * Records [event] (an experience-level "Clicked") to Digia the first time the
     * user engages with this campaign, deduped by `cepCampaignId`. Used for inline
     * widgets where the first item tap is the campaign's engagement signal.
     */
    fun digiaExperienceClickedOnce(payload: CEPTriggerPayload, event: EngageAnalyticsEvent) {
        if (!digiaClicked.add(payload.cepCampaignId)) return
        toDigia(event, payload)
    }

    /** Forgets the impression + first-click marks so a later re-trigger re-arms both. */
    fun resetImpression(cepCampaignId: String) {
        digiaImpressed.remove(cepCampaignId)
        digiaClicked.remove(cepCampaignId)
    }

    /** Forgets every impression + first-click mark. */
    fun clearImpressions() {
        digiaImpressed.clear()
        digiaClicked.clear()
    }
}
