package com.digia.engage.internal

/**
 * SDK-internal lifecycle events. Distinct from [com.digia.engage.DigiaExperienceEvent]
 * — these never reach the CEP plugin and exist only so the SDK can record
 * fine-grained signals (answers, completions) into its own analytics pipeline.
 */
internal sealed interface InternalEngageEvent {

    /** A survey question was answered. [stepId] is the survey node id. */
    data class SurveyAnswered(
        val stepId: String,
        val answer: Map<String, Any?> = emptyMap(),
    ) : InternalEngageEvent

    /** The survey ran to its terminal step. [response] maps each step id to its answer. */
    data class SurveyCompleted(
        val response: Map<String, Any?> = emptyMap(),
    ) : InternalEngageEvent
}
