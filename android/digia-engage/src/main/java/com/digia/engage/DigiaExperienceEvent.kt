package com.digia.engage

sealed interface DigiaExperienceEvent {
    data object Impressed : DigiaExperienceEvent
    data class Clicked(val elementId: String? = null) : DigiaExperienceEvent
    data object Dismissed : DigiaExperienceEvent
}
