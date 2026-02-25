package com.digia.digiaui.framework.logging

import android.os.Build

/// A utility class for logging messages.
/// Uses println when in debug mode.
///
class Logger {
    /// Private constructor to prevent instantiation
    private constructor()

    companion object {
        /// Checks if the app is in debug mode
        private val isDebugMode: Boolean
            get() = try {
                // Try BuildConfig.DEBUG first (most common)
//                val buildConfigClass = Class.forName("com.digia.digiaui.BuildConfig")
//                val debugField = buildConfigClass.getField("DEBUG")
//                debugField.getBoolean(null)
                false
            } catch (e: Exception) {
                // Fallback: check build type
                Build.TYPE == "debug" || Build.TYPE == "eng"
            }

        /// Logs a message only when in debug mode
        fun log(message: String, tag: String? = null) {
            if (isDebugMode) {
                val logMessage = tag?.let { "[$it] $message" } ?: message
                println(logMessage)
            }
        }

        /// Logs an error message only when in debug mode
        fun error(message: String, tag: String? = null, error: Any? = null) {
            if (isDebugMode) {
                val logMessage = tag?.let { "[$it] ERROR: $message" } ?: "ERROR: $message"
                val fullMessage = error?.let { "$logMessage - $error" } ?: logMessage
                println(fullMessage)
            }
        }

        /// Logs an info message only when in debug mode
        fun info(message: String, tag: String? = null) {
            if (isDebugMode) {
                val logMessage = tag?.let { "[$it] INFO: $message" } ?: "INFO: $message"
                println(logMessage)
            }
        }

        /// Logs a warning message only when in debug mode
        fun warning(message: String, tag: String? = null) {
            if (isDebugMode) {
                val logMessage = tag?.let { "[$it] WARNING: $message" } ?: "WARNING: $message"
                println(logMessage)
            }
        }
    }
}
