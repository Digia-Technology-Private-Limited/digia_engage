package com.digia.engage.internal.ui.nudge

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.json.JSONArray
import org.json.JSONObject

internal class NudgeParser {

    fun parse(templateConfig: JSONObject): NudgeColumn? {
        val layout = templateConfig.optJSONObject("layout") ?: return null
        return column(layout)
    }

    // ─── column ───────────────────────────────────────────────────────────────

    private fun column(layout: JSONObject): NudgeColumn {
        val props = layout.optJSONObject("props") ?: JSONObject()
        return NudgeColumn(
            crossAxisAlignment = crossAxis(props.optString("crossAxisAlignment", "start")),
            mainAxisAlignment = mainAxis(props.optString("mainAxisAlignment", "start")),
            spacing = props.optDouble("spacing", 0.0).toFloat(),
            children = childNodes(layout).mapNotNull { node(it) },
        )
    }

    // ─── child extraction ─────────────────────────────────────────────────────

    private fun childNodes(node: JSONObject): List<JSONObject> {
        val raw = node.opt("children") ?: node.opt("childGroups") ?: return emptyList()
        val list: JSONArray = when (raw) {
            is JSONObject -> raw.optJSONArray("children") ?: return emptyList()
            is JSONArray -> raw
            else -> return emptyList()
        }
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
    }

    // ─── node dispatcher ──────────────────────────────────────────────────────

    private fun node(obj: JSONObject): NudgeNode? {
        val type = obj.optString("type").takeIf { it.isNotBlank() } ?: return null
        val box = box(obj.optJSONObject("containerProps"))
        val props = obj.optJSONObject("props") ?: JSONObject()
        return nodeParsers[type]?.invoke(box, props)
    }

    private val nodeParsers: Map<String, (NudgeBox, JSONObject) -> NudgeNode> = mapOf(
        "digia/text" to ::text,
        "digia/image" to ::image,
        "digia/button" to ::button,
        "fw/sized_box" to ::gap,
        "digia/styledHorizontalDivider" to ::divider,
        "digia/lottie" to ::lottie,
        "digia/carousel" to ::carousel,
        "digia/videoPlayer" to ::video,
    )

    // ─── leaf node parsers ────────────────────────────────────────────────────

    private fun text(box: NudgeBox, props: JSONObject): NudgeNode {
        val style = props.optJSONObject("textStyle") ?: JSONObject()
        val font = style.optJSONObject("fontToken")?.optJSONObject("font") ?: JSONObject()
        return NudgeText(
            box = box,
            text = props.optString("text", ""),
            fontSize = font.optDouble("size", 16.0).toFloat(),
            fontWeight = fontWeight(font.optString("weight", "400")),
            color = parseColor(style.optString("textColor")) ?: Color(0xFF111111.toInt()),
            textAlign = textAlign(props.optString("alignment", "left")),
        )
    }

    private fun image(box: NudgeBox, props: JSONObject): NudgeNode {
        val aspectRatio = props.optDouble("aspectRatio", 0.0).toFloat()
        return NudgeImage(
            box = if (aspectRatio > 0) box.withoutFixedHeight() else box,
            url = props.optJSONObject("src")?.optString("imageSrc") ?: "",
            fit = boxFit(props.optString("fit", "cover")),
            aspectRatio = aspectRatio,
        )
    }

    private fun button(box: NudgeBox, props: JSONObject): NudgeNode {
        val text = props.optJSONObject("text") ?: JSONObject()
        val textStyle = text.optJSONObject("textStyle") ?: JSONObject()
        val font = textStyle.optJSONObject("fontToken")?.optJSONObject("font") ?: JSONObject()
        val defaultStyle = props.optJSONObject("defaultStyle") ?: JSONObject()
        return NudgeButton(
            box = box,
            label = text.optString("text", "Button"),
            variant = buttonVariant(props.optString("variant", "fill")),
            fontSize = font.optDouble("size", 16.0).toFloat(),
            fontWeight = fontWeight(font.optString("weight", "600")),
            background = parseColor(defaultStyle.optString("backgroundColor")) ?: Color(0xFF4945FF.toInt()),
            textColor = parseColor(textStyle.optString("textColor")) ?: Color(0xFFFFFFFF.toInt()),
            radius = props.optJSONObject("shape")?.optDouble("borderRadius", 8.0)?.toFloat() ?: 8f,
            actions = NudgeActionParser().parse(props.optJSONObject("onClick")),
            isPrimary = props.optBoolean("isPrimary", false),
        )
    }

    private fun gap(box: NudgeBox, props: JSONObject) =
        NudgeGap(box, height = props.optDouble("height", 8.0).toFloat())

    private fun divider(box: NudgeBox, props: JSONObject) = NudgeDivider(
        box = box,
        thickness = props.optDouble("thickness", 1.0).toFloat(),
        indent = props.optDouble("indent", 0.0).toFloat(),
        endIndent = props.optDouble("endIndent", 0.0).toFloat(),
        color = parseColor(props.optJSONObject("colorType")?.optString("color"))
            ?: Color(0xFFE0E0E0.toInt()),
    )

    private fun lottie(box: NudgeBox, props: JSONObject) = NudgeLottie(
        box = box,
        url = props.optJSONObject("src")?.optString("lottiePath") ?: "",
        height = props.optDouble("height", 160.0).toFloat(),
        loop = props.optString("animationType", "loop") != "once",
        autoplay = props.optBoolean("animate", true),
    )

    private fun carousel(box: NudgeBox, props: JSONObject): NudgeNode {
        val arr = props.optJSONArray("images") ?: JSONArray()
        val images = (0 until arr.length())
            .mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        return NudgeCarousel(
            box = box,
            images = images,
            height = props.optDouble("height", 180.0).toFloat(),
            autoPlay = props.optBoolean("autoPlay", true),
            autoPlayIntervalMs = props.optInt("autoPlayInterval", 3000),
            loop = props.optBoolean("infiniteScroll", true),
            showIndicator = props.optBoolean("showIndicator", true),
        )
    }

    private fun video(box: NudgeBox, props: JSONObject) = NudgeVideo(
        box = box,
        url = props.optString("url", ""),
        height = props.optDouble("height", 200.0).toFloat(),
        autoplay = props.optBoolean("autoPlay", false),
        loop = props.optBoolean("looping", false),
        showControls = props.optBoolean("showControls", true),
        muted = props.optBoolean("muted", false),
    )

    // ─── box ──────────────────────────────────────────────────────────────────

    private fun box(containerProps: JSONObject?): NudgeBox {
        val cp = containerProps ?: return NudgeBox.NONE
        val style = cp.optJSONObject("style") ?: JSONObject()
        val border = style.optJSONObject("border")
        val widthStr = style.optString("width", "")
        return NudgeBox(
            fillWidth = widthStr == "100%",
            fixedWidth = if (widthStr == "100%") null else widthStr.toFloatOrNull(),
            fixedHeight = style.optString("height", "").toFloatOrNull(),
            background = parseColor(style.optString("bgColor", style.optString("backgroundColor", ""))),
            paddingLeft = side(style.opt("padding"), "left"),
            paddingTop = side(style.opt("padding"), "top"),
            paddingRight = side(style.opt("padding"), "right"),
            paddingBottom = side(style.opt("padding"), "bottom"),
            marginLeft = side(style.opt("margin"), "left"),
            marginTop = side(style.opt("margin"), "top"),
            marginRight = side(style.opt("margin"), "right"),
            marginBottom = side(style.opt("margin"), "bottom"),
            borderRadius = style.optDouble("borderRadius", 0.0).toFloat(),
            borderColor = border?.let { parseColor(it.optString("borderColor", "")) },
            borderWidth = border?.optDouble("borderWidth", 0.0)?.toFloat() ?: 0f,
            selfAlign = selfAlign(cp.optString("align", "")),
        )
    }

    // ─── enum mappings ────────────────────────────────────────────────────────

    private fun crossAxis(v: String) = when (v) {
        "center" -> NudgeCrossAxisAlignment.CENTER
        "end" -> NudgeCrossAxisAlignment.END
        else -> NudgeCrossAxisAlignment.START
    }

    private fun mainAxis(v: String) = when (v) {
        "center" -> NudgeMainAxisAlignment.CENTER
        "end" -> NudgeMainAxisAlignment.END
        "spaceBetween" -> NudgeMainAxisAlignment.SPACE_BETWEEN
        "spaceAround" -> NudgeMainAxisAlignment.SPACE_AROUND
        "spaceEvenly" -> NudgeMainAxisAlignment.SPACE_EVENLY
        else -> NudgeMainAxisAlignment.START
    }

    private fun textAlign(v: String) = when (v) {
        "center" -> TextAlign.Center
        "right", "end" -> TextAlign.End
        else -> TextAlign.Start
    }

    private fun boxFit(v: String) = when (v) {
        "contain" -> ContentScale.Fit
        "fill" -> ContentScale.FillBounds
        else -> ContentScale.Crop
    }

    private fun fontWeight(v: String) = when (v) {
        "500" -> FontWeight.Medium
        "600" -> FontWeight.SemiBold
        "700" -> FontWeight.Bold
        else -> FontWeight.Normal
    }

    private fun buttonVariant(v: String) = when (v) {
        "outline" -> NudgeButtonVariant.OUTLINE
        "text" -> NudgeButtonVariant.TEXT
        "elevated" -> NudgeButtonVariant.ELEVATED
        else -> NudgeButtonVariant.FILL
    }

    private fun selfAlign(v: String) = when (v) {
        "start" -> NudgeSelfAlign.START
        "center" -> NudgeSelfAlign.CENTER
        "end" -> NudgeSelfAlign.END
        else -> null
    }

    private fun side(value: Any?, key: String): Float = when (value) {
        is Number -> value.toFloat()
        is JSONObject -> value.optDouble(key, 0.0).toFloat()
        else -> 0f
    }

    companion object {
        fun parseColor(hex: String?): Color? {
            if (hex.isNullOrBlank()) return null
            return runCatching {
                Color(android.graphics.Color.parseColor(hex.trim()))
            }.getOrNull()
        }
    }
}
