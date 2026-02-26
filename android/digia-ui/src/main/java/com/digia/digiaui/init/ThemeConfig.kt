package com.digia.digiaui.init

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}


data class ThemeSnapshot(
    val isDark: Boolean
)

object SDKThemeResolver {

    @Volatile
    private var snapshot = ThemeSnapshot(isDark = false)

    fun update(isDark: Boolean) {
        snapshot = ThemeSnapshot(isDark)
    }

    fun isDark(): Boolean = snapshot.isDark
}
