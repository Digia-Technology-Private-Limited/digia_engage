package com.digia.engage.internal

import com.digia.engage.internal.model.SurveyAction
import com.digia.engage.internal.model.SurveyBoolOp
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyOperator
import com.digia.engage.internal.model.SurveyStepModel

/**
 * A single user answer. [values] holds the comparable tokens used by branching
 * (option ids, or `[score]`, or `[text]`); [comment] holds free-text such as an
 * "other" option's note. Matrix answers encode cells as `"rowId:choiceId"`.
 */
data class SurveyAnswer(
    val values: List<String> = emptyList(),
    val comment: String? = null,
) {
    val isAnswered: Boolean
        get() = values.any { it.isNotBlank() } || !comment.isNullOrBlank()

    fun toMap(): Map<String, Any?> = mapOf("values" to values, "comment" to comment)

    /** Numeric view of a scalar answer (score / numeric input), or `null`. */
    fun asNumber(): Double? = values.firstOrNull()?.trim()?.toDoubleOrNull()
}

/**
 * Pure branching runtime — no Android / Compose dependencies.
 *
 * Implements both mechanisms: answer-jump ([nextStepIndex] via a step's `logic`)
 * and conditional visibility ([isVisible] via a step's `display_logic`).
 */
internal object SurveyLogicHandler {

    /** Sentinel returned when the survey ends. */
    const val FINISHED = -1

    /** Index of the first step that should be shown, honouring display logic. */
    fun firstVisibleIndex(survey: SurveyConfigModel, answers: Map<String, SurveyAnswer>): Int =
        scanForward(survey, from = 0, answers = answers)

    /** Decides the next step after [currentIndex] has been answered. */
    fun nextStepIndex(
        survey: SurveyConfigModel,
        currentIndex: Int,
        answers: Map<String, SurveyAnswer>,
    ): Int {
        val step = survey.steps.getOrNull(currentIndex) ?: return FINISHED
        val answer = answers[step.id]

        for (rule in step.logic) {
            if (!evaluate(rule.operator, rule.values, rule.logicOperator, answer)) continue
            return when (rule.action) {
                SurveyAction.END_SURVEY -> FINISHED
                SurveyAction.GO_TO_NEXT -> scanForward(survey, currentIndex + 1, answers)
                SurveyAction.GO_TO_STEP -> {
                    val target = survey.steps.indexOfFirst { it.id == rule.targetStepId }
                    if (target < 0) scanForward(survey, currentIndex + 1, answers)
                    else scanForward(survey, target, answers)
                }
            }
        }
        return scanForward(survey, currentIndex + 1, answers)
    }

    /** Whether [step] passes its display logic against the answers gathered so far. */
    fun isVisible(step: SurveyStepModel, answers: Map<String, SurveyAnswer>): Boolean {
        if (step.displayLogic.isEmpty()) return true
        val results = step.displayLogic.map { dl ->
            evaluate(dl.operator, dl.values, dl.logicOperator, answers[dl.dependsOnStepId])
        }
        return when (step.displayLogicOperator) {
            SurveyBoolOp.AND -> results.all { it }
            SurveyBoolOp.OR -> results.any { it }
        }
    }

    private fun scanForward(
        survey: SurveyConfigModel,
        from: Int,
        answers: Map<String, SurveyAnswer>,
    ): Int {
        var i = from
        while (i < survey.steps.size) {
            if (isVisible(survey.steps[i], answers)) return i
            i++
        }
        return FINISHED
    }

    private fun evaluate(
        operator: SurveyOperator,
        targets: List<String>,
        logicOp: SurveyBoolOp,
        answer: SurveyAnswer?,
    ): Boolean {
        val answered = answer?.isAnswered == true
        when (operator) {
            SurveyOperator.IS_ANSWERED -> return answered
            SurveyOperator.IS_NOT_ANSWERED -> return !answered
            else -> if (!answered) return false
        }

        val values = answer!!.values
        val text = (answer.values + listOfNotNull(answer.comment)).joinToString(" ").lowercase()

        return when (operator) {
            SurveyOperator.EQUALS -> values.toSet() == targets.toSet()
            SurveyOperator.NOT_EQUALS -> values.toSet() != targets.toSet()
            SurveyOperator.IS_EXACTLY -> values.toSet() == targets.toSet()
            SurveyOperator.INCLUDES_ANY -> targets.any { it in values }
            SurveyOperator.INCLUDES_ALL -> targets.all { it in values }
            SurveyOperator.CONTAINS ->
                combine(logicOp, targets) { text.contains(it.lowercase()) }
            SurveyOperator.NOT_CONTAINS ->
                !combine(logicOp, targets) { text.contains(it.lowercase()) }
            SurveyOperator.GREATER_THAN -> {
                val n = answer.asNumber() ?: return false
                val t = targets.firstOrNull()?.toDoubleOrNull() ?: return false
                n > t
            }
            SurveyOperator.LESS_THAN -> {
                val n = answer.asNumber() ?: return false
                val t = targets.firstOrNull()?.toDoubleOrNull() ?: return false
                n < t
            }
            SurveyOperator.IS_BETWEEN -> {
                val n = answer.asNumber() ?: return false
                val low = targets.getOrNull(0)?.toDoubleOrNull() ?: return false
                val high = targets.getOrNull(1)?.toDoubleOrNull() ?: return false
                n in minOf(low, high)..maxOf(low, high)
            }
            SurveyOperator.IS_ANSWERED, SurveyOperator.IS_NOT_ANSWERED -> true
        }
    }

    private inline fun combine(
        logicOp: SurveyBoolOp,
        targets: List<String>,
        predicate: (String) -> Boolean,
    ): Boolean {
        if (targets.isEmpty()) return false
        return when (logicOp) {
            SurveyBoolOp.AND -> targets.all(predicate)
            SurveyBoolOp.OR -> targets.any(predicate)
        }
    }
}
