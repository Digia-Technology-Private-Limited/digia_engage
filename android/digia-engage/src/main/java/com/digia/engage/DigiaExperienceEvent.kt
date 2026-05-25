package com.digia.engage

/**
 * CEP-facing experience events. The SDK forwards these to the active
 * [DigiaCEPPlugin]; the plugin maps them onto the host CEP's own event model
 * (CleverTap raised event, MoEngage trigger, etc).
 *
 * Survey answer/completion signals are intentionally NOT modelled here — they
 * are SDK-internal (see `InternalEngageEvent`) and never reach the CEP.
 */
sealed interface DigiaExperienceEvent {
    data object Impressed : DigiaExperienceEvent
    data class Clicked(val elementId: String? = null) : DigiaExperienceEvent
    data object Dismissed : DigiaExperienceEvent
}
