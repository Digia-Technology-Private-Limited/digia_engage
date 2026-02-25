package com.digia.digiaui.network

/// Configuration for network requests.
///
/// [NetworkConfiguration] defines the settings for HTTP client behavior,
/// including timeouts and default headers for API requests.
data class NetworkConfiguration(
    /// Connection timeout in milliseconds
    val timeoutInMs: Int,

    /// Default headers to include in all requests
    val defaultHeaders: Map<String, Any>
) {
    companion object {
        /// Creates a network configuration with default values.
        ///
        /// [defaultHeaders] will be empty if not provided.
        /// [timeoutInMs] will be 30 seconds if not provided.
        fun withDefaults(
            defaultHeaders: Map<String, Any>? = null,
            timeoutInMs: Int? = null
        ): NetworkConfiguration {
            return NetworkConfiguration(
                defaultHeaders = defaultHeaders ?: emptyMap(),
                timeoutInMs = timeoutInMs ?: 30000
            )
        }
    }
}
