package com.digia.digiaui.framework.preferences

import android.content.Context
import android.content.SharedPreferences
import com.digia.digiaui.framework.logging.Logger
import androidx.core.content.edit

/**
 * PreferencesStore provides a centralized way to access SharedPreferences.
 *
 * This singleton class wraps Android's SharedPreferences API and provides a simple interface for
 * storing and retrieving persistent data.
 *
 * Must be initialized before use by calling [initialize] with a Context.
 */
class PreferencesStore private constructor() {

    private var _prefs: SharedPreferences? = null

    /**
     * Gets the SharedPreferences instance
     *
     * @throws IllegalStateException if not initialized
     */
    val prefs: SharedPreferences
        get() =
                _prefs
                        ?: throw IllegalStateException(
                                "PreferencesStore not initialized. Call PreferencesStore.initialize() first."
                        )

    /** Gets a string value from preferences */
    fun getString(key: String, defaultValue: String? = null): String? {
        return prefs.getString(key, defaultValue)
    }

    /** Saves a string value to preferences */
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /** Gets an int value from preferences */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    /** Saves an int value to preferences */
    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    /** Gets a boolean value from preferences */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    /** Saves a boolean value to preferences */
    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    /** Removes a value from preferences */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** Clears all preferences */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "digia_ui_prefs"

        @Volatile private var INSTANCE: PreferencesStore? = null

        /** Gets the singleton instance */
        fun getInstance(): PreferencesStore {
            return INSTANCE
                    ?: synchronized(this) { INSTANCE ?: PreferencesStore().also { INSTANCE = it } }
        }

        /**
         * Initializes the PreferencesStore with a Context. Must be called before accessing the
         * instance.
         *
         * @param context Android application context
         */
        fun initialize(context: Context) {
            val instance = getInstance()
            if (instance._prefs == null) {
                instance._prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                Logger.log("PreferencesStore initialized")
            }
        }
    }
}


interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class AndroidKeyValueStorage(
    private val prefs: SharedPreferences
) : KeyValueStorage {

    override fun getString(key: String): String? =
        prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }
}