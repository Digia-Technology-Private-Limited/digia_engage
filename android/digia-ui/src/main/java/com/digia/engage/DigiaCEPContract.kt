package com.digia.engage

interface DigiaCEPDelegate {
    fun onExperienceReady(payload: InAppPayload)
    fun onExperienceInvalidated(payloadId: String)
}

interface DigiaCEPPlugin {
    val identifier: String
    fun setup(delegate: DigiaCEPDelegate)
    fun forwardScreenEvent(name: String)
    fun notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload)
    fun teardown()
}

data class InAppPayload(
    val id: String,
    val content: Map<String, Any?>,
    val cepContext: Map<String, Any?> = emptyMap(),
)

sealed interface DigiaExperienceEvent {
    data object Impressed : DigiaExperienceEvent
    data class Clicked(val elementId: String? = null) : DigiaExperienceEvent
    data object Dismissed : DigiaExperienceEvent
}
