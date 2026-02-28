package com.digia.engage

data class DigiaConfig(
    val apiKey: String,
    val logLevel: DigiaLogLevel = DigiaLogLevel.ERROR,
    val environment: DigiaEnvironment = DigiaEnvironment.PRODUCTION,
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
