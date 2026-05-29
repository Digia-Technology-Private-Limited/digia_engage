package com.digia.engage.internal

import com.digia.engage.InAppPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigiaOverlayControllerTest {

    @Test
    fun `show sets activePayload`() {
        val controller = DigiaOverlayController()
        val payload = InAppPayload("p1", mapOf("type" to "dialog", "componentId" to "w1"))

        controller.show(payload)

        assertEquals(payload, controller.activePayload.value)
    }

    @Test
    fun `show replaces active payload`() {
        val controller = DigiaOverlayController()
        val payloadA = InAppPayload("p1", mapOf("type" to "dialog", "componentId" to "w1"))
        val payloadB = InAppPayload("p2", mapOf("type" to "dialog", "componentId" to "w2"))

        controller.show(payloadA)
        controller.show(payloadB)

        assertEquals(payloadB, controller.activePayload.value)
    }

    @Test
    fun `dismiss clears activePayload`() {
        val controller = DigiaOverlayController()
        val payload = InAppPayload("p1", mapOf("type" to "dialog", "componentId" to "w1"))

        controller.show(payload)
        controller.dismiss()

        assertNull(controller.activePayload.value)
    }

    @Test
    fun `addSlot stores payload by placementKey`() {
        val controller = DigiaOverlayController()
        val payload = InAppPayload(
            "s1",
            mapOf("type" to "slot", "placementKey" to "hero", "componentId" to "c1"),
        )

        controller.addSlot("hero", payload)

        assertEquals(payload, controller.slotPayloads.value["hero"])
    }

    @Test
    fun `removeSlotById removes matching payload`() {
        val controller = DigiaOverlayController()
        val payload = InAppPayload(
            "s1",
            mapOf("type" to "slot", "placementKey" to "hero", "componentId" to "c1"),
        )

        controller.addSlot("hero", payload)
        controller.removeSlotById("s1")

        assertEquals(emptyMap<String, InAppPayload>(), controller.slotPayloads.value)
    }

    @Test
    fun `removeSlotById no-op when id does not exist`() {
        val controller = DigiaOverlayController()
        val payload = InAppPayload(
            "s1",
            mapOf("type" to "slot", "placementKey" to "hero", "componentId" to "c1"),
        )

        controller.addSlot("hero", payload)
        val before = controller.slotPayloads.value
        controller.removeSlotById("missing")

        assertEquals(before, controller.slotPayloads.value)
    }

    @Test
    fun `removeSlotByKey removes matching placement key`() {
        val controller = DigiaOverlayController()
        controller.addSlot("hero", InAppPayload("s1", mapOf("type" to "slot")))
        controller.addSlot("footer", InAppPayload("s2", mapOf("type" to "slot")))

        controller.removeSlotByKey("hero")

        assertEquals(1, controller.slotPayloads.value.size)
        assertNull(controller.slotPayloads.value["hero"])
        assertEquals("s2", controller.slotPayloads.value["footer"]?.id)
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
        controller.addSlot("a", InAppPayload("1", mapOf()))
        controller.addSlot("b", InAppPayload("2", mapOf()))

        controller.clearSlots()

        assertEquals(emptyMap<String, InAppPayload>(), controller.slotPayloads.value)
    }
}
