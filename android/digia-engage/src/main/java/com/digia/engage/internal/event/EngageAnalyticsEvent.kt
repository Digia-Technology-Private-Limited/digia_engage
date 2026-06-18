package com.digia.engage.internal.event

/** How a host-app surfaced a campaign. Serialized as lower_snake_case. */
internal enum class TriggerType(val wire: String) {
    EVENT("event"),
    SCREEN_VIEW("screen_view"),
    MANUAL("manual"),
    APP_OPEN("app_open"),
}

/** Trigger attribution shared by every campaign's "Viewed" event. */
internal data class TriggerContext(val type: TriggerType, val event: String? = null) {
    fun asProperties(): Map<String, Any?> =
            nonNull("trigger_type" to type.wire, "trigger_event" to event)
}

/**
 * A first-party (Digia analytics) event, modelled 1:1 on the Engage event matrix.
 *
 * Events are grouped by campaign type — [NudgeEvent], [GuideEvent], [SurveyEvent], [CarouselEvent],
 * [StoriesEvent] — so each subtype exposes *exactly* the fields its matrix row defines (ISP): a
 * nudge event cannot carry `has_welcome`, a guide step cannot omit its `item_index`. Each leaf owns
 * its wire [eventName] and how its typed fields serialize — [columns] hoisted to the payload's top
 * level, [properties] nested. Field→wire-key mapping (e.g. step index → `item_index`) lives in the
 * leaf, the single source of truth (SRP).
 *
 * These are Digia-only. CEP forwarding uses the coarse [com.digia.engage.DigiaExperienceEvent] on a
 * separate channel — the two concerns are deliberately not unified.
 */
internal sealed interface EngageAnalyticsEvent {
    val eventName: String
    val properties: Map<String, Any?>
        get() = emptyMap()
}

// ── Nudge (bottom_sheet / dialog; distinguished by displayStyle) ─────────────

internal sealed interface NudgeEvent : EngageAnalyticsEvent {
    data class Viewed(
            val displayStyle: String,
            val trigger: TriggerContext? = null,
            val screenName: String? = null,
    ) : NudgeEvent {
        override val eventName
            get() = "Digia Experience Viewed"
        override val properties
            get() =
                    nonNull("screen_name" to screenName, "display_style" to displayStyle) +
                            trigger.orEmpty()
    }

    data class Clicked(
            val elementId: String? = null,
            val ctaLabel: String? = null,
            val actionType: String? = null,
            val actionUrl: String? = null,
            val ctaRole: String? = null,
            val timeToActionMs: Long? = null,
    ) : NudgeEvent {
        override val eventName
            get() = "Digia Experience Clicked"
        override val properties
            get() =
                    nonNull(
                            "cta_role" to ctaRole,
                            "time_to_action_ms" to timeToActionMs,
                            "element_id" to elementId,
                            "cta_label" to ctaLabel,
                            "action_type" to actionType,
                            "action_url" to actionUrl,
                    )
    }

    data class Dismissed(
            val dwellMs: Long? = null,
    ) : NudgeEvent {
        override val eventName
            get() = "Digia Experience Dismissed"
        override val properties
            get() = nonNull("dwell_ms" to dwellMs)
    }
}

// ── Guide (tooltip / spotlight; distinguished by displayStyle) ───────────────

internal sealed interface GuideEvent : EngageAnalyticsEvent {
    data class Viewed(
            val displayStyle: String,
            val itemTotal: Int,
            val trigger: TriggerContext? = null,
            val screenName: String? = null,
    ) : GuideEvent {
        override val eventName
            get() = "Digia Experience Viewed"
        override val properties
            get() =
                    nonNull(
                            "screen_name" to screenName,
                            "display_style" to displayStyle,
                            "item_total" to itemTotal,
                    ) + trigger.orEmpty()
    }

    data class StepViewed(
            val itemIndex: Int,
            val itemTotal: Int,
            val anchorKey: String? = null,
            val displayStyle: String? = null,
    ) : GuideEvent {
        override val eventName
            get() = "Digia Step Viewed"
        override val properties
            get() =
                    nonNull(
                            "item_index" to itemIndex,
                            "item_total" to itemTotal,
                            "anchor_key" to anchorKey,
                            "display_style" to displayStyle,
                    )
    }

    data class StepClicked(
            val itemIndex: Int,
            val elementId: String? = null,
            val ctaLabel: String? = null,
            val actionType: String? = null,
            val actionUrl: String? = null,
    ) : GuideEvent {
        override val eventName
            get() = "Digia Step Clicked"
        override val properties
            get() =
                    nonNull(
                            "item_index" to itemIndex,
                            "element_id" to elementId,
                            "cta_label" to ctaLabel,
                            "action_type" to actionType,
                            "action_url" to actionUrl,
                    )
    }

    data class StepDismissed(
            val itemIndex: Int,
    ) : GuideEvent {
        override val eventName
            get() = "Digia Step Dismissed"
        override val properties
            get() = nonNull("item_index" to itemIndex)
    }

    /** Guide abandoned (rolls up step-level dismiss). */
    data class Dismissed(
            val abandonedAtItem: Int? = null,
            val itemTotal: Int? = null,
            val dwellMs: Long? = null,
    ) : GuideEvent {
        override val eventName
            get() = "Digia Experience Dismissed"
        override val properties
            get() =
                    nonNull(
                            "abandoned_at_item" to abandonedAtItem,
                            "item_total" to itemTotal,
                            "dwell_ms" to dwellMs
                    )
    }

    data class Completed(
            val itemTotal: Int? = null,
            val timeToCompleteMs: Long? = null,
    ) : GuideEvent {
        override val eventName
            get() = "Digia Experience Completed"
        override val properties
            get() = nonNull("time_to_complete_ms" to timeToCompleteMs, "item_total" to itemTotal)
    }
}

// ── Survey ──────────────────────────────────────────────────────────────────

internal sealed interface SurveyEvent : EngageAnalyticsEvent {
    data class Viewed(
            val itemTotal: Int? = null,
            val hasWelcome: Boolean? = null,
            val hasThanks: Boolean? = null,
            val hasBranching: Boolean? = null,
            val trigger: TriggerContext? = null,
            val screenName: String? = null,
    ) : SurveyEvent {
        override val eventName
            get() = "Digia Experience Viewed"
        override val properties
            get() =
                    nonNull(
                            "has_welcome" to hasWelcome,
                            "has_thanks" to hasThanks,
                            "has_branching" to hasBranching,
                            "screen_name" to screenName,
                            "item_total" to itemTotal
                    ) + trigger.orEmpty()
    }

    /** Start tapped on the welcome screen, or first-answer engagement. */
    data class Clicked(
            val elementId: String? = null,
    ) : SurveyEvent {
        override val eventName
            get() = "Digia Experience Clicked"
        override val properties
            get() = nonNull("element_id" to elementId)
    }

    data class QuestionViewed(
            val questionId: String,
            val questionType: String? = null,
            val itemIndex: Int? = null,
            val itemTotal: Int? = null,
            val blockType: String? = null,
            val blockId: String? = null,
            val isRequired: Boolean? = null,
            val questionTitle: String? = null,
    ) : SurveyEvent {
        override val eventName
            get() = "Digia Question Viewed"
        override val properties
            get() =
                    nonNull(
                            "block_type" to blockType,
                            "block_id" to blockId,
                            "is_required" to isRequired,
                            "question_title" to questionTitle,
                            "question_id" to questionId,
                            "question_type" to questionType,
                            "item_index" to itemIndex,
                            "item_total" to itemTotal,
                    )
    }

    data class QuestionAnswered(
            val questionId: String,
            val questionType: String? = null,
            val questionTitle: String? = null,
            val answerValue: String? = null,
            val answerText: String? = null,
            val blockType: String? = null,
            val blockId: String? = null,
            val answerLabel: String? = null,
            val answerOptions: List<String>? = null,
            val scaleMin: Int? = null,
            val scaleMax: Int? = null,
            val timeToAnswerMs: Long? = null,
            /** Raw answer envelope (`values`, `comment`) — preserved as-is. */
            val answer: Map<String, Any?> = emptyMap(),
    ) : SurveyEvent {
        override val eventName
            get() = "Digia Question Answered"
        override val properties
            get() =
                    nonNull(
                            "question_id" to questionId,
                            "question_type" to questionType,
                            "question_title" to questionTitle,
                            "answer_value" to answerValue,
                            "answer_text" to answerText,
                            "block_type" to blockType,
                            "block_id" to blockId,
                            "answer_label" to answerLabel,
                            "answer_options" to answerOptions,
                            "scale_min" to scaleMin,
                            "scale_max" to scaleMax,
                            "time_to_answer_ms" to timeToAnswerMs,
                            "answer" to answer.takeIf { it.isNotEmpty() },
                    )
    }

    data class QuestionSkipped(
            val questionId: String,
            val itemIndex: Int? = null,
            val blockType: String? = null,
            val blockId: String? = null,
            val questionTitle: String? = null,
    ) : SurveyEvent {
        override val eventName
            get() = "Digia Question Skipped"
        override val properties
            get() =
                    nonNull(
                            "block_type" to blockType,
                            "block_id" to blockId,
                            "question_id" to questionId,
                            "item_index" to itemIndex,
                            "question_title" to questionTitle
                    )
    }

    data class Dismissed(
            val abandonedAtItem: Int? = null,
            val itemTotal: Int? = null,
            val answeredCount: Int? = null,
            val dwellMs: Long? = null,
    ) : SurveyEvent {
        override val eventName
            get() = "Digia Experience Dismissed"
        override val properties
            get() =
                    nonNull(
                            "answered_count" to answeredCount,
                            "dwell_ms" to dwellMs,
                            "abandoned_at_item" to abandonedAtItem,
                            "item_total" to itemTotal,
                    )
    }

    data class Completed(
            val itemTotal: Int? = null,
            val answeredCount: Int? = null,
            val submissionId: String? = null,
            val timeToCompleteMs: Long? = null,
            /** Submitted answers, keyed by node id (each value is the answer envelope). */
            val response: Map<String, Any?> = emptyMap(),
    ) : SurveyEvent {
        override val eventName
            get() = "Digia Experience Completed"
        override val properties
            get() =
                    nonNull(
                            "item_total" to itemTotal,
                            "answered_count" to answeredCount,
                            "submission_id" to submissionId,
                            "time_to_complete_ms" to timeToCompleteMs,
                            "response" to response.takeIf { it.isNotEmpty() },
                    )
    }
}

// ── Inline: carousel ────────────────────────────────────────────────────────

internal sealed interface CarouselEvent : EngageAnalyticsEvent {
    data class Viewed(
            val itemTotal: Int? = null,
            val slotKey: String? = null,
            val screenName: String? = null,
    ) : CarouselEvent {
        override val eventName
            get() = "Digia Experience Viewed"

        override val properties
            get() =
                    nonNull(
                            "screen_name" to screenName,
                            "display_style" to "carousel",
                            "item_total" to itemTotal,
                            "slot_key" to slotKey,
                    )
    }

    data class StepViewed(
            val itemIndex: Int,
            val itemTotal: Int? = null,
            val itemId: String? = null,
            /** True when the carousel auto-advanced to this item; false on a manual swipe. */
            val auto: Boolean? = null,
    ) : CarouselEvent {
        override val eventName
            get() = "Digia Step Viewed"
        override val properties
            get() =
                    nonNull(
                            "item_id" to itemId,
                            "auto" to auto,
                            "item_index" to itemIndex,
                            "item_total" to itemTotal
                    )
    }

    data class StepClicked(
            val itemIndex: Int,
            val elementId: String? = null,
            val ctaLabel: String? = null,
            val actionType: String? = null,
            val actionUrl: String? = null,
            val itemId: String? = null,
    ) : CarouselEvent {
        override val eventName
            get() = "Digia Step Clicked"
        override val properties
            get() =
                    nonNull(
                            "item_id" to itemId,
                            "item_index" to itemIndex,
                            "element_id" to elementId,
                            "cta_label" to ctaLabel,
                            "action_type" to actionType,
                            "action_url" to actionUrl,
                    )
    }

    /** Carousel container tapped (non-item region). */
    data class Clicked(
            val elementId: String? = null,
            val ctaLabel: String? = null,
            val actionType: String? = null,
            val actionUrl: String? = null,
    ) : CarouselEvent {
        override val eventName
            get() = "Digia Experience Clicked"
        override val properties
            get() =
                    nonNull(
                            "element_id" to elementId,
                            "cta_label" to ctaLabel,
                            "action_type" to actionType,
                            "action_url" to actionUrl,
                    )
    }
}

// ── Inline: stories ─────────────────────────────────────────────────────────

internal sealed interface StoriesEvent : EngageAnalyticsEvent {
    data class Viewed(
            val itemTotal: Int? = null,
            val slotKey: String? = null,
            val screenName: String? = null,
    ) : StoriesEvent {
        override val eventName
            get() = "Digia Experience Viewed"
        override val properties
            get() =
                    nonNull(
                            "screen_name" to screenName,
                            "display_style" to "stories",
                            "item_total" to itemTotal,
                            "slot_key" to slotKey,
                    )
    }

    /** A story is opened (ring/thumbnail tapped). */
    data class Opened(
            val storyId: String? = null,
    ) : StoriesEvent {
        override val eventName
            get() = "Digia Experience Clicked"
        override val properties
            get() = nonNull("story_id" to storyId, "element_id" to "story_open")
    }

    data class StepViewed(
            val itemIndex: Int,
            val itemTotal: Int? = null,
            val storyId: String? = null,
            val frameId: String? = null,
    ) : StoriesEvent {
        override val eventName
            get() = "Digia Step Viewed"
        override val properties
            get() =
                    nonNull(
                            "story_id" to storyId,
                            "frame_id" to frameId,
                            "item_index" to itemIndex,
                            "item_total" to itemTotal
                    )
    }

    data class StepClicked(
            val itemIndex: Int,
            val ctaLabel: String? = null,
            val actionType: String? = null,
            val actionUrl: String? = null,
            val frameId: String? = null,
    ) : StoriesEvent {
        override val eventName
            get() = "Digia Step Clicked"
        override val properties
            get() =
                    nonNull(
                            "frame_id" to frameId,
                            "item_index" to itemIndex,
                            "cta_label" to ctaLabel,
                            "action_type" to actionType,
                            "action_url" to actionUrl,
                    )
    }

    data class StepDismissed(
            val itemIndex: Int,
    ) : StoriesEvent {
        override val eventName
            get() = "Digia Step Dismissed"
        override val properties
            get() = nonNull("item_index" to itemIndex)
    }

    data class Completed(
            val itemTotal: Int? = null,
            val timeToCompleteMs: Long? = null,
    ) : StoriesEvent {
        override val eventName
            get() = "Digia Experience Completed"
        override val properties
            get() = nonNull("time_to_complete_ms" to timeToCompleteMs, "item_total" to itemTotal)
    }
}

/** Builds a wire map from named fields, dropping any whose value is null. */
private fun nonNull(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()

private fun TriggerContext?.orEmpty(): Map<String, Any?> = this?.asProperties() ?: emptyMap()
