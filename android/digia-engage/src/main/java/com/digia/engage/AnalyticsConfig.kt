package com.digia.engage

data class AnalyticsConfig(
    val enabled: Boolean = true,
    val flushIntervalMs: Long = 5_000L,
    val flushBatchSize: Int = 10,
    val maxBatchSize: Int = 100,
    val queueMaxEvents: Int = 5_000,
    val sessionTimeoutMs: Long = 30L * 60 * 1_000,
    val endpointUrl: String = "https://app.digia.tech/api/v1/engage/sdk/track",
)
