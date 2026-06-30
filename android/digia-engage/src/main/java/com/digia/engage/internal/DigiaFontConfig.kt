package com.digia.engage.internal

import android.graphics.Typeface as AndroidTypeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

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
            cachedFamily = buildFamily(name)
        }
        return cachedFamily
    }

    /**
     * Build a Compose [FontFamily] for the named family that can resolve real
     * weights. Building it from a single `NORMAL` [AndroidTypeface] (the old way)
     * gave Compose only one face, so it rendered normal for weights < 600 and
     * SYNTHETIC bold for ≥ 600 — collapsing 400≈500 and 600≈700. Declaring a
     * [DeviceFontFamilyName] font per weight lets Compose pull the actual weight
     * face (or interpolate a variable font) instead.
     */
    private fun buildFamily(name: String): FontFamily =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deviceFamily(name)
        } else {
            FontFamily(AndroidTypeface.create(name, AndroidTypeface.NORMAL))
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deviceFamily(name: String): FontFamily {
        val device = DeviceFontFamilyName(name)
        val weights = listOf(
            FontWeight.W100,
            FontWeight.W200,
            FontWeight.W300,
            FontWeight.W400,
            FontWeight.W500,
            FontWeight.W600,
            FontWeight.W700,
            FontWeight.W800,
            FontWeight.W900,
        )
        return FontFamily(weights.map { Font(device, weight = it, style = FontStyle.Normal) })
    }
}
