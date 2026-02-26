package com.digia.digiaui.framework.utils

import com.digia.digiaui.framework.logging.Logger
import com.google.gson.Gson

class JsonUtil {
    companion object {
        private val gson = Gson()

        /**
         * Attempts to decode a JSON string, returning null if decoding fails.
         *
         * This function provides a safe way to decode JSON without throwing exceptions.
         *
         * [source] The JSON string to decode.
         * Returns the decoded JSON object, or null if decoding fails.
         */
        fun tryJsonDecode(source: String): Any? {
            return try {
                gson.fromJson(source, Any::class.java)
            } catch (e: Exception) {
                Logger.error("JSON decode error: $e", tag = "JsonUtil", error = e)
                null
            }
        }

        /**
         * Attempts to retrieve a value from a JSON object using multiple possible keys.
         *
         * [json] The JSON object to search in.
         * [keys] An ordered list of keys to try.
         * [parse] Optional function to cast or transform the value if found.
         *
         * Returns the value associated with the first matching key, or null if no key is found.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> tryKeys(
            json: JsonLike,
            keys: List<String>,
            parse: ((Any?) -> T?)? = null
        ): T? {
            for (key in keys) {
                if (json.containsKey(key)) {
                    val value = json[key]
                    return parse?.invoke(value) ?: value as? T
                }
            }
            return null
        }
    }
}

/**
 * Retrieves the value for a given key path in a nested map.
 *
 * The [keyPath] parameter is a dot-separated string representing the path to the desired value.
 * For example, 'a.b.c' will retrieve the value at map['a']['b']['c'].
 */
fun JsonLike.valueFor(keyPath: String): Any? {
    val keysSplit = keyPath.split('.').toMutableList()
    val thisKey = keysSplit.removeAt(0)
    val thisValue = this[thisKey]

    return if (keysSplit.isEmpty()) {
        thisValue
    } else if (thisValue is Map<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val nested = thisValue as JsonLike
        nested.valueFor(keysSplit.joinToString("."))
    } else {
        null
    }
}

/**
 * Sets the value for a given key path in a nested map.
 *
 * The [keyPath] parameter is a dot-separated string representing the path to the desired value.
 * For example, 'a.b.c' will set the value at map['a']['b']['c'].
 * If intermediate maps do not exist, they will be created.
 */
//fun JsonLike.setValueFor(keyPath: String, value: Any?) {
//    val keysSplit = keyPath.split('.').toMutableList()
//    val thisKey = keysSplit.removeAt(0)
//
//    if (keysSplit.isEmpty()) {
//        this[thisKey] = value
//        return
//    }
//
//    if (this[thisKey] !is Map<*, *>) {
//        this[thisKey] = mutableMapOf<String, Any?>()
//    }
//
//    (this[thisKey] as JsonLike).setValueFor(keysSplit.joinToString("."), value)
//}