package com.digia.engage

sealed interface DigiaExperienceEvent {
    data object Impressed : DigiaExperienceEvent
    data class Clicked(val elementId: String? = null) : DigiaExperienceEvent
    data object Dismissed : DigiaExperienceEvent

    /** A survey question was answered. [answer] carries `values` + `comment`. */
    data class Answered(
        val stepId: String,
        val answer: Map<String, Any?> = emptyMap(),
    ) : DigiaExperienceEvent

    /** A survey ran to its end. [response] maps each step id to its answer. */
    data class Completed(
        val response: Map<String, Any?> = emptyMap(),
    ) : DigiaExperienceEvent
}
