package com.digia.engage.internal.frequency

import com.digia.engage.internal.analytics.KeyValueStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden matrix for native (nudge + survey) frequency capping. Mirrors the RN
 * `frequencyEvaluator.test.ts` and the iOS XCTest suite exactly — `now` and
 * `sessionId` are injected for determinism.
 */
class FrequencyManagerTest {

    private val hour = 3_600_000L
    private val day = 86_400_000L

    private fun win(count: Int, window: String) = FrequencyWindow(count, window)

    // Drive a policy through show attempts. Returns the allow flags. Threads state
    // the way the real wiring does: evaluate → if allowed, recordShow.
    private data class Attempt(val now: Long, val sessionId: String = "s1", val complete: Boolean = false)

    private fun run(policy: FrequencyPolicy, attempts: List<Attempt>): List<Boolean> {
        var state: FrequencyState? = null
        val allowed = mutableListOf<Boolean>()
        for (a in attempts) {
            val res = FrequencyEvaluator.evaluate(policy, state, a.now, a.sessionId)
            allowed += res.allow
            if (res.allow) state = FrequencyEvaluator.recordShow(policy, state, a.now, a.sessionId)
            if (a.complete) state = FrequencyEvaluator.recordStop(state, a.now)
        }
        return allowed
    }

    @Test
    fun `1 - no-constraint policy is treated as uncapped`() {
        assertFalse(FrequencyPolicy().hasConstraint())
        assertTrue(FrequencyPolicy(maxTotal = 1).hasConstraint())
    }

    @Test
    fun `2 - maxTotal 3 allows three then blocks`() {
        val policy = FrequencyPolicy(maxTotal = 3)
        val allowed = run(policy, listOf(Attempt(0), Attempt(hour), Attempt(2 * hour), Attempt(3 * hour)))
        assertEquals(listOf(true, true, true, false), allowed)
    }

    @Test
    fun `3 - session window same session blocks second`() {
        val policy = FrequencyPolicy(maxPerWindow = win(1, "session"))
        assertEquals(listOf(true, false), run(policy, listOf(Attempt(0, "s1"), Attempt(hour, "s1"))))
    }

    @Test
    fun `4 - session window rotated session allows both`() {
        val policy = FrequencyPolicy(maxPerWindow = win(1, "session"))
        assertEquals(listOf(true, true), run(policy, listOf(Attempt(0, "s1"), Attempt(hour, "s2"))))
    }

    @Test
    fun `5 - two per day blocks third within 24h`() {
        val policy = FrequencyPolicy(maxPerWindow = win(2, "day"))
        assertEquals(
            listOf(true, true, false),
            run(policy, listOf(Attempt(0), Attempt(hour), Attempt(2 * hour))),
        )
    }

    @Test
    fun `6 - one per day rolls over after 25h`() {
        val policy = FrequencyPolicy(maxPerWindow = win(1, "day"))
        assertEquals(listOf(true, true), run(policy, listOf(Attempt(0), Attempt(25 * hour))))
    }

    @Test
    fun `7 - weekly and monthly roll over after their window`() {
        val week = FrequencyPolicy(maxPerWindow = win(1, "week"))
        assertEquals(listOf(true, true), run(week, listOf(Attempt(0), Attempt(8 * day))))
        assertEquals(listOf(true, false), run(week, listOf(Attempt(0), Attempt(6 * day))))
        val month = FrequencyPolicy(maxPerWindow = win(1, "month"))
        assertEquals(listOf(true, true), run(month, listOf(Attempt(0), Attempt(31 * day))))
        assertEquals(listOf(true, false), run(month, listOf(Attempt(0), Attempt(29 * day))))
    }

    @Test
    fun `8 - stopOn experienceCompleted blocks forever after completion`() {
        val policy = FrequencyPolicy(stopOn = "experienceCompleted")
        val allowed = run(
            policy,
            listOf(Attempt(0, "s1", complete = true), Attempt(hour, "s1"), Attempt(2 * day, "s2")),
        )
        assertEquals(listOf(true, false, false), allowed)
    }

    @Test
    fun `9 - maxTotal plus per-day - per-day wins same day`() {
        val policy = FrequencyPolicy(maxTotal = 5, maxPerWindow = win(1, "day"))
        assertEquals(listOf(true, false), run(policy, listOf(Attempt(0), Attempt(hour))))
    }

    @Test
    fun `10 - maxTotal ignores session rotation`() {
        val policy = FrequencyPolicy(maxTotal = 2)
        assertEquals(
            listOf(true, true, false),
            run(policy, listOf(Attempt(0, "s1"), Attempt(hour, "s2"), Attempt(2 * hour, "s3"))),
        )
    }

    @Test
    fun `11 - session window cold start allows both`() {
        val policy = FrequencyPolicy(maxPerWindow = win(1, "session"))
        assertEquals(listOf(true, true), run(policy, listOf(Attempt(0, "cold-1"), Attempt(day, "cold-2"))))
    }

    @Test
    fun `12 - blocked attempt does not advance state`() {
        val policy = FrequencyPolicy(maxTotal = 1)
        var state: FrequencyState? = null
        assertTrue(FrequencyEvaluator.evaluate(policy, state, 0, "s1").allow)
        state = FrequencyEvaluator.recordShow(policy, state, 0, "s1")
        assertEquals(1, state.total)
        assertFalse(FrequencyEvaluator.evaluate(policy, state, hour, "s1").allow)
        assertEquals(1, state!!.total)
    }

    @Test
    fun `recordShow re-anchors time window only on rollover`() {
        val policy = FrequencyPolicy(maxPerWindow = win(5, "day"))
        var s = FrequencyEvaluator.recordShow(policy, null, 1000, "s1")
        assertEquals(FrequencyState(total = 1, windowCount = 1, windowAnchorAt = 1000), s)
        s = FrequencyEvaluator.recordShow(policy, s, 1000 + hour, "s1")
        assertEquals(2, s.total); assertEquals(2, s.windowCount); assertEquals(1000L, s.windowAnchorAt)
        s = FrequencyEvaluator.recordShow(policy, s, 1000 + 25 * hour, "s1")
        assertEquals(3, s.total); assertEquals(1, s.windowCount); assertEquals(1000 + 25 * hour, s.windowAnchorAt)
    }

    @Test
    fun `recordStop is idempotent`() {
        var s = FrequencyEvaluator.recordStop(null, 500)
        assertEquals(500L, s.stoppedAt)
        s = FrequencyEvaluator.recordStop(s, 900)
        assertEquals(500L, s.stoppedAt)
    }

    // ── Persistence (FrequencyManager + fake store) ───────────────────────────

    private class FakeStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(key: String, default: String?) = map[key] ?: default
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    @Test
    fun `manager persists state across reloads and caps`() {
        val store = FakeStore()
        var now = 0L
        var session = "s1"
        val mgr = FrequencyManager(store, sessionIdProvider = { session }, clock = { now })
        val policy = FrequencyPolicy(maxPerWindow = win(1, "session"))

        assertTrue(mgr.isAllowed("camp", policy))
        mgr.recordShow("camp", policy)
        now += hour
        assertFalse(mgr.isAllowed("camp", policy)) // same session → capped

        // A fresh manager over the same store must see the persisted state.
        val mgr2 = FrequencyManager(store, sessionIdProvider = { session }, clock = { now })
        assertFalse(mgr2.isAllowed("camp", policy))

        session = "s2" // rotate session → window resets
        assertTrue(mgr2.isAllowed("camp", policy))
    }

    @Test
    fun `manager recordCompleted stops only for experienceCompleted policies`() {
        val store = FakeStore()
        val mgr = FrequencyManager(store, sessionIdProvider = { "s1" }, clock = { 0L })

        // No stopOn → recordCompleted is a no-op.
        mgr.recordCompleted("a", FrequencyPolicy(maxTotal = 5))
        assertNull(store.map["${FrequencyManager.KEY_PREFIX}a"])

        mgr.recordCompleted("b", FrequencyPolicy(stopOn = "experienceCompleted"))
        assertFalse(mgr.isAllowed("b", FrequencyPolicy(stopOn = "experienceCompleted")))
    }

    @Test
    fun `parsePolicy reads camelCase and rejects empty`() {
        assertNull(FrequencyManager.parsePolicy(null))
        assertNull(FrequencyManager.parsePolicy(JSONObject()))
        val p = FrequencyManager.parsePolicy(
            JSONObject("""{"maxTotal":3,"maxPerWindow":{"count":2,"window":"day"},"stopOn":"experienceCompleted"}"""),
        )!!
        assertEquals(3, p.maxTotal)
        assertEquals(FrequencyWindow(2, "day"), p.maxPerWindow)
        assertEquals("experienceCompleted", p.stopOn)
        // Old snake_case payload has no recognised keys → uncapped (null).
        assertNull(FrequencyManager.parsePolicy(JSONObject("""{"max_total":3,"stop_on":"click"}""")))
    }
}
