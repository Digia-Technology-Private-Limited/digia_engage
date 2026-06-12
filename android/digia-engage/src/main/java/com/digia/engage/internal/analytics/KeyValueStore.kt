package com.digia.engage.internal.analytics

import android.content.SharedPreferences

internal interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

internal class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String?) = prefs.getString(key, default)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
