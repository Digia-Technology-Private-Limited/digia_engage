package com.digia.digiaui.framework

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

abstract class DUIFontFactory {
    abstract fun getFont(
        fontFamily: String,
        textStyle: TextStyle? = null,
        color: Color? = null,
        backgroundColor: Color? = null,
        fontSize: Double? = null,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null,
        height: Double? = null,
        decoration: TextDecoration? = null,
        decorationColor: Color? = null,
        decorationStyle: String? = null,
        decorationThickness: Double? = null
    ): TextStyle

    abstract fun getDefaultFont(): TextStyle
}