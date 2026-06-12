package com.digia.engage.internal.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes a nudge `templateConfig` ({ container, layout }) into [NudgeConfig].
 * Direct port of Flutter nudge_parser.dart — single place that knows the wire format.
 * No Android/Compose imports; pure data construction only.
 */
internal class NudgeParser {

    fun parse(templateConfig: JSONObject): NudgeConfig? {
        val layout = templateConfig.optJSONObject("layout") ?: return null
        return NudgeConfig(
            surface = parseSurface(templateConfig.optJSONObject("container")),
            content = parseColumn(layout),
        )
    }

    // ── surface ───────────────────────────────────────────────────────────────

    private fun parseSurface(json: JSONObject?): NudgeSurface {
        val j = json ?: JSONObject()
        val widthPct = j.optDouble("widthPct", 86.0)
        return NudgeSurface(
            displayType = NudgeDisplayType.fromString(j.optString("displayType")),
            backgroundColor = j.optString("backgroundColor").ifBlank { null },
            barrierColor = j.optString("barrierColor").ifBlank { null },
            cornerRadius = j.optDouble("cornerRadius", 18.0).toFloat(),
            padding = j.optDouble("padding", 20.0).toFloat(),
            backdropDismissible = j.optBoolean("backdropDismissible", true),
            showCloseButton = j.optBoolean("showCloseButton", false),
            showHandle = j.optBoolean("showHandle", true),
            draggable = j.optBoolean("draggable", true),
            widthFraction = (widthPct / 100.0).toFloat().coerceIn(0.3f, 1.0f),
        )
    }

    // ── column ────────────────────────────────────────────────────────────────

    private fun parseColumn(layout: JSONObject): NudgeColumn {
        val props = layout.optJSONObject("props") ?: JSONObject()
        return NudgeColumn(
            crossAxisAlignment = props.optString("crossAxisAlignment", "start"),
            mainAxisAlignment = props.optString("mainAxisAlignment", "start"),
            spacing = props.optDouble("spacing", 0.0).toFloat(),
            children = childNodes(layout).mapNotNull { parseNode(it) },
        )
    }

    private fun childNodes(node: JSONObject): List<JSONObject> {
        val raw: Any? = node.opt("children") ?: node.opt("childGroups")
        val list: Any? = if (raw is JSONObject) raw.opt("children") else raw
        if (list !is JSONArray) return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
    }

    private fun parseNode(node: JSONObject): NudgeNode? {
        val type = node.optString("type")
        val box = parseBox(node.optJSONObject("containerProps"))
        val props = node.optJSONObject("props") ?: JSONObject()
        return nodeParsers[type]?.invoke(box, props)
    }

    // ── node registry (Strategy pattern) ─────────────────────────────────────

    private val nodeParsers: Map<String, (NudgeBox, JSONObject) -> NudgeNode> = mapOf(
        "digia/text" to ::parseText,
        "digia/image" to ::parseImage,
        "digia/button" to ::parseButton,
        "fw/sized_box" to ::parseGap,
        "digia/styledHorizontalDivider" to ::parseDivider,
        "digia/lottie" to ::parseLottie,
        "digia/carousel" to ::parseCarousel,
        "digia/videoPlayer" to ::parseVideo,
    )

    private fun parseText(box: NudgeBox, props: JSONObject): NudgeNode {
        val style = props.optJSONObject("textStyle") ?: JSONObject()
        val font = style.optJSONObject("fontToken")?.optJSONObject("font") ?: JSONObject()
        return NudgeText(
            box,
            text = props.optString("text"),
            fontSize = font.optDouble("size", 16.0).toFloat(),
            fontWeight = font.optString("weight", "400").toIntOrNull() ?: 400,
            color = parseColor(style.optString("textColor")) ?: 0xFF111111.toInt(),
            align = when (props.optString("alignment", "left")) {
                "center" -> NudgeTextAlign.CENTER
                "right", "end" -> NudgeTextAlign.RIGHT
                else -> NudgeTextAlign.LEFT
            },
        )
    }

    private fun parseImage(box: NudgeBox, props: JSONObject): NudgeNode {
        val ar = props.optDouble("aspectRatio", 0.0).toFloat()
        return NudgeImage(
            if (ar > 0f) box.withoutFixedHeight() else box,
            url = props.optJSONObject("src")?.optString("imageSrc") ?: "",
            fit = props.optString("fit", "cover"),
            aspectRatio = ar,
        )
    }

    private fun parseButton(box: NudgeBox, props: JSONObject): NudgeNode {
        val text = props.optJSONObject("text") ?: JSONObject()
        val textStyle = text.optJSONObject("textStyle") ?: JSONObject()
        val font = textStyle.optJSONObject("fontToken")?.optJSONObject("font") ?: JSONObject()
        val defaultStyle = props.optJSONObject("defaultStyle") ?: JSONObject()
        return NudgeButton(
            box,
            label = text.optString("text", "Button"),
            variant = when (props.optString("variant", "fill")) {
                "outline" -> NudgeButtonVariant.OUTLINE
                "text" -> NudgeButtonVariant.TEXT
                "elevated" -> NudgeButtonVariant.ELEVATED
                else -> NudgeButtonVariant.FILL
            },
            fontSize = font.optDouble("size", 16.0).toFloat(),
            fontWeight = font.optString("weight", "600").toIntOrNull() ?: 600,
            background = parseColor(defaultStyle.optString("backgroundColor")) ?: 0xFF4945FF.toInt(),
            textColor = parseColor(textStyle.optString("textColor")) ?: 0xFFFFFFFF.toInt(),
            radius = props.optJSONObject("shape")?.optDouble("borderRadius", 8.0)?.toFloat() ?: 8f,
            isPrimary = props.optBoolean("isPrimary", false),
            actions = NudgeActionParser().parse(props.optJSONObject("onClick")),
        )
    }

    private fun parseGap(box: NudgeBox, props: JSONObject): NudgeNode =
        NudgeGap(box, height = props.optDouble("height", 8.0).toFloat())

    private fun parseDivider(box: NudgeBox, props: JSONObject): NudgeNode = NudgeDivider(
        box,
        thickness = props.optDouble("thickness", 1.0).toFloat(),
        indent = props.optDouble("indent", 0.0).toFloat(),
        endIndent = props.optDouble("endIndent", 0.0).toFloat(),
        color = parseColor(props.optJSONObject("colorType")?.optString("color")) ?: 0xFFE0E0E0.toInt(),
    )

    private fun parseLottie(box: NudgeBox, props: JSONObject): NudgeNode = NudgeLottie(
        box,
        url = props.optJSONObject("src")?.optString("lottiePath") ?: "",
        height = props.optDouble("height", 160.0).toFloat(),
        loop = props.optString("animationType", "loop") != "once",
        autoplay = props.optBoolean("animate", true),
    )

    private fun parseCarousel(box: NudgeBox, props: JSONObject): NudgeNode {
        val imgs = mutableListOf<String>()
        props.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotEmpty()) imgs += s
            }
        }
        return NudgeCarousel(
            box,
            images = imgs,
            height = props.optDouble("height", 180.0).toFloat(),
            autoPlay = props.optBoolean("autoPlay", true),
            autoPlayInterval = props.optInt("autoPlayInterval", 3000),
            loop = props.optBoolean("infiniteScroll", true),
            showIndicator = props.optBoolean("showIndicator", true),
        )
    }

    private fun parseVideo(box: NudgeBox, props: JSONObject): NudgeNode = NudgeVideo(
        box,
        url = props.optString("url"),
        height = props.optDouble("height", 200.0).toFloat(),
        autoplay = props.optBoolean("autoPlay", false),
        loop = props.optBoolean("looping", false),
        showControls = props.optBoolean("showControls", true),
        muted = props.optBoolean("muted", false),
    )

    // ── box ───────────────────────────────────────────────────────────────────

    private fun parseBox(cp: JSONObject?): NudgeBox {
        val style = cp?.optJSONObject("style") ?: JSONObject()
        val border = style.optJSONObject("border")
        val widthStr = style.optString("width", "")
        return NudgeBox(
            fillWidth = widthStr == "100%",
            fixedWidth = if (widthStr == "100%") null else widthStr.toFloatOrNull(),
            fixedHeight = style.optString("height", "").toFloatOrNull(),
            background = parseColor(
                style.optString("bgColor", style.optString("backgroundColor", ""))
            ),
            paddingLeft = edgeSide(style.opt("padding"), "left"),
            paddingTop = edgeSide(style.opt("padding"), "top"),
            paddingRight = edgeSide(style.opt("padding"), "right"),
            paddingBottom = edgeSide(style.opt("padding"), "bottom"),
            marginLeft = edgeSide(style.opt("margin"), "left"),
            marginTop = edgeSide(style.opt("margin"), "top"),
            marginRight = edgeSide(style.opt("margin"), "right"),
            marginBottom = edgeSide(style.opt("margin"), "bottom"),
            borderRadius = style.optDouble("borderRadius", 0.0).toFloat(),
            borderColor = border?.let { parseColor(it.optString("borderColor")) },
            borderWidth = border?.optDouble("borderWidth", 0.0)?.toFloat() ?: 0f,
            selfAlign = NudgeSelfAlign.from(cp?.optString("align")),
        )
    }

    private fun edgeSide(raw: Any?, side: String): Float = when (raw) {
        is Number -> raw.toFloat()
        is JSONObject -> raw.optDouble(side, 0.0).toFloat()
        else -> 0f
    }
}

// ── Color helper ──────────────────────────────────────────────────────────────

/** Parses `#RGB` / `#RRGGBB` / `#AARRGGBB` hex strings; empty/invalid → null. */
internal fun parseColor(hex: String?): Int? {
    var v = hex?.trim() ?: return null
    if (v.isEmpty()) return null
    if (v.startsWith('#')) v = v.substring(1)
    if (v.length == 3) v = v.map { "$it$it" }.joinToString("")
    if (v.length == 6) v = "FF$v"
    if (v.length != 8) return null
    return v.toLongOrNull(16)?.toInt()
}
