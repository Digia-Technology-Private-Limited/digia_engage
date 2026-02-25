package com.digia.digiaui.framework.widgets.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class OverlayState {
    var isVisible by mutableStateOf(false)

    fun show() {
        if (!isVisible) isVisible = true
    }

    fun hide() {
        if (isVisible) isVisible = false
    }

    fun toggle() {
        isVisible = !isVisible
    }
}
