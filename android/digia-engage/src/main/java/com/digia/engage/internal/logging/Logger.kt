package com.digia.engage.internal.logging

import android.util.Log
import com.digia.engage.DigiaLogLevel

object Logger {
    private const val TAG = "Digia"

    @Volatile var level: DigiaLogLevel = DigiaLogLevel.ERROR

    fun configure(logLevel: DigiaLogLevel) {
        level = logLevel
    }

    fun verbose(message: String) {
        if (level == DigiaLogLevel.VERBOSE) Log.d(TAG, message)
    }

    fun log(message: String, tag: String? = null) {
        if (level == DigiaLogLevel.VERBOSE) Log.d(tag ?: TAG, message)
    }

    fun error(message: String, tag: String? = null, error: Any? = null) {
        if (level != DigiaLogLevel.NONE) {
            val full = if (error != null) "$message — $error" else message
            Log.w(tag ?: TAG, full)
        }
    }

    fun warning(message: String, tag: String? = null) {
        if (level != DigiaLogLevel.NONE) Log.w(tag ?: TAG, message)
    }

    fun info(message: String, tag: String? = null) {
        if (level == DigiaLogLevel.VERBOSE) Log.i(tag ?: TAG, message)
    }
}
