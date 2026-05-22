package com.digia.engage.internal.model

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Survey schema + branching models for a `campaign_type == "survey"` campaign.
 *
 * The definition is delivered by the **getCampaigns API** (see [CampaignModel])
 * as a `survey_config` object — digia_engage does not embed survey content in the
 * CEP trigger; the CEP only fires a `campaign_key`.
 *
 * Shape follows Userpilot's flat structure with Survicate's branching power —
 * answer-jump (`logic`) + conditional visibility (`display_logic`).
 */

// ── enums ───────────────────────────────────────────────────────────────────

enum class SurveySurface { BOTTOM_SHEET, DIALOG }

enum class SurveyBoolOp { AND, OR }

enum class SurveyQuestionType {
    NPS, RATING, CSAT, SINGLE_CHOICE, MULTIPLE_CHOICE,
    OPEN_TEXT, SINGLE_INPUT, MATRIX, THANK_YOU,
}

enum class SurveyRatingStyle { STARS, HEARTS, EMOJIS, NUMBERS }

enum class SurveyInputType { TEXT, EMAIL, PHONE, NUMBER, DATE }

enum class SurveyOperator {
    EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS,
    INCLUDES_ALL, INCLUDES_ANY, IS_EXACTLY,
    GREATER_THAN, LESS_THAN, IS_BETWEEN,
    IS_ANSWERED, IS_NOT_ANSWERED,
}

enum class SurveyAction { GO_TO_STEP, GO_TO_NEXT, END_SURVEY }

// ── value objects ───────────────────────────────────────────────────────────

data class SurveyChoice(val id: String, val label: String)

data class SurveyMatrixRow(val id: String, val label: String)

/** Per-question-type config — a union; only fields for the owning type apply. */
data class SurveyStepMetadata(
    val ratingStyle: SurveyRatingStyle = SurveyRatingStyle.STARS,
    val range: Int = 5,
    val lowLabel: String? = null,
    val highLabel: String? = null,
    val choices: List<SurveyChoice> = emptyList(),
    val isMultiSelect: Boolean = false,
    val otherChoiceEnabled: Boolean = false,
    val otherChoicePlaceholder: String? = null,
    val placeholder: String? = null,
    val inputType: SurveyInputType = SurveyInputType.TEXT,
    val maxLength: Int? = null,
    val matrixRows: List<SurveyMatrixRow> = emptyList(),
    val bodyText: String? = null,
)

/** Answer-jump rule: first rule whose condition holds decides the next step. */
data class SurveyLogic(
    val operator: SurveyOperator,
    val values: List<String> = emptyList(),
    val logicOperator: SurveyBoolOp = SurveyBoolOp.OR,
    val action: SurveyAction = SurveyAction.GO_TO_NEXT,
    val targetStepId: String? = null,
)

/** Conditional-visibility rule: a step is shown only if these are satisfied. */
data class SurveyDisplayLogic(
    val dependsOnStepId: String,
    val operator: SurveyOperator,
    val values: List<String> = emptyList(),
    val logicOperator: SurveyBoolOp = SurveyBoolOp.OR,
)

data class SurveyStepModel(
    val id: String,
    val sequenceOrder: Int,
    val type: SurveyQuestionType,
    val question: String? = null,
    val subheader: String? = null,
    val buttonLabel: String? = null,
    val isRequired: Boolean = false,
    val metadata: SurveyStepMetadata = SurveyStepMetadata(),
    val logic: List<SurveyLogic> = emptyList(),
    val displayLogic: List<SurveyDisplayLogic> = emptyList(),
    val displayLogicOperator: SurveyBoolOp = SurveyBoolOp.AND,
)

data class SurveyTheme(
    val accentColor: Int,
    val backgroundColor: Int,
)

data class SurveyConfigModel(
    val id: String,
    val surface: SurveySurface,
    val steps: List<SurveyStepModel>,
    val title: String?,
    val showProgress: Boolean,
    val dismissible: Boolean,
    val theme: SurveyTheme,
    val timeDelayMs: Int,
) {
    companion object {
        private val DEFAULT_ACCENT = Color.parseColor("#2D6CDF")
        private val DEFAULT_BACKGROUND = Color.parseColor("#FFFFFF")

        /**
         * Parses a `survey_config` JSON object. Lenient — bad entries fall back to
         * defaults; returns `null` if there are no usable steps.
         *
         * @param fallbackId used when the config omits an id (e.g. the campaign id).
         */
        fun fromJson(json: JSONObject, fallbackId: String): SurveyConfigModel? {
            val stepsArr = json.optJSONArray("steps") ?: return null
            val steps = mutableListOf<SurveyStepModel>()
            for (i in 0 until stepsArr.length()) {
                val obj = stepsArr.optJSONObject(i) ?: continue
                parseStep(obj, i)?.let(steps::add)
            }
            if (steps.isEmpty()) return null

            val themeObj = json.optJSONObject("theme") ?: JSONObject()
            return SurveyConfigModel(
                id = json.optString("id", "").ifBlank { json.optString("_id", "") }
                    .ifBlank { fallbackId },
                surface = parseSurface(json.optString("surface")),
                steps = steps.sortedBy { it.sequenceOrder },
                title = json.optString("title", "").takeIf { it.isNotBlank() },
                showProgress = json.optBoolean("show_progress", true),
                dismissible = json.optBoolean("dismissible", true),
                theme = SurveyTheme(
                    accentColor = parseColor(themeObj.optString("accent_color"), DEFAULT_ACCENT),
                    backgroundColor = parseColor(
                        themeObj.optString("background_color"), DEFAULT_BACKGROUND,
                    ),
                ),
                timeDelayMs = json.optInt("time_delay_ms", 0).coerceIn(0, 10_000),
            )
        }

        private fun parseStep(json: JSONObject, index: Int): SurveyStepModel? {
            val id = json.optString("id", "").ifBlank { json.optString("_id", "") }
                .takeIf { it.isNotBlank() } ?: return null
            val type = parseType(json.optString("type")) ?: return null
            return SurveyStepModel(
                id = id,
                sequenceOrder = json.optInt("sequence_order", index),
                type = type,
                question = json.optString("question", "").takeIf { it.isNotBlank() },
                subheader = json.optString("subheader", "").takeIf { it.isNotBlank() },
                buttonLabel = json.optString("button_label", "").takeIf { it.isNotBlank() },
                isRequired = json.optBoolean("required", false),
                metadata = parseMetadata(json.optJSONObject("metadata") ?: JSONObject()),
                logic = parseLogicList(json.optJSONArray("logic")),
                displayLogic = parseDisplayLogicList(json.optJSONArray("display_logic")),
                displayLogicOperator = parseBoolOp(
                    json.optString("display_logic_operator"), SurveyBoolOp.AND,
                ),
            )
        }

        private fun parseMetadata(json: JSONObject): SurveyStepMetadata = SurveyStepMetadata(
            ratingStyle = parseRatingStyle(json.optString("rating_style")),
            range = json.optInt("range", 5).coerceIn(2, 11),
            lowLabel = json.optString("low_label", "").takeIf { it.isNotBlank() },
            highLabel = json.optString("high_label", "").takeIf { it.isNotBlank() },
            choices = parseChoices(json.optJSONArray("choices")),
            isMultiSelect = json.optBoolean("multi_select", false),
            otherChoiceEnabled = json.optBoolean("other_enabled", false),
            otherChoicePlaceholder = json.optString("other_placeholder", "")
                .takeIf { it.isNotBlank() },
            placeholder = json.optString("placeholder", "").takeIf { it.isNotBlank() },
            inputType = parseInputType(json.optString("input_type")),
            maxLength = json.optInt("max_length", 0).takeIf { it > 0 },
            matrixRows = parseMatrixRows(json.optJSONArray("rows")),
            bodyText = json.optString("body", "").takeIf { it.isNotBlank() },
        )

        private fun parseChoices(arr: JSONArray?): List<SurveyChoice> {
            if (arr == null) return emptyList()
            val out = mutableListOf<SurveyChoice>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                    val label = obj.optString("label", "").ifBlank { obj.optString("value", id) }
                    out.add(SurveyChoice(id, label))
                } else {
                    val str = arr.optString(i, "").takeIf { it.isNotBlank() } ?: continue
                    out.add(SurveyChoice(str, str))
                }
            }
            return out
        }

        private fun parseMatrixRows(arr: JSONArray?): List<SurveyMatrixRow> {
            if (arr == null) return emptyList()
            val out = mutableListOf<SurveyMatrixRow>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                out.add(SurveyMatrixRow(id, obj.optString("label", "").ifBlank { id }))
            }
            return out
        }

        private fun parseLogicList(arr: JSONArray?): List<SurveyLogic> {
            if (arr == null) return emptyList()
            val out = mutableListOf<SurveyLogic>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val operator = parseOperator(obj.optString("operator")) ?: continue
                out.add(
                    SurveyLogic(
                        operator = operator,
                        values = parseStringArray(obj.optJSONArray("values")),
                        logicOperator = parseBoolOp(obj.optString("logic_operator"), SurveyBoolOp.OR),
                        action = parseAction(obj.optString("action")),
                        targetStepId = obj.optString("target_step_id", "")
                            .ifBlank { obj.optString("go_to", "") }
                            .takeIf { it.isNotBlank() },
                    ),
                )
            }
            return out
        }

        private fun parseDisplayLogicList(arr: JSONArray?): List<SurveyDisplayLogic> {
            if (arr == null) return emptyList()
            val out = mutableListOf<SurveyDisplayLogic>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val dependsOn = obj.optString("depends_on", "")
                    .ifBlank { obj.optString("depends_on_step_id", "") }
                    .takeIf { it.isNotBlank() } ?: continue
                val operator = parseOperator(obj.optString("operator")) ?: continue
                out.add(
                    SurveyDisplayLogic(
                        dependsOnStepId = dependsOn,
                        operator = operator,
                        values = parseStringArray(obj.optJSONArray("values")),
                        logicOperator = parseBoolOp(obj.optString("logic_operator"), SurveyBoolOp.OR),
                    ),
                )
            }
            return out
        }

        private fun parseStringArray(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                arr.optString(i, "").takeIf { it.isNotBlank() }?.let(out::add)
            }
            return out
        }

        private fun parseSurface(value: String?): SurveySurface =
            when (value?.trim()?.lowercase()) {
                "dialog", "center" -> SurveySurface.DIALOG
                else -> SurveySurface.BOTTOM_SHEET
            }

        private fun parseType(value: String?): SurveyQuestionType? =
            when (value?.trim()?.lowercase()?.replace("-", "_")) {
                "nps" -> SurveyQuestionType.NPS
                "rating", "star", "likert_scale" -> SurveyQuestionType.RATING
                "csat", "smiley_scale", "smiley" -> SurveyQuestionType.CSAT
                "single_choice", "single" -> SurveyQuestionType.SINGLE_CHOICE
                "multiple_choice", "multiple", "multi" -> SurveyQuestionType.MULTIPLE_CHOICE
                "open_text", "text", "long_text" -> SurveyQuestionType.OPEN_TEXT
                "single_input", "input" -> SurveyQuestionType.SINGLE_INPUT
                "matrix" -> SurveyQuestionType.MATRIX
                "thank_you", "completed", "thankyou" -> SurveyQuestionType.THANK_YOU
                else -> null
            }

        private fun parseRatingStyle(value: String?): SurveyRatingStyle =
            when (value?.trim()?.lowercase()) {
                "hearts", "heart" -> SurveyRatingStyle.HEARTS
                "emojis", "emoji" -> SurveyRatingStyle.EMOJIS
                "numbers", "number", "numeric" -> SurveyRatingStyle.NUMBERS
                else -> SurveyRatingStyle.STARS
            }

        private fun parseInputType(value: String?): SurveyInputType =
            when (value?.trim()?.lowercase()) {
                "email" -> SurveyInputType.EMAIL
                "phone", "tel" -> SurveyInputType.PHONE
                "number", "numeric" -> SurveyInputType.NUMBER
                "date" -> SurveyInputType.DATE
                else -> SurveyInputType.TEXT
            }

        private fun parseOperator(value: String?): SurveyOperator? =
            when (value?.trim()?.lowercase()?.replace("-", "_")) {
                "equals", "is", "equal" -> SurveyOperator.EQUALS
                "not_equals", "is_not", "not_equal" -> SurveyOperator.NOT_EQUALS
                "contains", "answer_contains" -> SurveyOperator.CONTAINS
                "not_contains", "answer_does_not_contain" -> SurveyOperator.NOT_CONTAINS
                "includes_all" -> SurveyOperator.INCLUDES_ALL
                "includes_any", "any" -> SurveyOperator.INCLUDES_ANY
                "is_exactly", "all" -> SurveyOperator.IS_EXACTLY
                "greater_than", "gt" -> SurveyOperator.GREATER_THAN
                "less_than", "lt" -> SurveyOperator.LESS_THAN
                "is_between", "between" -> SurveyOperator.IS_BETWEEN
                "is_answered", "known", "has_any_value", "question_is_answered" ->
                    SurveyOperator.IS_ANSWERED
                "is_not_answered", "not_known", "question_is_not_answered" ->
                    SurveyOperator.IS_NOT_ANSWERED
                else -> null
            }

        private fun parseAction(value: String?): SurveyAction =
            when (value?.trim()?.lowercase()?.replace("-", "_")) {
                "go_to_step", "go_to_module" -> SurveyAction.GO_TO_STEP
                "end_survey", "finish" -> SurveyAction.END_SURVEY
                else -> SurveyAction.GO_TO_NEXT
            }

        private fun parseBoolOp(value: String?, default: SurveyBoolOp): SurveyBoolOp =
            when (value?.trim()?.lowercase()) {
                "and" -> SurveyBoolOp.AND
                "or" -> SurveyBoolOp.OR
                else -> default
            }

        private fun parseColor(value: String?, default: Int): Int {
            if (value.isNullOrBlank()) return default
            return try {
                Color.parseColor(value)
            } catch (_: Exception) {
                default
            }
        }
    }
}
