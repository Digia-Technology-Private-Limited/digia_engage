package com.digia.engage.internal

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

internal data class ScreenRect(val x: Int, val y: Int, val width: Int, val height: Int)

internal data class AnchorEntry(
    val rect: ScreenRect,
    val viewRef: WeakReference<android.view.View>,
)

internal class AnchorRegistry {
    private val anchors = ConcurrentHashMap<String, AnchorEntry>()

    fun register(key: String, rect: ScreenRect) {
        // Update rect in-place if entry exists; create entry with null View if new
        val existing = anchors[key]
        anchors[key] = if (existing != null) existing.copy(rect = rect)
                       else AnchorEntry(rect, WeakReference(null))
    }

    fun registerWithView(key: String, rect: ScreenRect, view: android.view.View) {
        anchors[key] = AnchorEntry(rect, WeakReference(view))
    }

    fun unregister(key: String) { anchors.remove(key) }
    fun find(key: String): ScreenRect? = anchors[key]?.rect
    fun findView(key: String): WeakReference<android.view.View>? = anchors[key]?.viewRef
}
