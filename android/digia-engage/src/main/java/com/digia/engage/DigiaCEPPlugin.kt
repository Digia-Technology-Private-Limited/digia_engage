package com.digia.engage

interface DigiaCEPPlugin {
    val identifier: String
    fun setup(delegate: DigiaCEPDelegate)
    fun forwardScreen(name: String)
    fun notifyEvent(event: DigiaExperienceEvent, payload: CEPTriggerPayload)
    fun notifyAction(actionType: String, url: String, payload: CEPTriggerPayload): Boolean = false
    fun healthCheck(): DiagnosticReport
    fun teardown()
}
