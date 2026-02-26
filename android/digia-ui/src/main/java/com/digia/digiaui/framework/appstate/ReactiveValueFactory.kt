package com.digia.digiaui.framework.appstate

import android.content.SharedPreferences
import com.digia.digiaui.framework.utils.JsonLike

interface ReactiveValueFactory {
    fun create(
        descriptor: StateDescriptor<*>,
        prefs: SharedPreferences
    ): ReactiveValue<*>
}


class DefaultReactiveValueFactory : ReactiveValueFactory {

    override fun create(
        descriptor: StateDescriptor<*>,
        prefs: SharedPreferences
    ): ReactiveValue<*> {
        return when (descriptor.description) {
            "number" -> _createTyped<Number>(descriptor, prefs)
            "string" -> _createTyped<String>(descriptor, prefs)
            "bool" -> _createTyped<Boolean>(descriptor, prefs)
            "json" -> _createTyped<JsonLike>(descriptor, prefs)
            "list" -> _createTyped<List<Any>>(descriptor, prefs)
            else -> error("Unsupported type for key: ${descriptor.key}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> _createTyped(
        descriptor: StateDescriptor<*>,
        prefs: SharedPreferences
    ): ReactiveValue<T> {

        val typed = descriptor as StateDescriptor<T>

        return if (typed.shouldPersist) {
            PersistedReactiveValue(
                prefs = prefs,
                key = typed.key,
                initialValue = typed.initialValue,
                deserialize = typed.deserialize,
                serialize = typed.serialize,
                streamName = typed.streamName
            )
        } else {
            ReactiveValue(
                initialValue = typed.initialValue,
                streamName = typed.streamName
            )
        }
    }
}



