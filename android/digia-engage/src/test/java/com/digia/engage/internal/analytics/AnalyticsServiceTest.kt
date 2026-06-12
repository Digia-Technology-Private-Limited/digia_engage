package com.digia.engage.internal.analytics

import com.digia.engage.AnalyticsConfig
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsServiceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sharedStore: InMemoryKeyValueStore

    @Before
    fun setUp() {
        sharedStore = InMemoryKeyValueStore()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createService(
        config: AnalyticsConfig = AnalyticsConfig(flushIntervalMs = 10_000L),
        sender: AnalyticsSender = FakeSender { successResponse() },
        store: InMemoryKeyValueStore = sharedStore,
    ): AnalyticsService = AnalyticsService(
        config = config,
        apiKey = "test-api-key",
        identity = AnalyticsIdentityManager(store),
        queue = AnalyticsQueue(store),
        sender = sender,
        staticContext = mapOf("sdk_version" to "1.0.0", "sdk_platform" to "android"),
        scope = testScope,
    )

    private fun buildPayload(campaignKey: String) = InAppPayload(
        id = campaignKey,
        content = mapOf(
            "campaign_id" to "example-campaign",
            "campaign_key" to campaignKey,
            "campaign_type" to "guide",
        ),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `anonymous ID is generated and stable`() = testScope.runTest {
        val service = createService()
        val id1 = service.identity.anonymousId
        val id2 = service.identity.anonymousId
        assertTrue(id1.isNotEmpty())
        assertEquals(id1, id2)
    }

    @Test
    fun `setUserId persists and clearUserId resets session`() = testScope.runTest {
        val service = createService()

        service.setUserId("user-123")
        assertEquals("user-123", service.identity.userId)

        val sessionBefore = service.identity.sessionId
        service.clearUserId()

        assertNull(service.identity.userId)
        assertTrue(service.identity.sessionId.isNotEmpty())
        assertNotEquals(sessionBefore, service.identity.sessionId)
    }

    @Test
    fun `queue drops oldest events when capacity exceeded`() = testScope.runTest {
        val service = createService(
            config = AnalyticsConfig(queueMaxEvents = 3, flushBatchSize = 100, flushIntervalMs = 10_000L),
        )
        // capture is synchronous — no advanceUntilIdle needed
        for (i in 0..4) {
            service.capture(DigiaExperienceEvent.Impressed, buildPayload("event-$i"))
        }

        assertEquals(3, service.queue.size())
        val entries = service.queue.peek(10)
        // oldest two (event-0, event-1) were dropped; event-2, event-3, event-4 remain
        assertEquals("event-2", entries[0].payload["campaign_key"])
        assertEquals("event-3", entries[1].payload["campaign_key"])
        assertEquals("event-4", entries[2].payload["campaign_key"])
    }

    @Test
    fun `event payload has correct structure and identity fields`() = testScope.runTest {
        val service = createService()
        service.capture(DigiaExperienceEvent.Impressed, buildPayload("payload-1"))
        // capture is synchronous — event is immediately in queue

        val entries = service.queue.peek(1)
        assertTrue(entries.isNotEmpty())
        val event = entries.first().payload

        assertEquals("Digia Experience Viewed", event["event_name"])
        assertEquals("example-campaign", event["campaign_id"])
        assertEquals("payload-1", event["campaign_key"])
        assertEquals("guide", event["campaign_type"])
        assertTrue((event["event_id"] as? String)?.isNotEmpty() == true)
        assertTrue((event["occurred_at"] as? String)?.isNotEmpty() == true)
        assertTrue((event["anonymous_id"] as? String)?.isNotEmpty() == true)
        assertTrue((event["session_id"] as? String)?.isNotEmpty() == true)
        assertNull(event["user_id"]) // not set — must be absent

        @Suppress("UNCHECKED_CAST")
        val props = event["properties"] as? Map<String, Any?>
        assertNotNull(props)
        assertEquals("1.0.0", props?.get("sdk_version"))
        assertEquals("android", props?.get("sdk_platform"))
    }

    @Test
    fun `event names map correctly for all experience event types`() = testScope.runTest {
        val service = createService()
        val payload = buildPayload("test")

        service.capture(DigiaExperienceEvent.Impressed, payload)
        service.capture(DigiaExperienceEvent.Clicked("cta-btn"), payload)
        service.capture(DigiaExperienceEvent.Dismissed, payload)
        // capture is synchronous — all three are immediately in queue

        val entries = service.queue.peek(10)
        assertEquals(3, entries.size)
        assertEquals("Digia Experience Viewed", entries[0].payload["event_name"])
        assertEquals("Digia Experience Clicked", entries[1].payload["event_name"])
        assertEquals("Digia Experience Dismissed", entries[2].payload["event_name"])

        // element_id only present for Clicked
        @Suppress("UNCHECKED_CAST")
        val clickProps = entries[1].payload["properties"] as? Map<String, Any?>
        assertEquals("cta-btn", clickProps?.get("element_id"))
        @Suppress("UNCHECKED_CAST")
        val impressedProps = entries[0].payload["properties"] as? Map<String, Any?>
        assertNull(impressedProps?.get("element_id"))
    }

    @Test
    fun `batch threshold triggers immediate flush`() = testScope.runTest {
        val fakeSender = FakeSender { successResponse() }
        val service = createService(
            config = AnalyticsConfig(flushBatchSize = 2, flushIntervalMs = 10_000L),
            sender = fakeSender,
        )

        service.capture(DigiaExperienceEvent.Impressed, buildPayload("p1"))
        service.capture(DigiaExperienceEvent.Impressed, buildPayload("p2"))
        // second capture triggers batch flush (size >= flushBatchSize=2); run the dispatch coroutine
        advanceUntilIdle()

        assertEquals(0, service.queue.size())
        assertEquals(1, fakeSender.callCount)
    }

    @Test
    fun `timer fires after flushIntervalMs`() = testScope.runTest {
        val fakeSender = FakeSender { successResponse() }
        val service = createService(
            config = AnalyticsConfig(flushBatchSize = 10, flushIntervalMs = 50L),
            sender = fakeSender,
        )

        service.capture(DigiaExperienceEvent.Impressed, buildPayload("p1"))
        // capture is synchronous; timer scheduled at 50ms — do not call advanceUntilIdle here

        advanceTimeBy(200L)
        advanceUntilIdle()

        assertEquals(0, service.queue.size())
        assertEquals(1, fakeSender.callCount)
    }

    @Test
    fun `explicit flush dispatches pending events`() = testScope.runTest {
        val fakeSender = FakeSender { successResponse() }
        val service = createService(
            config = AnalyticsConfig(flushBatchSize = 10, flushIntervalMs = 10_000L),
            sender = fakeSender,
        )

        service.capture(DigiaExperienceEvent.Impressed, buildPayload("p1"))
        // capture is synchronous — event is immediately in queue
        assertEquals(1, service.queue.size())

        service.flush()
        advanceUntilIdle()

        assertEquals(0, service.queue.size())
        assertEquals(1, fakeSender.callCount)
    }

    @Test
    fun `5xx response retries and event survives until success`() = testScope.runTest {
        val fakeSender = FakeSender { callNum, _ ->
            if (callNum == 1) serverErrorResponse() else successResponse()
        }
        val service = createService(
            config = AnalyticsConfig(flushBatchSize = 10, flushIntervalMs = 10_000L),
            sender = fakeSender,
        )
        service.retryScheduleMs = listOf(10L, 20L)

        service.capture(DigiaExperienceEvent.Impressed, buildPayload("p1"))
        // capture is synchronous — do not advanceUntilIdle here (would advance clock to 10_000ms and fire timer)

        service.flush()
        runCurrent() // runs the flush dispatch at time=0 only; retry is scheduled in the future

        assertEquals(1, service.retryAttempt)
        assertEquals(1, service.queue.size())

        advanceTimeBy(100L)
        advanceUntilIdle() // retry fires → 200

        assertEquals(0, service.queue.size())
        assertEquals(0, service.retryAttempt)
        assertEquals(2, fakeSender.callCount)
    }

    @Test
    fun `lifecycle stop flushes pending events`() = testScope.runTest {
        val fakeSender = FakeSender { successResponse() }
        val service = createService(
            config = AnalyticsConfig(flushBatchSize = 10, flushIntervalMs = 10_000L),
            sender = fakeSender,
        )

        service.capture(DigiaExperienceEvent.Impressed, buildPayload("p1"))
        // capture is synchronous
        assertEquals(1, service.queue.size())

        service.onLifecycleStop()
        advanceUntilIdle()

        assertEquals(0, service.queue.size())
        assertEquals(1, fakeSender.callCount)
    }

    @Test
    fun `persisted queue flushes on next cold init`() = testScope.runTest {
        val store = InMemoryKeyValueStore()

        // First session — enqueue then simulate process death
        val service1 = createService(
            config = AnalyticsConfig(flushBatchSize = 10, flushIntervalMs = 10_000L),
            store = store,
        )
        service1.capture(DigiaExperienceEvent.Impressed, buildPayload("persisted"))
        // capture is synchronous — do not advanceUntilIdle (would fire timer and flush)
        assertEquals(1, service1.queue.size())
        service1.resetForTest()

        // Second session — re-init with same store, expects auto-flush via timer
        val fakeSender = FakeSender { successResponse() }
        createService(
            config = AnalyticsConfig(flushBatchSize = 10, flushIntervalMs = 50L),
            sender = fakeSender,
            store = store,
        )

        advanceTimeBy(200L)
        advanceUntilIdle()

        assertEquals(1, fakeSender.callCount)
        // queue belongs to service2 which shares the store
        assertEquals(0, AnalyticsQueue(store).size())
    }

    @Test
    fun `dismissed event queues but does not self-flush`() = testScope.runTest {
        val fakeSender = FakeSender { successResponse() }
        val service = createService(
            config = AnalyticsConfig(flushBatchSize = 100, flushIntervalMs = 10_000L),
            sender = fakeSender,
        )

        service.capture(DigiaExperienceEvent.Dismissed, buildPayload("p1"))
        // capture is synchronous — do not advanceUntilIdle (would fire timer)

        assertEquals(1, service.queue.size())
        assertEquals(0, fakeSender.callCount)
    }

    @Test
    fun `partial failure removes all batched events without retry`() = testScope.runTest {
        val fakeSender = FakeSender { _, _ ->
            // 207 partial failure — one accepted, one rejected
            SenderResult(207, """{"accepted":1,"rejected":1,"errors":[{"event_id":"any","reason":"invalid"}]}""")
        }
        val service = createService(
            config = AnalyticsConfig(flushBatchSize = 10, flushIntervalMs = 10_000L),
            sender = fakeSender,
        )

        service.capture(DigiaExperienceEvent.Impressed, buildPayload("p1"))
        service.capture(DigiaExperienceEvent.Clicked("cta"), buildPayload("p2"))
        // capture is synchronous

        service.flush()
        advanceUntilIdle()

        // Both events removed — partial failure is not retried
        assertEquals(0, service.queue.size())
        assertEquals(1, fakeSender.callCount)
    }
}

// ── Test doubles ─────────────────────────────────────────────────────────────

internal class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String, default: String?) = map[key] ?: default
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

internal class FakeSender(
    private val respond: (callNum: Int, body: String) -> SenderResult = { _, _ -> successResponse() },
) : AnalyticsSender {
    var callCount = 0

    // Convenience constructor for response-only lambdas
    constructor(respond: () -> SenderResult) : this({ _, _ -> respond() })

    override suspend fun post(
        url: String,
        jsonBody: String,
        headers: Map<String, String>,
    ): SenderResult = respond(++callCount, jsonBody)
}

private fun successResponse() =
    SenderResult(200, """{"accepted":1,"rejected":0,"errors":[]}""")

private fun serverErrorResponse() =
    SenderResult(500, """{"message":"internal server error"}""")
