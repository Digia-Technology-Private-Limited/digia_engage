package com.digia.engage.internal

import org.junit.Assert.*
import org.junit.Test

class FrequencyEvaluatorTest {
    private val now = System.currentTimeMillis()

    @Test
    fun `no policy always allows`() {
        assertTrue(FrequencyEvaluator.evaluate(null, null, now).allow)
    }

    @Test
    fun `no state always allows`() {
        val policy = FrequencyPolicy(maxTotal = 3, maxPerWindow = null, stopOn = null)
        assertTrue(FrequencyEvaluator.evaluate(policy, null, now).allow)
    }

    @Test
    fun `max_total blocks when count reached`() {
        val policy = FrequencyPolicy(maxTotal = 3, maxPerWindow = null, stopOn = null)
        val state = FrequencyState(shownCount = 3, firstShownAt = now - 1000L, stoppedAt = null)
        val result = FrequencyEvaluator.evaluate(policy, state, now)
        assertFalse(result.allow)
        assertEquals("max_total", result.reason)
    }

    @Test
    fun `stopped_at always blocks`() {
        val policy = FrequencyPolicy(maxTotal = 10, maxPerWindow = null, stopOn = null)
        val state = FrequencyState(shownCount = 1, firstShownAt = now - 1000L, stoppedAt = now - 500L)
        assertFalse(FrequencyEvaluator.evaluate(policy, state, now).allow)
    }

    @Test
    fun `allows when under max_total`() {
        val policy = FrequencyPolicy(maxTotal = 5, maxPerWindow = null, stopOn = null)
        val state = FrequencyState(shownCount = 2, firstShownAt = now - 1000L, stoppedAt = null)
        assertTrue(FrequencyEvaluator.evaluate(policy, state, now).allow)
    }

    @Test
    fun `max_per_window blocks when window elapsed`() {
        val policy = FrequencyPolicy(
            maxTotal = null,
            maxPerWindow = FrequencyPolicy.MaxPerWindow(count = 5, window = "day"),
            stopOn = null,
        )
        val firstShown = now - 90_000_000L // 25 hours ago
        val state = FrequencyState(shownCount = 1, firstShownAt = firstShown, stoppedAt = null)
        val result = FrequencyEvaluator.evaluate(policy, state, now)
        assertFalse(result.allow)
        assertEquals("window", result.reason)
    }

    @Test
    fun `max_per_window blocks when count reached within window`() {
        val policy = FrequencyPolicy(
            maxTotal = null,
            maxPerWindow = FrequencyPolicy.MaxPerWindow(count = 2, window = "day"),
            stopOn = null,
        )
        val state = FrequencyState(shownCount = 2, firstShownAt = now - 1000L, stoppedAt = null)
        val result = FrequencyEvaluator.evaluate(policy, state, now)
        assertFalse(result.allow)
    }

    @Test
    fun `hasPolicy returns false for empty policy`() {
        assertFalse(FrequencyEvaluator.hasPolicy(null))
        assertFalse(FrequencyEvaluator.hasPolicy(FrequencyPolicy(null, null, null)))
    }

    @Test
    fun `hasPolicy returns true when any field set`() {
        assertTrue(FrequencyEvaluator.hasPolicy(FrequencyPolicy(maxTotal = 1, null, null)))
    }
}
