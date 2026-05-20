package com.digia.engage.internal

import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload

internal class AnalyticsClient(
    private val diagnosticsReporter: DiagnosticsReporter,
) {
    fun track(event: DigiaExperienceEvent, payload: InAppPayload) {
        diagnosticsReporter.reportWarning(
            "analytics track stub: event=${event::class.simpleName}, payload=${payload.id}",
        )
    }
}
