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

    /** Records SDK-internal events (survey Answered/Completed). Handling TBD. */
    fun trackInternal(event: InternalEngageEvent, payload: InAppPayload) {
        diagnosticsReporter.reportWarning(
            "analytics internal stub: event=${event::class.simpleName}, payload=${payload.id}",
        )
    }
}
