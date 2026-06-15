package com.digia.engage.internal

/**
 * SDK-internal lifecycle events. Distinct from [com.digia.engage.DigiaExperienceEvent]
 * — these never reach the CEP plugin and exist only so the SDK can record
 * fine-grained signals (answers, completions) into its own analytics pipeline.
 */
internal sealed interface InternalEngageEvent {

    /** Fired once, when the user answers the survey's first question (engagement signal). */
    data object ExperienceClicked : InternalEngageEvent

    /** Fired once, when the user answers the survey's first question. [stepId] is the survey node id. */
    data class QuestionAnswered(
        val stepId: String,
        val answer: Map<String, Any?> = emptyMap(),
    ) : InternalEngageEvent

    /** The survey ran to its terminal step. [response] maps each step id to its answer. */
    data class ExperienceCompleted(
        val response: Map<String, Any?> = emptyMap(),
    ) : InternalEngageEvent
}
