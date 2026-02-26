package com.digia.engage.internal

import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DigiaInstanceTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        DigiaInstance.resetForTest()
    }

    @After
    fun tearDown() {
        DigiaInstance.teardown()
        Dispatchers.resetMain()
    }

    @Test
    fun `register wires setup with delegate`() {
        val plugin = FakePlugin()

        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        assertTrue(plugin.setupCalled)
        assertNotNull(plugin.delegate)
    }

    @Test
    fun `register replacement tears down previous plugin`() {
        val first = FakePlugin()
        val second = FakePlugin()

        DigiaInstance.initForTest()
        DigiaInstance.register(first)
        DigiaInstance.register(second)

        assertTrue(first.setupCalled)
        assertTrue(first.teardownCalled)
        assertTrue(second.setupCalled)
    }

    @Test
    fun `setCurrentScreen before register is forwarded when plugin registers later`() {
        val plugin = FakePlugin()

        DigiaInstance.initForTest()
        DigiaInstance.setCurrentScreen("checkout")
        DigiaInstance.register(plugin)

        assertEquals(listOf("checkout"), plugin.forwardedScreens)
    }

    @Test
    fun `valid overlay payload shows and emits impressed`() = runTest(testDispatcher) {
        val plugin = FakePlugin()

        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        val payload = InAppPayload(
            id = "exp-1",
            content = mapOf(
                "type" to "dialog",
                "componentId" to "welcome_modal",
                "args" to mapOf("title" to "Hi"),
            ),
        )
        plugin.delegate!!.onExperienceReady(payload)

        testScheduler.advanceUntilIdle()

        assertNotNull(DigiaInstance.controller.activePayload.value)
        val event = plugin.events.single()
        assertTrue(event.first is DigiaExperienceEvent.Impressed)
        assertEquals("exp-1", event.second.id)
    }

    @Test
    fun `slot payload is stored in slotPayloads`() = runTest(testDispatcher) {
        val plugin = FakePlugin()

        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        val payload = InAppPayload(
            id = "slot-1",
            content = mapOf(
                "type" to "slot",
                "placementKey" to "hero_banner",
                "componentId" to "hero_component",
            ),
        )
        plugin.delegate!!.onExperienceReady(payload)

        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.activePayload.value)
        assertEquals("slot-1", DigiaInstance.controller.slotPayloads.value["hero_banner"]?.id)
    }

    @Test
    fun `experience is dropped when plugin not registered`() = runTest(testDispatcher) {
        DigiaInstance.initForTest()

        DigiaInstance.onExperienceReady(
            InAppPayload(
                id = "exp-1",
                content = mapOf("type" to "dialog", "componentId" to "welcome"),
            ),
        )

        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.activePayload.value)
        assertTrue(DigiaInstance.controller.slotPayloads.value.isEmpty())
    }

    @Test
    fun `bottom_sheet payload shows and emits impressed`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        val payload = InAppPayload(
            id = "exp-bs-1",
            content = mapOf(
                "type" to "bottom_sheet",
                "componentId" to "checkout_sheet",
            ),
        )
        plugin.delegate!!.onExperienceReady(payload)
        testScheduler.advanceUntilIdle()

        assertNotNull(DigiaInstance.controller.activePayload.value)
        assertEquals("exp-bs-1", DigiaInstance.controller.activePayload.value?.id)
        assertTrue(plugin.events.single().first is DigiaExperienceEvent.Impressed)
    }

    @Test
    fun `unknown payload type is dropped and does not emit impressed`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        plugin.delegate!!.onExperienceReady(
            InAppPayload(
                id = "exp-unknown",
                content = mapOf("type" to "fullscreen", "componentId" to "splash"),
            ),
        )
        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.activePayload.value)
        assertTrue(plugin.events.isEmpty())
    }

    @Test
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        DigiaInstance.onExperienceReady(
            InAppPayload(
                id = "exp-invalid-dialog",
                content = mapOf("type" to "dialog"),
            ),
        )

        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.activePayload.value)
        assertTrue(plugin.events.isEmpty())
    }

    @Test
    fun `invalid slot payload is dropped`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        DigiaInstance.onExperienceReady(
            InAppPayload(
                id = "exp-invalid-slot",
                content = mapOf("type" to "slot", "componentId" to "hero_component"),
            ),
        )

        testScheduler.advanceUntilIdle()

        assertTrue(DigiaInstance.controller.slotPayloads.value.isEmpty())
        assertNull(DigiaInstance.controller.activePayload.value)
    }

    @Test
    fun `markDismissed emits dismissed and clears active payload`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        val payload = InAppPayload(
            id = "dialog-1",
            content = mapOf("type" to "dialog", "componentId" to "welcome_modal"),
        )
        DigiaInstance.onExperienceReady(payload)
        testScheduler.advanceUntilIdle()

        DigiaInstance.markDismissed("dialog-1")

        assertNull(DigiaInstance.controller.activePayload.value)
        assertEquals(2, plugin.events.size)
        assertTrue(plugin.events[0].first is DigiaExperienceEvent.Impressed)
        assertTrue(plugin.events[1].first is DigiaExperienceEvent.Dismissed)
    }

    @Test
    fun `markDismissed with non matching id does not emit dismissed`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.onExperienceReady(
            InAppPayload(
                id = "dialog-1",
                content = mapOf("type" to "dialog", "componentId" to "welcome_modal"),
            ),
        )
        testScheduler.advanceUntilIdle()

        DigiaInstance.markDismissed("another-id")

        assertNotNull(DigiaInstance.controller.activePayload.value)
        assertEquals(1, plugin.events.size)
        assertTrue(plugin.events.single().first is DigiaExperienceEvent.Impressed)
    }

    @Test
    fun `emitExplicitCtaClick emits clicked when active payload exists`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.onExperienceReady(
            InAppPayload(
                id = "dialog-1",
                content = mapOf("type" to "dialog", "componentId" to "welcome_modal"),
            ),
        )
        testScheduler.advanceUntilIdle()

        DigiaInstance.emitExplicitCtaClick("cta-1")

        assertEquals(2, plugin.events.size)
        assertTrue(plugin.events[1].first is DigiaExperienceEvent.Clicked)
        val clicked = plugin.events[1].first as DigiaExperienceEvent.Clicked
        assertEquals("cta-1", clicked.elementId)
    }

    @Test
    fun `emitExplicitCtaClick is no op when no active payload`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        DigiaInstance.emitExplicitCtaClick("cta-1")

        assertFalse(plugin.events.any { it.first is DigiaExperienceEvent.Clicked })
    }

    @Test
    fun `onExperienceInvalidated clears active and slot`() = runTest(testDispatcher) {
        val plugin = FakePlugin()

        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        plugin.delegate!!.onExperienceReady(
            InAppPayload(
                id = "dialog-1",
                content = mapOf("type" to "dialog", "componentId" to "w1"),
            ),
        )
        plugin.delegate!!.onExperienceReady(
            InAppPayload(
                id = "slot-1",
                content = mapOf(
                    "type" to "slot",
                    "placementKey" to "hero",
                    "componentId" to "c1",
                ),
            ),
        )

        testScheduler.advanceUntilIdle()

        plugin.delegate!!.onExperienceInvalidated("dialog-1")
        plugin.delegate!!.onExperienceInvalidated("slot-1")

        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.activePayload.value)
        assertTrue(DigiaInstance.controller.slotPayloads.value.isEmpty())
    }

    private class FakePlugin : DigiaCEPPlugin {
        override val identifier: String = "fake"
        var setupCalled = false
        var teardownCalled = false
        var delegate: DigiaCEPDelegate? = null
        val forwardedScreens = mutableListOf<String>()
        val events = mutableListOf<Pair<DigiaExperienceEvent, InAppPayload>>()

        override fun setup(delegate: DigiaCEPDelegate) {
            setupCalled = true
            this.delegate = delegate
        }

        override fun forwardScreenEvent(name: String) {
            forwardedScreens += name
        }

        override fun notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
            events += event to payload
        }

        override fun teardown() {
            teardownCalled = true
        }
    }
}
