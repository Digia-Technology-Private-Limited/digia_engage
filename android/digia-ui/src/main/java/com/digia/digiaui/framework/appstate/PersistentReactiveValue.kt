package com.digia.digiaui.framework.appstate

import android.content.SharedPreferences
import com.digia.digiaui.init.DigiaUIManager
import androidx.core.content.edit

class PersistedReactiveValue<T>(
    private val prefs: SharedPreferences,
    private val key: String,
    initialValue: T,
    private val deserialize: (String) -> T,
    private val serialize: (T) -> String,
    streamName: String
) : ReactiveValue<T>(
    loadValue(prefs, key, initialValue, deserialize),
    streamName
) {

    override fun update(newValue: T): Boolean {
        val updated = super.update(newValue)
        if (updated) {
            prefs.edit {
                putString(createPrefKey(key), serialize(newValue))
            }
        }
        return updated
    }

    companion object {
        private fun <T> loadValue(
            prefs: SharedPreferences,
            key: String,
            initialValue: T,
            deserialize: (String) -> T
        ): T {
            val stored = prefs.getString(createPrefKey(key), null)
            return stored?.let(deserialize) ?: initialValue
        }

        private fun createPrefKey(key: String): String {
            val projectId = DigiaUIManager.getInstance().accessKey
            return "${projectId}_app_state_$key"
        }
    }
}
