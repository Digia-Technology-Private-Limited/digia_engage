package com.digia.engage.internal.frequency

import com.digia.engage.internal.analytics.KeyValueStore
import org.json.JSONObject

/**
 * Applies frequency capping for natively-rendered campaigns (nudge + survey).
 * Wraps the pure [FrequencyEvaluator] with persistence (one [FrequencyState] per
 * campaignKey in a [KeyValueStore]) and pulls the current analytics sessionId so
 * `session` windows reset exactly when the native session rotates (cold start /
 * 30-min idle / setUserId / clearUserId).
 *
 * @param sessionIdProvider returns the current analytics sessionId, or null when
 *   analytics is disabled (the evaluator then treats session windows as fresh).
 * @param clock injectable for tests; defaults to wall-clock.
 */
internal class FrequencyManager(
    private val store: KeyValueStore,
    private val sessionIdProvider: () -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** Gate: whether the campaign may show now. Capping-free policies always pass. */
    fun isAllowed(campaignKey: String, policy: FrequencyPolicy?): Boolean {
        if (policy == null || !policy.hasConstraint()) return true
        val result = FrequencyEvaluator.evaluate(policy, load(campaignKey), clock(), sessionIdProvider())
        return result.allow
    }

    /** Record one show on "Digia Experience Viewed". */
    fun recordShow(campaignKey: String, policy: FrequencyPolicy?) {
        if (policy == null || !policy.hasConstraint()) return
        val next = FrequencyEvaluator.recordShow(policy, load(campaignKey), clock(), sessionIdProvider())
        save(campaignKey, next)
    }

    /** Apply the permanent stop on "Digia Experience Completed" (survey only). */
    fun recordCompleted(campaignKey: String, policy: FrequencyPolicy?) {
        if (policy?.stopOn != STOP_ON_EXPERIENCE_COMPLETED) return
        val prev = load(campaignKey)
        if (prev?.stoppedAt != null) return
        save(campaignKey, FrequencyEvaluator.recordStop(prev, clock()))
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun keyFor(campaignKey: String) = "$KEY_PREFIX$campaignKey"

    private fun load(campaignKey: String): FrequencyState? {
        val raw = store.getString(keyFor(campaignKey), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            FrequencyState(
                total = o.optInt("total", 0),
                windowCount = o.optInt("windowCount", 0),
                windowAnchorAt = if (o.has("windowAnchorAt")) o.optLong("windowAnchorAt") else null,
                sessionId = if (o.has("sessionId") && !o.isNull("sessionId")) o.optString("sessionId") else null,
                stoppedAt = if (o.has("stoppedAt")) o.optLong("stoppedAt") else null,
            )
        }.getOrNull()
    }

    private fun save(campaignKey: String, state: FrequencyState) {
        val o = JSONObject()
            .put("total", state.total)
            .put("windowCount", state.windowCount)
        state.windowAnchorAt?.let { o.put("windowAnchorAt", it) }
        state.sessionId?.let { o.put("sessionId", it) }
        state.stoppedAt?.let { o.put("stoppedAt", it) }
        store.putString(keyFor(campaignKey), o.toString())
    }

    companion object {
        const val KEY_PREFIX = "freq:"
        const val STOP_ON_EXPERIENCE_COMPLETED = "experienceCompleted"

        /**
         * Parse the opaque camelCase frequency policy from a campaign's JSON, or
         * null when there is none ("No cap" / inline). Old snake_case payloads
         * parse to a no-constraint policy and are treated as uncapped.
         */
        fun parsePolicy(json: JSONObject?): FrequencyPolicy? {
            if (json == null) return null
            val maxTotal = if (json.has("maxTotal") && !json.isNull("maxTotal")) json.optInt("maxTotal") else null
            val windowJson = json.optJSONObject("maxPerWindow")
            val maxPerWindow = windowJson?.let {
                val count = if (it.has("count") && !it.isNull("count")) it.optInt("count") else return@let null
                val window = it.optString("window", "").takeIf { w -> w.isNotBlank() } ?: return@let null
                FrequencyWindow(count = count, window = window)
            }
            val stopOn = if (json.has("stopOn") && !json.isNull("stopOn")) {
                json.optString("stopOn", "").takeIf { it.isNotBlank() }
            } else {
                null
            }
            val policy = FrequencyPolicy(maxTotal = maxTotal, maxPerWindow = maxPerWindow, stopOn = stopOn)
            return if (policy.hasConstraint()) policy else null
        }
    }
}
