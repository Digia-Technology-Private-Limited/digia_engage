package com.digia.engage.internal

import com.digia.engage.DiagnosticReport
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `register wires setup with delegate and runs health check`() {
        val plugin = FakePlugin()

        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        assertTrue(plugin.setupCalled)
        assertNotNull(plugin.delegate)
        assertTrue(plugin.healthChecked)
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
    fun `setCurrentScreen forwards screen to plugin`() {
        val plugin = FakePlugin()

        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCurrentScreen("checkout")

        assertEquals(listOf("checkout"), plugin.forwardedScreens)
    }

    @Test
    fun `matching screen nudge payload shows overlay`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCurrentScreen("home")

        plugin.delegate!!.onCampaignTriggered(
            nudgePayload(id = "exp-1", screenId = "home", command = "SHOW_DIALOG"),
        )
        testScheduler.advanceUntilIdle()

        assertEquals("exp-1", DigiaInstance.controller.activePayload.value?.id)
    }

    @Test
    fun `mismatched screen payload is dropped`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCurrentScreen("checkout")

        plugin.delegate!!.onCampaignTriggered(
            nudgePayload(id = "exp-1", screenId = "home", command = "SHOW_DIALOG"),
        )
        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.activePayload.value)
    }

    @Test
    fun `inline payload stores slot by placement key`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCurrentScreen("home")

        plugin.delegate!!.onCampaignTriggered(
            inlinePayload(
                id = "slot-1",
                screenId = "home",
                placementKey = "hero_banner",
                componentId = "hero_component",
            ),
        )
        testScheduler.advanceUntilIdle()

        assertEquals("slot-1", DigiaInstance.controller.slotPayloads.value["hero_banner"]?.id)
        assertNull(DigiaInstance.controller.activePayload.value)
    }

    @Test
    fun `pending nudge payload is buffered during initializing and flushed on ready`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCurrentScreen("home")
        DigiaInstance.setSdkStateForTest(SDKState.INITIALIZING)

        plugin.delegate!!.onCampaignTriggered(
            nudgePayload(id = "pending-1", screenId = "home", command = "SHOW_BOTTOM_SHEET"),
        )
        testScheduler.advanceUntilIdle()
        assertNull(DigiaInstance.controller.activePayload.value)

        DigiaInstance.setSdkStateForTest(SDKState.READY)
        DigiaInstance.flushPendingPayloadForTest()
        testScheduler.advanceUntilIdle()

        assertEquals("pending-1", DigiaInstance.controller.activePayload.value?.id)
    }

    @Test
    fun `markDismissed emits dismissed event and clears overlay`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCurrentScreen("home")

        plugin.delegate!!.onCampaignTriggered(
            nudgePayload(id = "dialog-1", screenId = "home", command = "SHOW_DIALOG"),
        )
        testScheduler.advanceUntilIdle()

        DigiaInstance.reportOverlayImpression(
            DigiaInstance.controller.activePayload.value!!,
        )
        DigiaInstance.markOverlayDismissed("dialog-1")

        assertNull(DigiaInstance.controller.activePayload.value)
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Dismissed })
    }

    @Test
    fun `emitExplicitCtaClick emits clicked event when active payload exists`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCurrentScreen("home")

        plugin.delegate!!.onCampaignTriggered(
            nudgePayload(id = "dialog-1", screenId = "home", command = "SHOW_DIALOG"),
        )
        testScheduler.advanceUntilIdle()

        DigiaInstance.emitExplicitCtaClick("cta-1")

        val clicked = plugin.events.first { it.first is DigiaExperienceEvent.Clicked }.first as DigiaExperienceEvent.Clicked
        assertEquals("cta-1", clicked.elementId)
    }

    @Test
    fun `onCampaignInvalidated clears active and slot payload`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCurrentScreen("home")

        plugin.delegate!!.onCampaignTriggered(
            nudgePayload(id = "dialog-1", screenId = "home", command = "SHOW_DIALOG"),
        )
        plugin.delegate!!.onCampaignTriggered(
            inlinePayload(
                id = "slot-1",
                screenId = "home",
                placementKey = "hero",
                componentId = "comp-1",
            ),
        )
        testScheduler.advanceUntilIdle()

        plugin.delegate!!.onCampaignInvalidated("dialog-1")
        plugin.delegate!!.onCampaignInvalidated("slot-1")
        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.activePayload.value)
        assertTrue(DigiaInstance.controller.slotPayloads.value.isEmpty())
    }

    private class FakePlugin : DigiaCEPPlugin {
        override val identifier: String = "fake"
        var setupCalled = false
        var teardownCalled = false
        var healthChecked = false
        var delegate: DigiaCEPDelegate? = null
        val forwardedScreens = mutableListOf<String>()
        val events = mutableListOf<Pair<DigiaExperienceEvent, InAppPayload>>()

        override fun setup(delegate: DigiaCEPDelegate) {
            setupCalled = true
            this.delegate = delegate
        }

        override fun forwardScreen(name: String) {
            forwardedScreens += name
        }

        override fun notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
            events += event to payload
        }

        override fun healthCheck(): DiagnosticReport {
            healthChecked = true
            return DiagnosticReport(isHealthy = true)
        }

        override fun teardown() {
            teardownCalled = true
        }
    }

    private fun nudgePayload(
        id: String,
        screenId: String,
        command: String,
    ): InAppPayload = InAppPayload(
        id = id,
        content = mapOf(
            "command" to command,
            "viewId" to "welcome_modal",
            "screenId" to screenId,
            "args" to mapOf("title" to "Hello"),
        ),
    )

    private fun inlinePayload(
        id: String,
        screenId: String,
        placementKey: String,
        componentId: String,
    ): InAppPayload = InAppPayload(
        id = id,
        content = mapOf(
            "command" to "SHOW_INLINE",
            "screenId" to screenId,
            "placementKey" to placementKey,
            "componentId" to componentId,
            "args" to mapOf("style" to "compact"),
        ),
    )
}
