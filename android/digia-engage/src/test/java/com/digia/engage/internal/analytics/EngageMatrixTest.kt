package com.digia.engage.internal.analytics

import com.digia.engage.internal.model.DismissAction
import com.digia.engage.internal.model.OpenDeeplinkAction
import com.digia.engage.internal.model.OpenUrlAction
import com.digia.engage.internal.model.ShareAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-builder tests mirroring the Flutter `engage_matrix_test.dart`. */
class EngageMatrixTest {

    @Test
    fun `nudgeClicked synthesises element_id and resolves first action`() {
        val props = EngageMatrix.nudgeClicked(
            label = "Shop now",
            isPrimary = true,
            actions = listOf(OpenUrlAction("https://x.test")),
            timeToActionMs = 1200L,
        )

        assertEquals("cta_primary", props["element_id"])
        assertEquals("Shop now", props["cta_label"])
        assertEquals("primary", props["cta_position"])
        assertEquals("url", props["action_type"])
        assertEquals("https://x.test", props["action_url"])
        assertEquals(1200L, props["time_to_action_ms"])
    }

    @Test
    fun `nudgeClicked secondary without actions omits action fields`() {
        val props = EngageMatrix.nudgeClicked(
            label = "Later",
            isPrimary = false,
            actions = emptyList(),
        )

        assertEquals("cta_secondary", props["element_id"])
        assertEquals("secondary", props["cta_position"])
        assertFalse(props.containsKey("action_type"))
        assertFalse(props.containsKey("action_url"))
        assertFalse(props.containsKey("time_to_action_ms"))
    }

    @Test
    fun `dismiss and share actions map to tokens without urls`() {
        assertEquals("dismiss", EngageMatrix.actionTypeToken(DismissAction))
        assertEquals("custom", EngageMatrix.actionTypeToken(ShareAction("hi")))
        assertEquals("deeplink", EngageMatrix.actionTypeToken(OpenDeeplinkAction("app://x")))
        assertEquals(null, EngageMatrix.actionUrlOf(DismissAction))
        assertEquals("app://x", EngageMatrix.actionUrlOf(OpenDeeplinkAction("app://x")))
    }

    @Test
    fun `nudgeViewed includes context only when known`() {
        val bare = EngageMatrix.nudgeViewed(displayStyle = "dialog")
        assertEquals("dialog", bare["display_style"])
        assertFalse(bare.containsKey("screen_name"))
        assertFalse(bare.containsKey("trigger_type"))

        val full = EngageMatrix.nudgeViewed(
            displayStyle = "bottom_sheet",
            screenName = "Home",
            triggerType = "event",
            triggerEvent = "add_to_cart",
        )
        assertEquals("Home", full["screen_name"])
        assertEquals("event", full["trigger_type"])
        assertEquals("add_to_cart", full["trigger_event"])
    }

    @Test
    fun `containerViewed carries display style and item total`() {
        val props = EngageMatrix.containerViewed(displayStyle = "carousel", itemTotal = 3)
        assertEquals("carousel", props["display_style"])
        assertEquals(3, props["item_total"])
    }

    @Test
    fun `step carries index, total and optional click action`() {
        val viewed = EngageMatrix.step(displayStyle = "carousel", itemIndex = 0, itemTotal = 4)
        assertEquals(0, viewed["item_index"])
        assertEquals(4, viewed["item_total"])
        assertFalse(viewed.containsKey("action_type"))

        val clicked = EngageMatrix.step(
            displayStyle = "carousel",
            itemIndex = 2,
            itemTotal = 4,
            actionType = "deeplink",
            actionUrl = "app://pdp/5",
        )
        assertEquals("deeplink", clicked["action_type"])
        assertEquals("app://pdp/5", clicked["action_url"])
    }

    @Test
    fun `completed includes time only when known`() {
        val bare = EngageMatrix.completed(displayStyle = "story", itemTotal = 5)
        assertFalse(bare.containsKey("time_to_complete_ms"))

        val timed = EngageMatrix.completed(displayStyle = "story", itemTotal = 5, timeToCompleteMs = 8000L)
        assertEquals(8000L, timed["time_to_complete_ms"])
    }

    @Test
    fun `question carries index, total and server identity`() {
        val props = EngageMatrix.question(
            questionIndex = 1,
            questionTotal = 6,
            questionId = "q-nps",
            questionType = "nps",
        )
        assertEquals(1, props["item_index"])
        assertEquals(6, props["item_total"])
        assertEquals("q-nps", props["question_id"])
        assertEquals("nps", props["question_type"])
        assertTrue(props.containsKey("question_type"))
    }
}
