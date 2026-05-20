package com.digia.engage

data class DiagnosticReport(
    val isHealthy: Boolean,
    val issue: String? = null,
    val resolution: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
)
