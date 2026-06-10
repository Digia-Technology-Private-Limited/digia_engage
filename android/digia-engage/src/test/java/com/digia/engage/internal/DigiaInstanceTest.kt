package com.digia.engage.internal

import com.digia.engage.DiagnosticReport
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.internal.model.CampaignModel
import com.digia.engage.internal.model.NudgeDisplayType
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
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
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "exp-1", type = "dialog")))

        plugin.delegate!!.onCampaignTriggered(
            InAppPayload(id = "exp-1", content = mapOf("campaign_key" to "exp-1")),
        )
        testScheduler.advanceUntilIdle()

        assertEquals("exp-1", DigiaInstance.controller.nudgeOverlay.value?.payload?.id)
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
        DigiaInstance.setCampaignsForTest(listOf(inlineCampaign(key = "slot-1", slotKey = "hero_banner")))

        plugin.delegate!!.onCampaignTriggered(
            InAppPayload(id = "slot-1", content = mapOf("campaign_key" to "slot-1")),
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
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "pending-1", type = "bottomSheet")))
        DigiaInstance.setSdkStateForTest(SDKState.INITIALIZING)

        plugin.delegate!!.onCampaignTriggered(
            InAppPayload(id = "pending-1", content = mapOf("campaign_key" to "pending-1")),
        )
        testScheduler.advanceUntilIdle()
        assertNull(DigiaInstance.controller.nudgeOverlay.value)

        DigiaInstance.setSdkStateForTest(SDKState.READY)
        DigiaInstance.flushPendingPayloadForTest()
        testScheduler.advanceUntilIdle()

        assertEquals("pending-1", DigiaInstance.controller.nudgeOverlay.value?.payload?.id)
    }

    @Test
    fun `markDismissed emits dismissed event and clears overlay`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "dialog-1", type = "dialog")))

        plugin.delegate!!.onCampaignTriggered(
            InAppPayload(id = "dialog-1", content = mapOf("campaign_key" to "dialog-1")),
        )
        testScheduler.advanceUntilIdle()

        DigiaInstance.reportNudgeImpression()
        DigiaInstance.markNudgeDismissed()

        assertNull(DigiaInstance.controller.nudgeOverlay.value)
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Dismissed })
    }

    @Test
    fun `emitExplicitCtaClick emits clicked event when active payload exists`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "dialog-1", type = "dialog")))

        plugin.delegate!!.onCampaignTriggered(
            InAppPayload(id = "dialog-1", content = mapOf("campaign_key" to "dialog-1")),
        )
        testScheduler.advanceUntilIdle()

        DigiaInstance.emitNudgeClick("cta-1")

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

    @Test
    fun `nudge campaign routes to nudge overlay channel with display style`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "welcome_nudge", type = "bottomSheet")))

        plugin.delegate!!.onCampaignTriggered(
            InAppPayload(id = "welcome_nudge", content = mapOf("campaign_key" to "welcome_nudge")),
        )
        testScheduler.advanceUntilIdle()

        val overlay = DigiaInstance.controller.nudgeOverlay.value
        assertNotNull(overlay)
        assertEquals("welcome_nudge", overlay!!.payload.id)
        assertEquals(NudgeDisplayType.BOTTOM_SHEET, overlay.config.surface.displayType)
        assertEquals("nudge", overlay.payload.content["campaign_type"])
        assertEquals("bottom_sheet", overlay.payload.content["display_style"])
        // Impression fires through the shared overlay path.
        DigiaInstance.reportNudgeImpression()
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Impressed })
    }

    @Test
    fun `markNudgeDismissed clears channel and emits dismissed`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "n1", type = "dialog")))

        plugin.delegate!!.onCampaignTriggered(
            InAppPayload(id = "n1", content = mapOf("campaign_key" to "n1")),
        )
        testScheduler.advanceUntilIdle()
        assertNotNull(DigiaInstance.controller.nudgeOverlay.value)

        DigiaInstance.markNudgeDismissed()

        assertNull(DigiaInstance.controller.nudgeOverlay.value)
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Dismissed })
    }

    private fun nudgeCampaign(key: String, type: String): CampaignModel =
        CampaignModel.fromJson(
            JSONObject(
                """
                {
                  "id": "$key-id",
                  "campaignKey": "$key",
                  "campaignType": "nudge",
                  "templateConfig": {
                    "container": { "displayType": "$type" },
                    "layout": {
                      "type": "digia/column",
                      "childGroups": { "children": [ { "type": "digia/text", "props": { "text": "Hi" } } ] }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )!!

    private fun inlineCampaign(key: String, slotKey: String): CampaignModel =
        CampaignModel.fromJson(
            JSONObject(
                """
                {
                  "id": "$key-id",
                  "campaignKey": "$key",
                  "campaignType": "inline",
                  "templateConfig": {
                    "slotKey": "$slotKey",
                    "items": [{ "imageUrl": "https://example.com/img.png" }]
                  }
                }
                """.trimIndent(),
            ),
        )!!

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
