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
    private const val PRODUCTION = "https://app.digia.tech"
    private const val SANDBOX    = "https://dev.digia.tech"

    @Volatile private var _baseUrl: String = PRODUCTION

    fun configure(config: DigiaConfig) {
        _baseUrl = (config.baseUrl ?: if (config.environment == DigiaEnvironment.SANDBOX) SANDBOX else PRODUCTION)
            .trimEnd('/')
    }

    /** Reset to production default. Use in tests only. */
    fun resetForTest(baseUrl: String = PRODUCTION) { _baseUrl = baseUrl }

    val campaigns  get() = "$_baseUrl/api/v1/engage/sdk/getCampaigns"
    val track      get() = "$_baseUrl/api/v1/engage/sdk/track"
    val session    get() = "$_baseUrl/api/v1/engage/sdk/session"
    val submission get() = "$_baseUrl/api/v1/engage/sdk/recordSubmission"
    val recordEvent get() = "$_baseUrl/api/v1/engage/sdk/recordEvent"
}
