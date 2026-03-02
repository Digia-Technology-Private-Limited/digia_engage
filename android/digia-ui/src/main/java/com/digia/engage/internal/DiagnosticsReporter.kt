package com.digia.engage.internal

import com.digia.engage.DiagnosticReport

internal class DiagnosticsReporter(
    private val logger: (String) -> Unit,
) {
    fun report(report: DiagnosticReport, source: String) {
        if (!report.isHealthy) {
            logger(
                "[$source] unhealthy: ${report.issue ?: "unknown"}; " +
                    "resolution=${report.resolution ?: "n/a"}",
            )
        }
    }

    fun reportWarning(message: String) {
        logger(message)
    }
}
