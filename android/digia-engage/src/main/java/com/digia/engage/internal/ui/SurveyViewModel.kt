package com.digia.engage.internal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.digia.engage.internal.SurveyAnswer
import com.digia.engage.internal.SurveyLogicHandler
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyQuestionType
import com.digia.engage.internal.model.SurveyStepModel

/**
 * Holds the in-progress state of one survey showing: the answers collected so
 * far and the position in the (possibly branching) step graph.
 *
 * Lives in a [ViewModel] so a configuration change does not lose answers.
 */
internal class SurveyViewModel(val survey: SurveyConfigModel) : ViewModel() {

    /** stepId → answer. Snapshot-backed so widgets recompose on edit. */
    val answers = mutableStateMapOf<String, SurveyAnswer>()

    private val backStack = ArrayDeque<Int>()

    var currentIndex by mutableStateOf(
        SurveyLogicHandler.firstVisibleIndex(survey, emptyMap()),
    )
        private set

    var isComplete by mutableStateOf(false)
        private set

    val currentStep: SurveyStepModel?
        get() = survey.steps.getOrNull(currentIndex)

    val canGoBack: Boolean
        get() = backStack.isNotEmpty()

    val progress: Float
        get() {
            if (survey.steps.isEmpty()) return 0f
            return ((backStack.size + 1).toFloat() / survey.steps.size).coerceIn(0f, 1f)
        }

    /** Whether the current step may be left — required questions must be answered. */
    fun canAdvance(): Boolean {
        val step = currentStep ?: return false
        if (step.type == SurveyQuestionType.THANK_YOU) return true
        if (!step.isRequired) return true
        return answers[step.id]?.isAnswered == true
    }

    fun setAnswer(stepId: String, answer: SurveyAnswer) {
        answers[stepId] = answer
    }

    /** Records the current answer and moves to the branching-decided next step. */
    fun advance() {
        val from = currentIndex
        if (from == SurveyLogicHandler.FINISHED || isComplete) return
        val next = SurveyLogicHandler.nextStepIndex(survey, from, answers)
        backStack.addLast(from)
        if (next == SurveyLogicHandler.FINISHED) {
            isComplete = true
        } else {
            currentIndex = next
        }
    }

    fun back() {
        val prev = backStack.removeLastOrNull() ?: return
        currentIndex = prev
        isComplete = false
    }

    /** The collected answers as a serialisable map, for the `completed` event. */
    fun responsePayload(): Map<String, Any?> =
        answers.mapValues { (_, answer) -> answer.toMap() }

    class Factory(private val survey: SurveyConfigModel) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SurveyViewModel(survey) as T
    }
}
