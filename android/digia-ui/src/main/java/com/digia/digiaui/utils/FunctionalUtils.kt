package com.digia.digiaui.utils

/// Provides a safe way to apply a function to a nullable value.
///
/// This extension method allows for chaining operations on nullable types,
/// similar to Optional in Java or Option in Scala.
///
/// Usage:
/// ```kotlin
/// val result = someNullableValue.maybe { v -> doSomething(v) }
/// ```
///
/// [fn] is the function to apply if this value is not null.
/// Returns the result of [fn] if this is not null, otherwise returns null.
fun <T, R> T?.maybe(fn: (T) -> R?): R? = this?.let(fn)

/// Provides a safe way to apply a function to two nullable values.
///
/// This extension method allows for combining two nullable values in a pair
/// and applying a function to them only if both are non-null.
///
/// Usage:
/// ```kotlin
/// val result = Pair(value1, value2).maybe { a, b -> combineValues(a, b) }
/// ```
///
/// [fn] is the function to apply if both values in the pair are not null.
/// Returns the result of [fn] if both values are not null, otherwise returns null.
fun <T, U, R> Pair<T?, U?>.maybe(fn: (T, U) -> R): R? =
    if (first != null && second != null) fn(first as T, second as U) else null

/// Attempts to cast a value to a specified type.
///
/// [x] The value to cast.
/// [orElse] An optional function to provide a default value if casting fails.
///
/// Returns the cast value, or throws if casting fails and no [orElse] is provided.
inline fun <reified T> asType(x: Any?, noinline orElse: (() -> T)? = null): T {
    return if (x is T) {
        x
    } else {
        orElse?.invoke() ?: throw TypeCastException("Cannot cast $x to ${T::class}")
    }
}

/// Safely attempts to cast a value to a specified type, with graceful fallback options.
///
/// This function differs significantly from Kotlin's `as` operator:
/// - It returns `null` instead of throwing an exception if the cast fails.
///
/// Key differences from Kotlin's `as?`:
/// 1. `asSafe<T>(x)` returns `null` if `x` is not of type `T`.
/// 2. `x as? T` returns `null` if `x` is not of type `T` or is null.
///
/// Example:
/// ```kotlin
/// val someValue: Any = 42
/// val result1: String? = asSafe<String>(someValue) // Returns null
/// val result2: String? = someValue as? String     // Returns null
/// ```
///
/// [x] The value to cast.
///
/// Returns:
/// - The cast value if successful.
/// - `null` if casting fails.
///
/// This function is particularly useful in scenarios where you want to
/// attempt a cast without the risk of runtime exceptions, such as when
/// working with dynamic data or when graceful degradation is preferred.
inline fun <reified T> asSafe(x: Any?): T? {
    return if (x is T) {
        x
    } else {
        // Consider using a logging framework instead of print for production code
        if (isDebugMode() && x != null) {
            Logger.error("CastError when trying to cast $x to ${T::class}", tag = "FunctionalUtil", error = TypeCastException())
        }
        null
    }
}

/// Checks if the app is in debug mode
fun isDebugMode(): Boolean = try {
    // Try BuildConfig.DEBUG first (most common)
//    val buildConfigClass = Class.forName("com.digia.digiaui.BuildConfig")
//    val debugField = buildConfigClass.getField("DEBUG")
//    debugField.getBoolean(null)
    false
} catch (_: Exception) {
    // Fallback: check build type
    android.os.Build.TYPE == "debug" || android.os.Build.TYPE == "eng"
}
