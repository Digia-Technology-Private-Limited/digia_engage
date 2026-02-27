package com.digia.digiaui.framework.appstate

import com.digia.digiaui.framework.preferences.PreferencesStore
import com.digia.digiaui.framework.utils.JsonLike


/**
 * Global state manager that holds multiple reactive values
 */
class DUIAppState private constructor() {

    companion object {
        val instance: DUIAppState by lazy { DUIAppState() }
    }

    private val values: MutableMap<String, ReactiveValue<Any?>> = mutableMapOf()
    private var isInitialized: Boolean = false

    /**
     * Initialize the global state with state descriptors
     *
     * @param rawValues JSON / dynamic values describing state
     */
    suspend fun init(rawValues: List<Any>) {
        if (isInitialized) {
            dispose()
        }

        val descriptors = rawValues.map {
            StateDescriptorFactory().fromJson(it as JsonLike)
        }

        val prefs = PreferencesStore.getInstance().prefs

        for (descriptor in descriptors) {
            if (values.containsKey(descriptor.key)) {
                throw IllegalStateException(
                    "Duplicate state key: ${descriptor.key}"
                )
            }

            val reactiveValue =
                DefaultReactiveValueFactory().create(
                    descriptor,
                    prefs
                )

            @Suppress("UNCHECKED_CAST")
            values[descriptor.key] = reactiveValue as ReactiveValue<Any?>
        }

        isInitialized = true
    }

    /**
     * Get a reactive value by key
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): ReactiveValue<T> {
        ensureInitialized()

        val value = values[key]
            ?: throw IllegalStateException("State key \"$key\" not found")

        try {
            return value as ReactiveValue<T>
        } catch (e: ClassCastException) {
            throw IllegalStateException(
                "Type mismatch for key \"$key\""
            )
        }
    }

    /**
     * Get current value by key
     */
    fun <T> getValue(key: String): T =
        get<T>(key).value

    /**
     * Update value by key
     */
    fun <T> update(key: String, newValue: T): Boolean =
        get<T>(key).update(newValue)

    /**
     * Observe value changes as Flow
     */
    fun <T> stream(key: String) =
        get<T>(key).flow

    /**
     * Access all values (read-only)
     */
    fun all(): Map<String, ReactiveValue<Any?>> = values.toMap()

    /**
     * Dispose all registered values
     * Call ONLY on full app reset
     */
    fun dispose() {
        values.clear()
        isInitialized = false
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(
                "DUIAppState must be initialized before use"
            )
        }
    }
}
