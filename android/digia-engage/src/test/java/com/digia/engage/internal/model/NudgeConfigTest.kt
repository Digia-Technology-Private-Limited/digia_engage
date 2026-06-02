package com.digia.engage.internal.model

import com.digia.engage.framework.models.VWNodeData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the nudge wire contract: the dashboard serializes `templateConfig.layout` as the
 * exact native DUI `VWData` tree, and this confirms the SDK parses those key names
 * (`containerProps`, `childGroups.children`, `digia/*` / `fw/*` type strings) into the
 * recursive renderer's data model without translation.
 */
class NudgeConfigTest {

    /** A bottom-sheet layout: root column + one of each of the 5 supported leaf widgets. */
    private fun sampleTemplateConfig(): JSONObject = JSONObject(
        """
        {
          "templateType": "bottomSheet",
          "container": {
            "bgColor": "#FFFFFF",
            "cornerRadius": 20,
            "padding": 12,
            "dismissOnOutsideTap": false,
            "scrimColor": "#80000000",
            "maxHeightRatio": 0.6,
            "dragHandle": false
          },
          "layout": {
            "type": "digia/column",
            "containerProps": { "style": {} },
            "childGroups": {
              "children": [
                { "type": "digia/text", "props": { "text": "Hi" } },
                { "type": "digia/image", "props": { "src": { "imageSrc": "http://x/y.png" } } },
                { "type": "digia/button", "props": { "text": { "text": "Go" } } },
                { "type": "fw/sized_box", "props": { "height": 8 } },
                { "type": "digia/styledHorizontalDivider", "props": { "thickness": 1 } }
              ]
            }
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `parses templateType container and layout tree`() {
        val config = NudgeConfig.fromJson(sampleTemplateConfig())
        assertNotNull(config)
        config!!

        assertEquals(NudgeTemplateType.BOTTOM_SHEET, config.templateType)
        assertEquals("bottom_sheet", config.templateType.displayStyle)

        // Container fields round-trip from the wire.
        assertEquals("#FFFFFF", config.container.bgColor)
        assertEquals(20f, config.container.cornerRadius)
        assertEquals(false, config.container.dismissOnOutsideTap)
        assertEquals("#80000000", config.container.scrimColor)
        assertEquals(0.6f, config.container.maxHeightRatio)
        assertEquals(false, config.container.dragHandle)

        // Root is a digia/column whose `children` group holds the 5 leaf widgets in order.
        val root = config.layout as VWNodeData
        assertEquals("digia/column", root.type)
        val children = root.childGroups?.get("children")
        assertNotNull(children)
        assertEquals(
            listOf(
                "digia/text",
                "digia/image",
                "digia/button",
                "fw/sized_box",
                "digia/styledHorizontalDivider",
            ),
            children!!.map { (it as VWNodeData).type },
        )
    }

    @Test
    fun `dialog templateType maps to dialog display style`() {
        val json = sampleTemplateConfig().put("templateType", "dialog")
        val config = NudgeConfig.fromJson(json)!!
        assertEquals(NudgeTemplateType.DIALOG, config.templateType)
        assertEquals("dialog", config.templateType.displayStyle)
    }

    @Test
    fun `container defaults applied when omitted`() {
        val json = sampleTemplateConfig()
        json.remove("container")
        val config = NudgeConfig.fromJson(json)!!
        val defaults = NudgeContainerConfig()
        assertEquals(defaults.bgColor, config.container.bgColor)
        assertEquals(defaults.cornerRadius, config.container.cornerRadius)
        assertTrue(config.container.dismissOnOutsideTap)
        assertEquals(defaults.maxHeightRatio, config.container.maxHeightRatio)
    }

    @Test
    fun `returns null when layout is missing`() {
        val json = sampleTemplateConfig()
        json.remove("layout")
        assertNull(NudgeConfig.fromJson(json))
    }
}
