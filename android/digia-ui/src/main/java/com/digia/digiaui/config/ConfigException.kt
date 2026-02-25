package com.digia.digiaui.config

/** Error types for configuration loading failures */
enum class ConfigErrorType {
    NETWORK,
    FILE_OPERATION,
    INVALID_DATA,
    CACHE,
    VERSION,
    ASSET,
    UNKNOWN
}

/**
 * Exception thrown when configuration loading fails
 *
 * @param message Error message
 * @param type The category of error that occurred
 * @param originalError The underlying error, if any
 * @param stackTrace The stack trace, if any
 * @param cause The throwable cause
 */
class ConfigException(
        message: String,
        val type: ConfigErrorType = ConfigErrorType.UNKNOWN,
        val originalError: Throwable? = null,
        val stackTrace: String? = null,
        cause: Throwable? = null
) : Exception(message, cause ?: originalError) {

    override fun toString(): String {
        var result = "ConfigException(${type.name}): $message"
        originalError?.let { result += "\nOriginal error: $it" }
        stackTrace?.let { result += "\nStack trace: $it" }
        return result
    }

    companion object {
        /** Creates a network-related error */
        fun networkError(message: String, cause: Throwable? = null) =
                ConfigException(message, ConfigErrorType.NETWORK, cause, cause?.stackTraceToString(), cause)

        /** Creates a file operation error */
        fun fileError(message: String, cause: Throwable? = null) =
                ConfigException(message, ConfigErrorType.FILE_OPERATION, cause, cause?.stackTraceToString(), cause)

        /** Creates an invalid data error */
        fun invalidData(message: String, cause: Throwable? = null) =
                ConfigException(message, ConfigErrorType.INVALID_DATA, cause, cause?.stackTraceToString(), cause)

        /** Creates a cache-related error */
        fun cacheError(message: String, cause: Throwable? = null) =
                ConfigException(message, ConfigErrorType.CACHE, cause, cause?.stackTraceToString(), cause)

        /** Creates a version-related error */
        fun versionError(message: String, cause: Throwable? = null) =
                ConfigException(message, ConfigErrorType.VERSION, cause, cause?.stackTraceToString(), cause)

        /** Creates an asset-related error */
        fun assetError(message: String, cause: Throwable? = null) =
                ConfigException(message, ConfigErrorType.ASSET, cause, cause?.stackTraceToString(), cause)

        /** Creates an unknown error */
        fun unknownError(message: String, cause: Throwable? = null) =
                ConfigException(message, ConfigErrorType.UNKNOWN, cause, cause?.stackTraceToString(), cause)
    }
}
