package com.digia.engage.internal.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Locks the nudge wire contract against the new dashboard format (Flutter parity).
 * Parses via NudgeParser which mirrors Flutter's nudge_parser.dart.
 */
class NudgeConfigTest {

    private fun minimalConfig(displayType: String = "bottom_sheet") = JSONObject(
        """
        {
          "container": {
            "displayType": "$displayType",
            "cornerRadius": 18,
            "padding": 20,
            "widthPct": 86,
            "showHandle": true,
            "draggable": true,
            "backdropDismissible": true
          },
          "layout": {
            "type": "digia/column",
            "props": { "spacing": 8 },
            "children": [
              {
                "type": "digia/text",
                "props": { "text": "Hello" },
                "containerProps": { "style": {} }
              }
            ]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `parses bottom sheet surface with flutter defaults`() {
        val config = NudgeParser().parse(minimalConfig())
        assertNotNull(config)
        config!!
        val surface = config.surface
        assertEquals(NudgeDisplayType.BOTTOM_SHEET, surface.displayType)
        assertEquals("bottom_sheet", surface.displayType.displayStyle)
        assertEquals(18f, surface.cornerRadius)
        assertEquals(20f, surface.padding)
        assertEquals(true, surface.showHandle)
        assertEquals(true, surface.draggable)
        assertEquals(true, surface.backdropDismissible)
        assertEquals(false, surface.showCloseButton)
        assertEquals(0.86f, surface.widthFraction, 0.01f)
    }

    @Test
    fun `parses dialog display type`() {
        val config = NudgeParser().parse(minimalConfig("dialog"))!!
        assertEquals(NudgeDisplayType.DIALOG, config.surface.displayType)
        assertEquals("dialog", config.surface.displayType.displayStyle)
    }

    @Test
    fun `surface defaults when container omitted`() {
        val json = JSONObject("""{"layout":{"type":"digia/column","children":[]}}""")
        val config = NudgeParser().parse(json)!!
        val s = config.surface
        assertEquals(NudgeDisplayType.BOTTOM_SHEET, s.displayType)
        assertEquals(18f, s.cornerRadius)
        assertEquals(20f, s.padding)
        assertEquals(0.86f, s.widthFraction, 0.01f)
        assertTrue(s.showHandle)
        assertTrue(s.backdropDismissible)
        assertFalse(s.showCloseButton)
    }

    @Test
    fun `returns null when layout missing`() {
        assertNull(NudgeParser().parse(JSONObject("""{"container":{}}""")))
    }

    @Test
    fun `parses text node`() {
        val config = NudgeParser().parse(minimalConfig())!!
        val text = config.content.children.first() as NudgeText
        assertEquals("Hello", text.text)
        assertEquals(NudgeTextAlign.LEFT, text.align)
    }

    @Test
    fun `parses widthPct as widthFraction`() {
        val json = JSONObject("""
            {
              "container": { "widthPct": 70 },
              "layout": { "type": "digia/column", "children": [] }
            }
        """.trimIndent())
        val config = NudgeParser().parse(json)!!
        assertEquals(0.70f, config.surface.widthFraction, 0.01f)
    }

    @Test
    fun `widthFraction clamped to 0_3 minimum`() {
        val json = JSONObject("""
            {
              "container": { "widthPct": 10 },
              "layout": { "type": "digia/column", "children": [] }
            }
        """.trimIndent())
        val config = NudgeParser().parse(json)!!
        assertEquals(0.3f, config.surface.widthFraction, 0.01f)
    }

    @Test
    fun `parses full node set`() {
        val json = JSONObject(
            """
            {
              "layout": {
                "type": "digia/column",
                "props": {},
                "children": [
                  { "type": "digia/text", "props": { "text": "Hi" }, "containerProps": {} },
                  { "type": "digia/image", "props": { "src": { "imageSrc": "https://x/y.png" } }, "containerProps": {} },
                  { "type": "digia/button", "props": { "text": { "text": "Go" }, "isPrimary": true }, "containerProps": {} },
                  { "type": "fw/sized_box", "props": { "height": 8 }, "containerProps": {} },
                  { "type": "digia/styledHorizontalDivider", "props": { "thickness": 1 }, "containerProps": {} },
                  { "type": "digia/lottie", "props": { "src": { "lottiePath": "https://x/a.json" }, "height": 160 }, "containerProps": {} },
                  { "type": "digia/carousel", "props": { "images": ["https://x/1.png"] }, "containerProps": {} },
                  { "type": "digia/videoPlayer", "props": { "url": "https://x/v.mp4" }, "containerProps": {} }
                ]
              }
            }
            """.trimIndent(),
        )
        val config = NudgeParser().parse(json)!!
        val types = config.content.children.map { it::class.simpleName }
        assertEquals(
            listOf("NudgeText", "NudgeImage", "NudgeButton", "NudgeGap",
                "NudgeDivider", "NudgeLottie", "NudgeCarousel", "NudgeVideo"),
            types,
        )
        val button = config.content.children[2] as NudgeButton
        assertTrue(button.isPrimary)
        val image = config.content.children[1] as NudgeImage
        assertEquals("https://x/y.png", image.url)
    }

    @Test
    fun `unknown node type is dropped`() {
        val json = JSONObject("""
            {
              "layout": {
                "type": "digia/column",
                "children": [
                  { "type": "unknown/widget", "props": {}, "containerProps": {} }
                ]
              }
            }
        """.trimIndent())
        val config = NudgeParser().parse(json)!!
        assertTrue(config.content.children.isEmpty())
    }
}
