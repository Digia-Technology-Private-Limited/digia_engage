package com.digia.engage

interface DigiaCEPPlugin {
    val identifier: String
    fun setup(delegate: DigiaCEPDelegate)
    fun forwardScreen(name: String)
    fun notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload)
    fun healthCheck(): DiagnosticReport
    fun teardown()
}
