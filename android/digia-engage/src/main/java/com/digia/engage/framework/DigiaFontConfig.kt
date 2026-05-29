package com.digia.engage.framework

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.ui.text.font.FontFamily

/**
 * Holds the global font family supplied via [com.digia.engage.DigiaConfig.fontFamily]
 * during SDK initialization. Applied as the default font for all Digia-rendered text
 * when a widget does not resolve a font through a [DUIFontFactory].
 */
object DigiaFontConfig {
    @Volatile
    var fontFamily: String? = null

    private var cachedName: String? = null
    private var cachedFamily: FontFamily? = null

    /** Resolves the configured family name to a Compose [FontFamily], cached per name. */
    @Synchronized
    fun composeFontFamily(): FontFamily? {
        val name = fontFamily?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            cachedName = null
            cachedFamily = null
            return null
        }
        if (name != cachedName) {
            cachedName = name
            cachedFamily = FontFamily(AndroidTypeface.create(name, AndroidTypeface.NORMAL))
        }
        return cachedFamily
    }
}
