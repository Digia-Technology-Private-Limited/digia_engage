package com.digia.digiaui.utils

/// Interface for capturing debug information and events in Digia UI.
///
/// Implementations of this interface can integrate with existing logging
/// infrastructure or capture specific types of debug information.
/// The inspector receives events from throughout the Digia UI system.
interface DigiaInspector {
    /// Called when debug information is available
    fun onDebugInfo(info: String)

    /// Network observer for intercepting and monitoring HTTP requests
    val networkObserver: NetworkObserver?
}

/// Network observer for HTTP request monitoring.
///
/// [NetworkObserver] provides access to HTTP interceptors for debugging
/// and monitoring network requests in development environments.
data class NetworkObserver(
    /// OkHttp interceptor for request/response monitoring
    val interceptor: okhttp3.Interceptor?
)
