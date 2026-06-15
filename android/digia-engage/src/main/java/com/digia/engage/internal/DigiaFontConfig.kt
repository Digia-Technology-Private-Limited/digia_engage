package com.digia.engage.internal

import android.graphics.Typeface as AndroidTypeface
import androidx.compose.ui.text.font.FontFamily

internal object DigiaFontConfig {
    @Volatile
    var fontFamily: String? = null

    private var cachedName: String? = null
    private var cachedFamily: FontFamily? = null

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
