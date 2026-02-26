package com.digia.digiaui.framework.utils

import com.digia.digiaui.framework.logging.Logger

/**
 * Safely attempts to cast the current object to a specified type R, with graceful fallback to null.
 *
 * This method provides safe casting with logging for failed casts in debug mode.
 */
inline fun <reified R> Any?.asSafe(): Any? {
    if (this is R) {
        return this
    }

    // Log the cast error in debug mode
    if (true && this != null) {  // TODO: use BuildConfig.DEBUG
        Logger.error("CastError when trying to cast $this to ${R::class}", tag = "ObjectUtil", error = TypeCastException())
    }

    return null
}

/**
 * Extension on Any? to provide a flexible type conversion method.
 */
inline fun <reified R> Any?.to(defaultValue: R? = null): R? {
    val value = this
    // If the value is null, return the default value
    if (value == null) return defaultValue

    // If the value is already of type R, return it
    if (value is R) return value

    // Attempt type-specific conversions
    return (when (R::class) {
        // Convert to String using toString()
        String::class -> value.toString()
        // Use NumUtil for numeric conversions
        Int::class -> NumUtil.toInt(value)
        Double::class -> NumUtil.toDouble(value)
        Float::class -> NumUtil.toFloat(value)
        // Use NumUtil for boolean conversion
        Boolean::class -> NumUtil.toBool(value)

        // Use NumUtil for num conversion
        Number::class -> NumUtil.toNum(value)
        // Map conversion
        MutableMap::class -> _toJsonLike(value)
        // List conversion
        List::class -> _toList(value)
        // For any other type, attempt a safe cast
        else -> value.asSafe<R>()
    } ?: defaultValue) as R?
}

// Helper method for JsonLike conversion
fun _toJsonLike(value: Any?): MutableMap<String, Any?>? {
    if (value is MutableMap<*, *>) {
        @Suppress("UNCHECKED_CAST")
        return value as MutableMap<String, Any?>
    }
    if (value !is String) return null
    val parsed = JsonUtil.tryJsonDecode(value)
    @Suppress("UNCHECKED_CAST")
    return parsed as? MutableMap<String, Any?>
}

// Helper method for List conversion
@Suppress("UNCHECKED_CAST")
 fun _toList(value: Any?): List<Any>? {
    if (value is List<*>) return value as List<Any>
    if (value !is String) return null
    val parsed = JsonUtil.tryJsonDecode(value)
    return if (parsed is List<*>) parsed as List<Any> else null
}