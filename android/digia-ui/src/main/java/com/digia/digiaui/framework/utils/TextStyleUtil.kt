import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.digia.digiaui.framework.DUIFontFactory
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.JsonUtil.Companion.tryKeys
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.utils.valueFor
import kotlin.text.get

/* ---------------------------------------------------------
 * Defaults
 * --------------------------------------------------------- */

val defaultTextStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 21.sp,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Normal,
)

/* ---------------------------------------------------------
 * Public API
 * --------------------------------------------------------- */

@Composable
fun makeTextStyle(
    json: Map<String, Any?>?,
    eval: (Any?) -> String?,
    fallback: TextStyle? = defaultTextStyle,
    resources: UIResources? = null,
    useLocalResources: Boolean = true
): TextStyle? {
    if (json == null) return fallback

    val textColor = evalColor(json["textColor"], eval, useLocalResources, resources)
    val textBgColor = evalColor(
        tryKeys<String>(json as JsonLike, listOf("textBackgroundColor", "textBgColor")),
        eval, useLocalResources, resources
    )
    val textDecoration = toTextDecoration(json["textDecoration"])

    val fontToken = json["fontToken"]

    /* ---------------- Case 1: No fontToken ---------------- */

    if (fontToken == null) {
        return fallback?.copy(
            color = textColor ?: fallback.color,
            background = textBgColor ?: fallback.background,
            textDecoration = textDecoration,
        )
    }

    /* ---------------- Case 2: Token string ---------------- */

    if (fontToken is String) {
        val tokenStyle = resources?.textStyles?.get(fontToken) ?: (if (useLocalResources) resourceTextStyle(fontToken) else null) ?: defaultTextStyle

        return tokenStyle.copy(
            color = textColor ?: tokenStyle.color,
            background = textBgColor ?: tokenStyle.background,
            textDecoration = textDecoration,
        )
    }

    /* ---------------- Case 3: Token map ---------------- */

    val tokenMap = fontToken as? JsonLike ?: return fallback

    val tokenValue = tokenMap["value"] as? String

    @Suppress("UNCHECKED_CAST")
    val overridingFontFamily = tokenMap.valueFor("font.fontFamily") as? String

    val overridingFontStyle =
        tokenMap.valueFor("font.style")
            ?.let { eval(it) }
            ?.let(::toFontStyle)
            ?: tokenMap.valueFor("font.isItalic")
                ?.let { eval(it) }
                ?.toBooleanStrictOrNull()
                ?.let { if (it) FontStyle.Italic else null }

    val overridingFontWeight =
        tokenMap.valueFor("font.weight")
            ?.let { eval(it) }
            ?.let(::toFontWeight)

    val overridingFontSize =
        tokenMap.valueFor("font.size")
            ?.let { eval(it) }
            ?.toDoubleOrNull()

    val overridingFontHeight =
        tokenMap.valueFor("font.height")
            ?.let { eval(it) }
            ?.toDoubleOrNull()

    val baseSize = overridingFontSize?.sp ?: defaultTextStyle.fontSize

    /* -------- Case 3a: Design token selected -------- */

    if (tokenValue != null) {
        val tokenStyle = resources?.textStyles?.get(tokenValue) ?: (if (useLocalResources) resourceTextStyle(tokenValue) else null)
        if (tokenStyle != null) {
            return tokenStyle.copy(
                color = textColor ?: tokenStyle.color,
                background = textBgColor ?: tokenStyle.background,
                textDecoration = textDecoration,
            )
        }
    }

    /* -------- Case 3b: Inline font overrides -------- */

    val resolvedStyle = defaultTextStyle.copy(
        fontWeight = overridingFontWeight,
        fontStyle = overridingFontStyle,
        fontSize = baseSize,
        lineHeight = (baseSize * (overridingFontHeight ?: 1.5)),
        color = textColor ?: defaultTextStyle.color,
        background = textBgColor ?: defaultTextStyle.background,
        textDecoration = textDecoration,
    )


    val fontFactory = resources?.fontFactory ?: if (useLocalResources) resourceFontFactory() else null

    if (fontFactory != null && overridingFontFamily != null) {
        return fontFactory.getFont(overridingFontFamily, textStyle = resolvedStyle)
    }

    return resolvedStyle
}

/* ---------------------------------------------------------
 * Helpers
 * --------------------------------------------------------- */

@Composable
private fun evalColor(expr: Any?, eval: (Any?) -> String?, useLocalResources: Boolean = true,resources: UIResources?= null): Color? {
    return eval(expr)?.let { if (useLocalResources) resourceColor(it) else resources?.colors?.get(it) }
}

/* ---------------------------------------------------------
 * Conversion helpers
 * --------------------------------------------------------- */

fun toTextDecoration(value: Any?): TextDecoration? =
    when (value as? String) {
        "underline" -> TextDecoration.Underline
        "lineThrough" -> TextDecoration.LineThrough
        "none" -> TextDecoration.None
        else -> null
    }

fun toFontWeight(value: Any?): FontWeight? =
    when (value as? String) {
        "normal" -> FontWeight.Normal
        "bold" -> FontWeight.Bold
        "100" -> FontWeight.W100
        "200" -> FontWeight.W200
        "300" -> FontWeight.W300
        "400" -> FontWeight.W400
        "500" -> FontWeight.W500
        "600" -> FontWeight.W600
        "700" -> FontWeight.W700
        "800" -> FontWeight.W800
        "900" -> FontWeight.W900
        "black"-> FontWeight.Black
        else -> null
    }

fun toFontStyle(value: Any?): FontStyle? =
    when (value as? String) {
        "normal" -> FontStyle.Normal
        "italic" -> FontStyle.Italic
        else -> null
    }

/* ---------------------------------------------------------
 * Legacy conversion support
 * --------------------------------------------------------- */

fun convertToTextStyle(
    value: Any?,
    fontFactory: DUIFontFactory?,
): TextStyle? {
    if (value !is Map<*, *>) return null

    val fontWeight = toFontWeight(value["weight"])
    val fontStyle = toFontStyle(value["style"])
    val fontSize = NumUtil.toDouble(value["size"]) ?: 14.0
    val fontHeight = NumUtil.toDouble(value["height"]) ?: 1.5
    
    @Suppress("UNCHECKED_CAST")
    val valueAsJsonLike = value as? JsonLike ?: return null
    val fontFamily = tryKeys<String>(valueAsJsonLike, listOf("font-family", "fontFamily")) as? String

    if (fontFactory != null && fontFamily != null) {
        return fontFactory.getFont(
            fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontSize = fontSize,
            height = fontHeight,
        )
    }

    return TextStyle(
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * fontHeight).sp,
    )
}
