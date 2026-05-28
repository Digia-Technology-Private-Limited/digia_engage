package com.digia.engage

data class DigiaConfig(
    val apiKey: String,
    val logLevel: DigiaLogLevel = DigiaLogLevel.ERROR,
    val environment: DigiaEnvironment = DigiaEnvironment.PRODUCTION,
    val baseUrl: String? = null,
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
