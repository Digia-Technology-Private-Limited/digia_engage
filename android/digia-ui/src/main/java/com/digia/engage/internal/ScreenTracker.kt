package com.digia.engage.internal

internal class ScreenTracker(
    private val onScreenChanged: (String) -> Unit,
) {
    var currentScreen: String? = null
        private set

    fun setScreen(name: String) {
        currentScreen = name
        onScreenChanged(name)
    }

    fun clear() {
        currentScreen = null
    }
}
