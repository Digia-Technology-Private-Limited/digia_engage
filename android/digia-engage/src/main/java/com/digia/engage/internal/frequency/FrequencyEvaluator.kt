package com.digia.engage.internal.frequency

/**
 * Frequency capping core — pure, platform-free logic shared in spirit with the
 * iOS implementation. Identical golden matrix across platforms. `now` and
 * `sessionId` are injected so the logic is fully deterministic and unit-testable.
 *
 * Caps all natively-managed campaigns: nudge, survey, and (on React Native)
 * guides — whose Viewed/Completed events arrive over the bridge and drive
 * recordShow/recordStop.
 */

internal data class FrequencyWindow(val count: Int, val window: String)

internal data class FrequencyPolicy(
    val maxTotal: Int? = null,
    val maxPerWindow: FrequencyWindow? = null,
    val stopOn: String? = null,
) {
    /** True when the policy carries at least one active constraint. */
    fun hasConstraint(): Boolean = maxTotal != null || maxPerWindow != null || stopOn != null
}

/**
 * Persisted per-campaign capping state. `total` (lifetime) and `windowCount`
 * (current window) are tracked independently so a per-window cap never
 * double-counts against maxTotal.
 */
internal data class FrequencyState(
    val total: Int = 0,
    val windowCount: Int = 0,
    val windowAnchorAt: Long? = null,
    val sessionId: String? = null,
    val stoppedAt: Long? = null,
)

internal enum class FrequencySkipReason { MAX_TOTAL, WINDOW, STOPPED }

internal data class FrequencyEvalResult(
    val allow: Boolean,
    val reason: FrequencySkipReason? = null,
)

internal object FrequencyEvaluator {

    private const val DAY_MS = 86_400_000L
    private val WINDOW_MS = mapOf(
        "day" to DAY_MS,
        "week" to 7 * DAY_MS,
        "month" to 30 * DAY_MS,
    )

    /**
     * Pure eligibility check. Blocks if ANY active constraint is exceeded:
     * permanent stop, lifetime total, or the current window.
     */
    fun evaluate(
        policy: FrequencyPolicy,
        state: FrequencyState?,
        now: Long,
        sessionId: String?,
    ): FrequencyEvalResult {
        if (state == null) return FrequencyEvalResult(true)
        if (state.stoppedAt != null) return FrequencyEvalResult(false, FrequencySkipReason.STOPPED)
        if (policy.maxTotal != null && state.total >= policy.maxTotal) {
            return FrequencyEvalResult(false, FrequencySkipReason.MAX_TOTAL)
        }
        val w = policy.maxPerWindow
        if (w != null) {
            val effective = if (isWindowExpired(w, state, now, sessionId)) 0 else state.windowCount
            if (effective >= w.count) return FrequencyEvalResult(false, FrequencySkipReason.WINDOW)
        }
        return FrequencyEvalResult(true)
    }

    /**
     * Record one show on "Digia Experience Viewed". Bumps the lifetime total and
     * the window count, re-anchoring the window when it has rolled over.
     */
    fun recordShow(
        policy: FrequencyPolicy,
        state: FrequencyState?,
        now: Long,
        sessionId: String?,
    ): FrequencyState {
        val prev = state ?: FrequencyState()
        val w = policy.maxPerWindow
        val fresh = w == null || isWindowExpired(w, prev, now, sessionId)
        val isTimeWindow = w != null && w.window != "session"
        val isSessionWindow = w != null && w.window == "session"
        return prev.copy(
            total = prev.total + 1,
            windowCount = if (fresh) 1 else prev.windowCount + 1,
            windowAnchorAt = if (isTimeWindow) {
                if (fresh) now else prev.windowAnchorAt
            } else {
                prev.windowAnchorAt
            },
            sessionId = if (isSessionWindow) sessionId else prev.sessionId,
        )
    }

    /**
     * Permanently stop the campaign on "Digia Experience Completed" when the
     * policy opted into stopOn. Idempotent: the first stop timestamp wins.
     */
    fun recordStop(state: FrequencyState?, now: Long): FrequencyState {
        val prev = state ?: FrequencyState()
        return if (prev.stoppedAt != null) prev else prev.copy(stoppedAt = now)
    }

    private fun isWindowExpired(
        w: FrequencyWindow,
        state: FrequencyState,
        now: Long,
        sessionId: String?,
    ): Boolean {
        if (w.window == "session") return state.sessionId != sessionId
        val ms = WINDOW_MS[w.window] ?: return true
        val anchor = state.windowAnchorAt ?: return true
        return now - anchor >= ms
    }
}
