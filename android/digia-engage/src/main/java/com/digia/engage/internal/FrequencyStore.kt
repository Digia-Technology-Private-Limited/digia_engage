package com.digia.engage.internal

import android.content.SharedPreferences
import org.json.JSONObject

/** Port of RN frequencyStore.ts — persists per-campaign frequency state via SharedPreferences. */
internal class FrequencyStore(private val prefs: SharedPreferences) {

    fun get(campaignKey: String): FrequencyState? {
        val raw = prefs.getString(storeKey(campaignKey), null) ?: return null
        return try {
            val j = JSONObject(raw)
            FrequencyState(
                shownCount = j.optInt("shown_count", 0),
                firstShownAt = if (j.has("first_shown_at")) j.getLong("first_shown_at") else null,
                stoppedAt = if (j.has("stopped_at")) j.getLong("stopped_at") else null,
            )
        } catch (_: Exception) { null }
    }

    fun set(campaignKey: String, state: FrequencyState) {
        val j = JSONObject().apply {
            put("shown_count", state.shownCount)
            state.firstShownAt?.let { put("first_shown_at", it) }
            state.stoppedAt?.let { put("stopped_at", it) }
        }
        prefs.edit().putString(storeKey(campaignKey), j.toString()).apply()
    }

    fun markStopped(campaignKey: String) {
        val state = get(campaignKey) ?: FrequencyState(0, null, null)
        set(campaignKey, state.copy(stoppedAt = System.currentTimeMillis()))
    }

    private fun storeKey(k: String) = "digia:freq:$k"
}
