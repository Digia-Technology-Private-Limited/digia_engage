package com.digia.engage.internal

import com.digia.engage.CEPTriggerPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigiaOverlayControllerTest {

    private fun payload(id: String) = CEPTriggerPayload(cepCampaignId = id, campaignKey = id)

    @Test
    fun `show sets activePayload`() {
        val controller = DigiaOverlayController()
        val payload = payload("p1")

        controller.show(payload)

        assertEquals(payload, controller.activePayload.value)
    }

    @Test
    fun `show replaces active payload`() {
        val controller = DigiaOverlayController()
        val payloadA = payload("p1")
        val payloadB = payload("p2")

        controller.show(payloadA)
        controller.show(payloadB)

        assertEquals(payloadB, controller.activePayload.value)
    }

    @Test
    fun `dismiss clears activePayload`() {
        val controller = DigiaOverlayController()
        val payload = payload("p1")

        controller.show(payload)
        controller.dismiss()

        assertNull(controller.activePayload.value)
    }

    @Test
    fun `addSlot stores payload by placementKey`() {
        val controller = DigiaOverlayController()
        val payload = payload("s1")

        controller.addSlot("hero", payload)

        assertEquals(payload, controller.slotPayloads.value["hero"])
    }

    @Test
    fun `removeSlotById removes matching payload`() {
        val controller = DigiaOverlayController()
        val payload = payload("s1")

        controller.addSlot("hero", payload)
        controller.removeSlotById("s1")

        assertEquals(emptyMap<String, CEPTriggerPayload>(), controller.slotPayloads.value)
    }

    @Test
    fun `removeSlotById no-op when id does not exist`() {
        val controller = DigiaOverlayController()
        val payload = payload("s1")

        controller.addSlot("hero", payload)
        val before = controller.slotPayloads.value
        controller.removeSlotById("missing")

        assertEquals(before, controller.slotPayloads.value)
    }

    @Test
    fun `removeSlotByKey removes matching placement key`() {
        val controller = DigiaOverlayController()
        controller.addSlot("hero", payload("s1"))
        controller.addSlot("footer", payload("s2"))

        controller.removeSlotByKey("hero")

        assertEquals(1, controller.slotPayloads.value.size)
        assertNull(controller.slotPayloads.value["hero"])
        assertEquals("s2", controller.slotPayloads.value["footer"]?.cepCampaignId)
    }

    @Test
    fun `dismiss with no active payload remains null`() {
        val controller = DigiaOverlayController()

        controller.dismiss()

        assertNull(controller.activePayload.value)
    }

    @Test
    fun `clearSlots removes all`() {
        val controller = DigiaOverlayController()
        controller.addSlot("a", payload("1"))
        controller.addSlot("b", payload("2"))

        controller.clearSlots()

        assertEquals(emptyMap<String, CEPTriggerPayload>(), controller.slotPayloads.value)
    }
}
