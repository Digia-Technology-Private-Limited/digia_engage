package com.digia.engage

data class DigiaConfig(
    val apiKey: String,
    val logLevel: DigiaLogLevel = DigiaLogLevel.ERROR,
    val environment: DigiaEnvironment = DigiaEnvironment.PRODUCTION,
    val baseUrl: String? = null,
    /**
     * Optional global font family applied to all Digia-rendered text.
     * Resolved as an Android system/registered font family name
     * (e.g. "sans-serif", or a family bundled and registered with the system).
     */
    val fontFamily: String? = null,
    val analyticsConfig: AnalyticsConfig = AnalyticsConfig(),
    val wrapperBinding: String? = null,
    val wrapperVersion: String? = null,
)

enum class DigiaLogLevel {
    NONE,
    ERROR,
    VERBOSE,
}

enum class DigiaEnvironment {
    PRODUCTION,
    SANDBOX,
}

internal object DigiaEndpoints {
    const val PRODUCTION = "https://app.digia.tech"
    const val SANDBOX = "https://dev.digia.tech"
}
