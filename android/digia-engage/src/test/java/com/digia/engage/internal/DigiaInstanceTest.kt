package com.digia.engage.internal

import com.digia.engage.DiagnosticReport
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.CEPTriggerPayload
import com.digia.engage.DigiaExperienceEvent
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
    fun `nudge campaign payload shows overlay`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "exp-1", type = "dialog")))

        plugin.delegate!!.onCampaignTriggered(trigger(key = "exp-1"))
        testScheduler.advanceUntilIdle()

        assertEquals("exp-1", DigiaInstance.controller.nudgeOverlay.value?.payload?.id)
    }

    @Test
    fun `unknown campaign key is dropped`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)

        // No campaign with this key is in the store → routing drops it.
        plugin.delegate!!.onCampaignTriggered(trigger(key = "not-published"))
        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.activePayload.value)
        assertNull(DigiaInstance.controller.nudgeOverlay.value)
    }

    @Test
    fun `inline payload stores slot by placement key`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(inlineCampaign(key = "slot-1", slotKey = "hero_banner")))

        plugin.delegate!!.onCampaignTriggered(trigger(key = "slot-1"))
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

        plugin.delegate!!.onCampaignTriggered(trigger(key = "pending-1"))
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

        plugin.delegate!!.onCampaignTriggered(trigger(key = "dialog-1"))
        testScheduler.advanceUntilIdle()

        DigiaInstance.reportNudgeImpression()
        DigiaInstance.markNudgeDismissed()

        assertNull(DigiaInstance.controller.nudgeOverlay.value)
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Dismissed })
    }

    @Test
    fun `emitNudgeClick emits clicked event with synthesised element id`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "dialog-1", type = "dialog")))

        plugin.delegate!!.onCampaignTriggered(trigger(key = "dialog-1"))
        testScheduler.advanceUntilIdle()

        DigiaInstance.emitNudgeClick(label = "Shop now", isPrimary = true, actions = emptyList())

        // CEP receives the coarse Clicked with the synthesised cta_* element id.
        val clicked = plugin.events.first { it.first is DigiaExperienceEvent.Clicked }.first as DigiaExperienceEvent.Clicked
        assertEquals("cta_primary", clicked.elementId)
    }

    @Test
    fun `onCampaignInvalidated clears nudge overlay and slot payload`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(
            listOf(
                nudgeCampaign(key = "dialog-1", type = "dialog"),
                inlineCampaign(key = "slot-1", slotKey = "hero"),
            ),
        )

        plugin.delegate!!.onCampaignTriggered(trigger(key = "dialog-1"))
        plugin.delegate!!.onCampaignTriggered(trigger(key = "slot-1"))
        testScheduler.advanceUntilIdle()
        assertNotNull(DigiaInstance.controller.nudgeOverlay.value)
        assertEquals("slot-1", DigiaInstance.controller.slotPayloads.value["hero"]?.id)

        plugin.delegate!!.onCampaignInvalidated("dialog-1")
        plugin.delegate!!.onCampaignInvalidated("slot-1")
        testScheduler.advanceUntilIdle()

        assertNull(DigiaInstance.controller.nudgeOverlay.value)
        assertNull(DigiaInstance.controller.activePayload.value)
        assertTrue(DigiaInstance.controller.slotPayloads.value.isEmpty())
    }

    @Test
    fun `nudge campaign routes to nudge overlay channel with display style`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(nudgeCampaign(key = "welcome_nudge", type = "bottomSheet")))

        plugin.delegate!!.onCampaignTriggered(trigger(key = "welcome_nudge"))
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

        plugin.delegate!!.onCampaignTriggered(trigger(key = "n1"))
        testScheduler.advanceUntilIdle()
        assertNotNull(DigiaInstance.controller.nudgeOverlay.value)

        DigiaInstance.markNudgeDismissed()

        assertNull(DigiaInstance.controller.nudgeOverlay.value)
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Dismissed })
    }

    @Test
    fun `survey start impresses to CEP but question and answer stay off the CEP`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(surveyCampaign(key = "nps-survey")))

        plugin.delegate!!.onCampaignTriggered(trigger(key = "nps-survey"))
        testScheduler.advanceUntilIdle()

        DigiaInstance.reportSurveyStarted()
        DigiaInstance.reportSurveyQuestionViewed("q1")
        DigiaInstance.reportSurveyAnswered("q1", mapOf("value" to 9))

        // CEP sees the coarse container impression …
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Impressed })
        // … but never the per-question / answer signals (Clicked is Digia-only here).
        assertTrue(plugin.events.none { it.first is DigiaExperienceEvent.Clicked })
    }

    @Test
    fun `survey dismiss emits coarse dismissed to CEP and clears the survey`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(surveyCampaign(key = "nps-survey")))

        plugin.delegate!!.onCampaignTriggered(trigger(key = "nps-survey"))
        testScheduler.advanceUntilIdle()

        DigiaInstance.markSurveyDismissed()

        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Dismissed })
    }

    @Test
    fun `guide view impresses to CEP and completion dismisses to CEP`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(guideCampaign(key = "onboarding")))

        plugin.delegate!!.onCampaignTriggered(trigger(key = "onboarding"))
        testScheduler.advanceUntilIdle()

        DigiaInstance.reportGuideViewed()
        DigiaInstance.reportGuideStepViewed(0)
        DigiaInstance.completeGuide()

        // Container view → CEP Impressed; completion → CEP Dismissed once.
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Impressed })
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Dismissed })
        // Per-step signals never reach the CEP (Clicked stays Digia-only).
        assertTrue(plugin.events.none { it.first is DigiaExperienceEvent.Clicked })
    }

    @Test
    fun `dismissing a guide emits coarse dismissed and clears guide state`() = runTest(testDispatcher) {
        val plugin = FakePlugin()
        DigiaInstance.initForTest()
        DigiaInstance.register(plugin)
        DigiaInstance.setCampaignsForTest(listOf(guideCampaign(key = "onboarding")))

        plugin.delegate!!.onCampaignTriggered(trigger(key = "onboarding"))
        testScheduler.advanceUntilIdle()
        assertNotNull(DigiaInstance.guideState.value)

        DigiaInstance.reportGuideViewed()
        DigiaInstance.dismissGuide()

        assertNull(DigiaInstance.guideState.value)
        assertTrue(plugin.events.any { it.first is DigiaExperienceEvent.Dismissed })
    }

    /** Builds the CEP→core trigger contract the way a plugin delivers it. */
    private fun trigger(key: String): CEPTriggerPayload =
        CEPTriggerPayload(cepCampaignId = key, campaignKey = key)

    private fun guideCampaign(key: String): CampaignModel =
        CampaignModel.fromJson(
            JSONObject(
                """
                {
                  "id": "$key-id",
                  "campaignKey": "$key",
                  "campaignType": "guide",
                  "templateConfig": {
                    "templateType": "tooltip",
                    "steps": [
                      { "anchorKey": "fab", "title": { "text": "Tap here" } }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!

    private fun surveyCampaign(key: String): CampaignModel =
        CampaignModel.fromJson(
            JSONObject(
                """
                {
                  "id": "$key-id",
                  "campaignKey": "$key",
                  "campaignType": "survey",
                  "templateConfig": {
                    "templateType": "survey",
                    "rootNodeId": "n1",
                    "blocks": [
                      { "id": "b1", "type": "nps", "title": { "text": "How likely?" } }
                    ],
                    "nodes": [
                      { "id": "n1", "blockId": "b1" }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!

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
        val events = mutableListOf<Pair<DigiaExperienceEvent, CEPTriggerPayload>>()

        override fun setup(delegate: DigiaCEPDelegate) {
            setupCalled = true
            this.delegate = delegate
        }

        override fun forwardScreen(name: String) {
            forwardedScreens += name
        }

        override fun notifyEvent(event: DigiaExperienceEvent, payload: CEPTriggerPayload) {
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
}
