package com.digia.engage.internal

/** Port of RN frequencyEvaluator.ts — pure functions, no Android imports. */

internal data class FrequencyPolicy(
    val maxTotal: Int?,
    val maxPerWindow: MaxPerWindow?,
    val stopOn: String?,
) {
    data class MaxPerWindow(val count: Int, val window: String)
}

internal data class FrequencyState(
    val shownCount: Int,
    val firstShownAt: Long?,
    val stoppedAt: Long?,
)

internal data class FrequencyEvalResult(val allow: Boolean, val reason: String?)

internal object FrequencyEvaluator {
    private val windowMs = mapOf(
        "day" to 86_400_000L,
        "week" to 7 * 86_400_000L,
        "month" to 30 * 86_400_000L,
    )

    fun evaluate(policy: FrequencyPolicy?, state: FrequencyState?, now: Long): FrequencyEvalResult {
        if (policy == null) return FrequencyEvalResult(true, null)
        if (state == null) return FrequencyEvalResult(true, null)
        if (state.stoppedAt != null) return FrequencyEvalResult(false, "stopped")
        policy.maxTotal?.let { if (state.shownCount >= it) return FrequencyEvalResult(false, "max_total") }
        policy.maxPerWindow?.let { mpw ->
            val wms = windowMs[mpw.window]
            if (wms != null && state.firstShownAt != null && now - state.firstShownAt > wms)
                return FrequencyEvalResult(false, "window")
            if (state.shownCount >= mpw.count) return FrequencyEvalResult(false, "max_total")
        }
        return FrequencyEvalResult(true, null)
    }

    fun hasPolicy(p: FrequencyPolicy?) =
        p != null && (p.maxTotal != null || p.maxPerWindow != null || p.stopOn != null)
}
