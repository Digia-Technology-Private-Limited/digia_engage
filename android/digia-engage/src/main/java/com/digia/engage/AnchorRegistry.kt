package com.digia.engage

import android.view.View
import java.lang.ref.WeakReference

object AnchorRegistry {
    private val registry = mutableMapOf<String, WeakReference<View>>()

    fun register(key: String, view: View) {
        registry[key] = WeakReference(view)
    }

    fun unregister(key: String) {
        registry.remove(key)
    }

    fun getView(key: String): View? = registry[key]?.get()
}
