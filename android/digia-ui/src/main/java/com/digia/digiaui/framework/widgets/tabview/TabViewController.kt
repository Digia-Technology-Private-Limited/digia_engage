package com.digia.digiaui.framework.widgets.tabview

import androidx.compose.runtime.compositionLocalOf

/**
 * TabViewController holds the state for tab navigation. It provides the list of tabs, current
 * index, and animation progress.
 */
data class TabViewController(
        val tabs: List<Any?>,
        val initialIndex: Int,
        val currentIndex: Int,
        val onTabChange: (Int) -> Unit
) {
    val length: Int
        get() = tabs.size

    fun animateTo(index: Int) {
        if (index in 0 until length) {
            onTabChange(index)
        }
    }
}

/**
 * CompositionLocal for providing TabViewController down the composition tree. This mirrors
 * Flutter's InheritedTabViewController pattern.
 */
val LocalTabViewController = compositionLocalOf<TabViewController?> { null }
