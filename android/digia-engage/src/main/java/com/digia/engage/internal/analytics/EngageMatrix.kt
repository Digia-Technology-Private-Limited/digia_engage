package com.digia.engage.internal.analytics

import com.digia.engage.internal.model.CopyToClipboardAction
import com.digia.engage.internal.model.DismissAction
import com.digia.engage.internal.model.NudgeAction
import com.digia.engage.internal.model.OpenDeeplinkAction
import com.digia.engage.internal.model.OpenUrlAction
import com.digia.engage.internal.model.ShareAction

/**
 * Pure builders that assemble the engage-event-matrix `properties` map for each
 * rendered campaign type, using the exact matrix snake_case keys. Free functions
 * (no Compose/SDK-state dependencies) so the error-prone mapping logic is
 * unit-tested in isolation; renderers/orchestrators call these at the
 * `AnalyticsService.capture(eventName, payload, properties)` site.
 *
 * Mirrors the Flutter `engage_matrix.dart`. Optional keys are omitted entirely
 * when their source is null — keeps the on-wire payload tight and the builders'
 * intent explicit (the analytics service strips nulls anyway).
 */
internal object EngageMatrix {

    /** Maps a [NudgeAction] to the matrix `action_type` token. */
    fun actionTypeToken(action: NudgeAction): String = when (action) {
        is OpenUrlAction -> "url"
        is OpenDeeplinkAction -> "deeplink"
        is DismissAction -> "dismiss"
        is ShareAction -> "custom"
        is CopyToClipboardAction -> "custom"
    }

    /** The matrix `action_url` for an action, or null for actions without a target. */
    fun actionUrlOf(action: NudgeAction): String? = when (action) {
        is OpenUrlAction -> action.url
        is OpenDeeplinkAction -> action.url
        else -> null
    }

    /**
     * Properties for a nudge `Digia Experience Viewed`. [displayStyle] is required
     * (`bottom_sheet` | `dialog`); trigger/screen context is included only when known.
     */
    fun nudgeViewed(
        displayStyle: String,
        screenName: String? = null,
        triggerType: String? = null,
        triggerEvent: String? = null,
    ): Map<String, Any?> = buildMap {
        put("display_style", displayStyle)
        if (screenName != null) put("screen_name", screenName)
        if (triggerType != null) put("trigger_type", triggerType)
        if (triggerEvent != null) put("trigger_event", triggerEvent)
    }

    /**
     * Properties for a nudge `Digia Experience Clicked`. `element_id` is synthesised
     * from the button's role (`cta_primary` / `cta_secondary`) — a stable id that
     * mirrors `cta_position`. `action_type`/`action_url` come from the button's first
     * action (the navigational intent of the tap).
     */
    fun nudgeClicked(
        label: String,
        isPrimary: Boolean,
        actions: List<NudgeAction>,
        timeToActionMs: Long? = null,
    ): Map<String, Any?> {
        val position = if (isPrimary) "primary" else "secondary"
        val action = actions.firstOrNull()
        val url = action?.let { actionUrlOf(it) }
        return buildMap {
            put("element_id", "cta_$position")
            put("cta_label", label)
            put("cta_position", position)
            if (action != null) put("action_type", actionTypeToken(action))
            if (url != null) put("action_url", url)
            if (timeToActionMs != null) put("time_to_action_ms", timeToActionMs)
        }
    }

    /**
     * Properties for a nudge `Digia Experience Dismissed`. Both fields optional
     * because the dismiss source and view-time are only known when the presentation
     * layer surfaces them.
     */
    fun nudgeDismissed(
        dismissReason: String? = null,
        timeToDismissMs: Long? = null,
    ): Map<String, Any?> = buildMap {
        if (dismissReason != null) put("dismiss_reason", dismissReason)
        if (timeToDismissMs != null) put("time_to_dismiss_ms", timeToDismissMs)
    }

    /**
     * Properties for an inline/guide container `Digia Experience Viewed`.
     * [displayStyle] is `carousel` | `story` | `tooltip` | `spotlight`; [itemTotal]
     * is the slide/story/step count. Screen/trigger context included only when known.
     */
    fun containerViewed(
        displayStyle: String,
        itemTotal: Int,
        screenName: String? = null,
        triggerType: String? = null,
        triggerEvent: String? = null,
    ): Map<String, Any?> = buildMap {
        put("display_style", displayStyle)
        put("item_total", itemTotal)
        if (screenName != null) put("screen_name", screenName)
        if (triggerType != null) put("trigger_type", triggerType)
        if (triggerEvent != null) put("trigger_event", triggerEvent)
    }

    /**
     * Properties shared by `Digia Step Viewed` / `Step Clicked` / `Step Dismissed`
     * (inline frames and guide steps): the 0-based [itemIndex] within [itemTotal],
     * the container [displayStyle], plus optional server [itemId] and (for clicks)
     * the resolved [actionType]/[actionUrl]. Optional fields omitted when null.
     */
    fun step(
        displayStyle: String,
        itemIndex: Int,
        itemTotal: Int,
        itemId: String? = null,
        actionType: String? = null,
        actionUrl: String? = null,
    ): Map<String, Any?> = buildMap {
        put("display_style", displayStyle)
        put("item_index", itemIndex)
        put("item_total", itemTotal)
        if (itemId != null) put("item_id", itemId)
        if (actionType != null) put("action_type", actionType)
        if (actionUrl != null) put("action_url", actionUrl)
    }

    /**
     * Properties for an `Digia Experience Completed` (a story/guide played through
     * all frames/steps). [timeToCompleteMs] — total view-through time — included
     * only when known (the matrix does not hoist it).
     */
    fun completed(
        displayStyle: String,
        itemTotal: Int,
        timeToCompleteMs: Long? = null,
    ): Map<String, Any?> = buildMap {
        put("display_style", displayStyle)
        put("item_total", itemTotal)
        if (timeToCompleteMs != null) put("time_to_complete_ms", timeToCompleteMs)
    }

    /**
     * Properties for a survey `Digia Question Viewed` / `Question Answered` /
     * `Question Skipped`: the 0-based [questionIndex] within [questionTotal] and the
     * server [questionId]/[questionType]. Optional fields omitted when null.
     */
    fun question(
        questionIndex: Int,
        questionTotal: Int,
        questionId: String? = null,
        questionType: String? = null,
    ): Map<String, Any?> = buildMap {
        put("item_index", questionIndex)
        put("item_total", questionTotal)
        if (questionId != null) put("question_id", questionId)
        if (questionType != null) put("question_type", questionType)
    }
}
