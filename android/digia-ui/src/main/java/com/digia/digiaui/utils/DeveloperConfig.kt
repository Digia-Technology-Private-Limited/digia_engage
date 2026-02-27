package com.digia.digiaui.utils

/// Sealed class representing different hosting environments for Digia UI.
///
/// [DigiaUIHost] defines the hosting configuration for the Digia UI system,
/// allowing different deployment scenarios and resource proxy configurations.
/// This abstraction enables support for various hosting environments while
/// maintaining consistent API access patterns.
///
/// The class supports resource proxy URLs for scenarios where assets need
/// to be served through a different endpoint than the main API.
sealed class DigiaUIHost(val resourceProxyUrl: String? = null)

/// Host configuration for Digia Dashboard deployment.
///
/// [DashboardHost] represents the standard Digia Studio dashboard hosting
/// environment. This is the most common deployment scenario where the
/// application connects to the official Digia Studio backend services.
///
/// Example usage:
/// ```kotlin
/// val host = DashboardHost(
///   resourceProxyUrl = "https://cdn.example.com" // Optional
/// )
/// ```
class DashboardHost(resourceProxyUrl: String? = null) : DigiaUIHost(resourceProxyUrl)


class PreviewHost(resourceProxyUrl: String? = null) : DigiaUIHost(resourceProxyUrl)
/// Developer configuration for debugging and development features.
///
/// [DeveloperConfig] provides configuration options specifically designed
/// for development and debugging scenarios. This includes proxy settings,
/// logging configuration, inspection tools, and custom backend URLs.
///
/// Key features:
/// - **Proxy Support**: Route traffic through debugging proxies
/// - **Inspection Tools**: Network request monitoring and debugging
/// - **Custom Logging**: Configurable logging for development insights
/// - **Backend Override**: Use custom backend URLs for testing
/// - **Host Configuration**: Custom hosting environment settings
///
/// The configuration is typically only used in debug builds and should
/// not be included in production releases for security and performance reasons.
///
/// Example usage:
/// ```kotlin
/// val developerConfig = DeveloperConfig(
///   proxyUrl = "192.168.1.100:8888", // Charles Proxy
///   inspector = MyCustomInspector(),
///   baseUrl = "https://dev-api.digia.tech/api/v1",
///   host = DashboardHost()
/// )
/// ```
data class DeveloperConfig(
    /// Proxy URL for routing HTTP traffic through debugging tools.
    ///
    /// This is typically used with tools like Charles Proxy, Fiddler, or
    /// other network debugging proxies. The URL should include the port
    /// number (e.g., '192.168.1.100:8888').
    ///
    /// Only applies to Android/iOS platforms in debug mode.
    val proxyUrl: String? = null,

    /// Inspector instance for capturing debug information and events.
    ///
    /// Custom inspectors can be provided to integrate with existing logging
    /// infrastructure or to capture specific types of debug information.
    /// The inspector receives events from throughout the Digia UI system.
    val inspector: DigiaInspector? = null,

    /// Host configuration for custom deployment environments.
    ///
    /// Allows overriding the default hosting configuration to connect to
    /// custom backend deployments or use different resource serving strategies.
    val host: DigiaUIHost? = null,

    /// Base URL for Digia Studio backend API requests.
    ///
    /// This allows connecting to custom backend deployments such as:
    /// - Development/staging environments
    /// - Self-hosted Digia Studio instances
    /// - Local development servers
    ///
    /// Defaults to the production Digia Studio API URL.
    val baseUrl: String = "https://app.digia.tech/api/v1"
)



/**
 * Represents the distinct types of host applications supported by Digia UI.
 */
enum class HostApp(val value: String) {
    DASHBOARD("dashboard"),
    PREVIEW("preview");

    companion object {
        /**
         * Returns the corresponding [HostApp] enum value for the given [DigiaUIHost].
         *
         * This function maps [DigiaUIHost] subclasses to their respective [HostApp] values.
         * Returns `null` if the host type is not recognized.
         */
        fun getHostApp(host: DigiaUIHost?): HostApp? {
            return when (host) {
                is DashboardHost -> DASHBOARD
                is PreviewHost -> PREVIEW
                else -> null
            }
        }
    }
}