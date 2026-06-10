package com.digia.engage.framework.logging

import android.util.Log
import com.digia.engage.DigiaLogLevel

/**
 * Central logger for the Digia Engage SDK.
 *
 * Call [configure] once during SDK init to set the log level from [DigiaConfig.logLevel]:
 *   - NONE    → no output
 *   - ERROR   → warnings and errors only (default)
 *   - VERBOSE → all diagnostic output, helpful when campaigns are not showing
 */
object Logger {
    private const val TAG = "Digia"

    @Volatile var level: DigiaLogLevel = DigiaLogLevel.ERROR

    fun configure(logLevel: DigiaLogLevel) {
        level = logLevel
    }

    /** Detailed diagnostic log — only emitted when logLevel is VERBOSE. */
    fun verbose(message: String) {
        if (level == DigiaLogLevel.VERBOSE) Log.d(TAG, message)
    }

    /** General info log — only emitted when logLevel is VERBOSE. */
    fun log(message: String, tag: String? = null) {
        if (level == DigiaLogLevel.VERBOSE) Log.d(tag ?: TAG, message)
    }

    /** Warning / error — emitted unless logLevel is NONE. */
    fun error(message: String, tag: String? = null, error: Any? = null) {
        if (level != DigiaLogLevel.NONE) {
            val full = if (error != null) "$message — $error" else message
            Log.w(tag ?: TAG, full)
        }
    }

    /** Alias of [error] for semantic clarity at call sites. */
    fun warning(message: String, tag: String? = null) {
        if (level != DigiaLogLevel.NONE) Log.w(tag ?: TAG, message)
    }

    fun info(message: String, tag: String? = null) {
        if (level == DigiaLogLevel.VERBOSE) Log.i(tag ?: TAG, message)
    }
}
