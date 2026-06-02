package com.digia.engage.internal

import com.digia.engage.internal.model.BoolOp
import com.digia.engage.internal.model.BranchTarget
import com.digia.engage.internal.model.BranchTargetKind
import com.digia.engage.internal.model.BranchingType
import com.digia.engage.internal.model.ConditionExpr
import com.digia.engage.internal.model.ConditionOperator
import com.digia.engage.internal.model.NodeBranching
import com.digia.engage.internal.model.SurveyBlock
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyNode

/**
 * A single user answer. [values] holds the comparable tokens used by branching
 * (option ids, or `[score]`, or `[text]`); [comment] holds free-text such as
 * an "other" option's note.
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

/** Sentinel id meaning "the survey is finished". */
internal const val SURVEY_FINISHED: String = "__digia_survey_finished__"

internal data class SurveyNavigation(
    /** Target node id, or [SURVEY_FINISHED] when the survey should end. */
    val nextNodeId: String,
    val redirectUrl: String? = null,
)

/**
 * Pure branching runtime — operates on the node graph (no Android / Compose
 * dependencies). Resolves node-owned branching rules and conditional block
 * visibility (`showWhen`).
 */
internal object SurveyLogicHandler {

    /** Id of the first node that should be shown, honouring `showWhen`. */
    fun firstNodeId(survey: SurveyConfigModel, answers: Map<String, SurveyAnswer>): String {
        val root = survey.rootNode() ?: return SURVEY_FINISHED
        return scanForwardFrom(survey, root, answers)
    }

    /**
     * Decides the next node after [currentNodeId] has been answered. Always
     * returns a valid node id or [SURVEY_FINISHED].
     */
    fun nextStep(
        survey: SurveyConfigModel,
        currentNodeId: String,
        answers: Map<String, SurveyAnswer>,
    ): SurveyNavigation {
        val node = survey.nodeById(currentNodeId) ?: return SurveyNavigation(SURVEY_FINISHED)
        val branching = node.branching

        if (branching.type != BranchingType.LINEAR) {
            for (rule in branching.rules) {
                if (evaluateExpr(rule.whenExpr, node, branching, answers)) {
                    return resolveTarget(survey, currentNodeId, rule.target, answers)
                }
            }
        }
        return resolveTarget(survey, currentNodeId, branching.defaultTarget, answers)
    }

    /** Whether [block] passes its authored `showWhen` gate. */
    fun isVisible(
        block: SurveyBlock,
        ownerNodeId: String,
        answers: Map<String, SurveyAnswer>,
    ): Boolean {
        val expr = block.showWhen ?: return true
        return evaluateExprForNode(expr, ownerNodeId, answers)
    }

    // ── internal helpers ────────────────────────────────────────────────────

    private fun scanForwardFrom(
        survey: SurveyConfigModel,
        startNode: SurveyNode,
        answers: Map<String, SurveyAnswer>,
        visited: MutableSet<String> = mutableSetOf(),
    ): String {
        var current: SurveyNode? = startNode
        while (current != null) {
            if (!visited.add(current.id)) return SURVEY_FINISHED  // cycle guard
            val block = survey.blockFor(current)
            if (block == null || isVisible(block, current.id, answers)) return current.id
            current = nextNodeAfter(survey, current, answers)
        }
        return SURVEY_FINISHED
    }

    private fun nextNodeAfter(
        survey: SurveyConfigModel,
        node: SurveyNode,
        answers: Map<String, SurveyAnswer>,
    ): SurveyNode? {
        val target = node.branching.defaultTarget
        return when (target.kind) {
            BranchTargetKind.NODE -> survey.nodeById(target.nodeId)
            BranchTargetKind.NEXT -> {
                val idx = survey.nodes.indexOfFirst { it.id == node.id }
                survey.nodes.getOrNull(idx + 1)
            }
            BranchTargetKind.URL, BranchTargetKind.END -> null
        }
    }

    private fun resolveTarget(
        survey: SurveyConfigModel,
        currentNodeId: String,
        target: BranchTarget,
        answers: Map<String, SurveyAnswer>,
    ): SurveyNavigation =
        when (target.kind) {
            BranchTargetKind.END -> SurveyNavigation(SURVEY_FINISHED)
            BranchTargetKind.URL -> SurveyNavigation(SURVEY_FINISHED, target.url.takeIf { it.isNotBlank() })
            BranchTargetKind.NODE -> {
                val next = survey.nodeById(target.nodeId)
                if (next == null) SurveyNavigation(SURVEY_FINISHED)
                else SurveyNavigation(scanForwardFrom(survey, next, answers))
            }
            BranchTargetKind.NEXT -> {
                val idx = survey.nodes.indexOfFirst { it.id == currentNodeId }
                val next = survey.nodes.getOrNull(idx + 1)
                if (next == null) SurveyNavigation(SURVEY_FINISHED)
                else SurveyNavigation(scanForwardFrom(survey, next, answers))
            }
        }

    private fun evaluateExpr(
        expr: ConditionExpr,
        ownerNode: SurveyNode,
        branching: NodeBranching,
        answers: Map<String, SurveyAnswer>,
    ): Boolean {
        // `by_parent` rewrites a condition's null nodeId to the configured parent;
        // `by_condition` (and `linear` fallback) treats null nodeId as the owner.
        val defaultAnswerNodeId = when (branching.type) {
            BranchingType.BY_PARENT -> branching.parentNodeId ?: ownerNode.id
            else -> ownerNode.id
        }
        return evaluateExprForNode(expr, defaultAnswerNodeId, answers)
    }

    private fun evaluateExprForNode(
        expr: ConditionExpr,
        defaultAnswerNodeId: String,
        answers: Map<String, SurveyAnswer>,
    ): Boolean {
        val groupResults = expr.groups.map { group ->
            val conditionResults = group.conditions.map { condition ->
                val answerNodeId = condition.nodeId ?: defaultAnswerNodeId
                evaluate(condition.operator, condition.values, group.operator, answers[answerNodeId])
            }
            when (group.operator) {
                BoolOp.AND -> conditionResults.all { it }
                BoolOp.OR -> conditionResults.any { it }
            }
        }
        return when (expr.operator) {
            BoolOp.AND -> groupResults.all { it }
            BoolOp.OR -> groupResults.any { it }
        }
    }

    private fun evaluate(
        operator: ConditionOperator,
        targets: List<String>,
        logicOp: BoolOp,
        answer: SurveyAnswer?,
    ): Boolean {
        val answered = answer?.isAnswered == true
        when (operator) {
            ConditionOperator.IS_ANSWERED -> return answered
            ConditionOperator.IS_NOT_ANSWERED -> return !answered
            else -> if (!answered) return false
        }

        val values = answer!!.values
        val text = (values + listOfNotNull(answer.comment)).joinToString(" ").lowercase()

        return when (operator) {
            ConditionOperator.EQUALS,
            ConditionOperator.IS_EXACTLY -> values.toSet() == targets.toSet()
            ConditionOperator.NOT_EQUALS -> values.toSet() != targets.toSet()
            ConditionOperator.INCLUDES_ANY -> targets.any { it in values }
            ConditionOperator.INCLUDES_ALL -> targets.all { it in values }
            ConditionOperator.CONTAINS ->
                combine(logicOp, targets) { text.contains(it.lowercase()) }
            ConditionOperator.NOT_CONTAINS ->
                !combine(logicOp, targets) { text.contains(it.lowercase()) }
            ConditionOperator.GREATER_THAN -> {
                val n = answer.asNumber() ?: return false
                val t = targets.firstOrNull()?.toDoubleOrNull() ?: return false
                n > t
            }
            ConditionOperator.LESS_THAN -> {
                val n = answer.asNumber() ?: return false
                val t = targets.firstOrNull()?.toDoubleOrNull() ?: return false
                n < t
            }
            ConditionOperator.IS_BETWEEN -> {
                val n = answer.asNumber() ?: return false
                val low = targets.getOrNull(0)?.toDoubleOrNull() ?: return false
                val high = targets.getOrNull(1)?.toDoubleOrNull() ?: return false
                n in minOf(low, high)..maxOf(low, high)
            }
            ConditionOperator.IS_ANSWERED, ConditionOperator.IS_NOT_ANSWERED -> true
        }
    }

    private inline fun combine(
        logicOp: BoolOp,
        targets: List<String>,
        predicate: (String) -> Boolean,
    ): Boolean {
        if (targets.isEmpty()) return false
        return when (logicOp) {
            BoolOp.AND -> targets.all(predicate)
            BoolOp.OR -> targets.any(predicate)
        }
    }
}
