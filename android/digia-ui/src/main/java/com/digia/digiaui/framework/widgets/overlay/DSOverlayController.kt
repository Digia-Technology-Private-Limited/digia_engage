package com.digia.digiaui.framework.widgets.overlay

class DSOverlayController {
    private var state: OverlayState? = null

    /**
     * Wired by the Overlay composable (equivalent to Flutter's initState/dispose).
     *
     * Public so it works even if this package is consumed from another module.
     */
    fun attach(state: OverlayState?) {
        this.state = state
    }

    fun show() = state?.show()
    fun hide() = state?.hide()
    fun toggle() = state?.toggle()
    val isVisible: Boolean
        get() = state?.isVisible ?: false
}
