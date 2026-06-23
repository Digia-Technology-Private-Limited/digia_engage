package com.digia.engage.internal.event

/**
 * Tracks how long each campaign instance was on screen.
 *
 * [markViewed] stamps the moment a campaign becomes visible; [consumeDwellMs]
 * returns the elapsed time at dismissal and forgets the mark. Keyed by
 * `cepCampaignId`. SRP: this owns dwell timing only, decoupled from the events
 * and the emitter. The clock is injectable for tests.
 */
internal class DwellTracker(
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val viewedAtMs = mutableMapOf<String, Long>()

    /** Records that [cepCampaignId] became visible now. */
    fun markViewed(cepCampaignId: String) {
        viewedAtMs[cepCampaignId] = now()
    }

    /**
     * Returns ms since [markViewed] for [cepCampaignId] *without* forgetting the
     * mark — for mid-life signals (e.g. a click while the campaign is still up).
     */
    fun elapsedMs(cepCampaignId: String): Long? =
        viewedAtMs[cepCampaignId]?.let { now() - it }

    /**
     * Returns ms elapsed since [markViewed] for [cepCampaignId] and forgets the
     * mark, or null if it was never marked (so callers omit the field).
     */
    fun consumeDwellMs(cepCampaignId: String): Long? =
        viewedAtMs.remove(cepCampaignId)?.let { now() - it }

    fun clear() {
        viewedAtMs.clear()
    }
}
