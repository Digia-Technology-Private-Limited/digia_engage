package com.digia.engage.internal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.digia.engage.internal.SURVEY_FINISHED
import com.digia.engage.internal.SurveyAnswer
import com.digia.engage.internal.SurveyLogicHandler
import com.digia.engage.internal.model.SurveyBlock
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyNode

/**
 * Holds the in-progress state of one survey showing: the answers collected so
 * far, the position in the (possibly branching) node graph, and the back-stack
 * for the Back button.
 *
 * Lives in a [ViewModel] so a configuration change does not lose answers.
 */
internal class SurveyViewModel(val survey: SurveyConfigModel) : ViewModel() {

    /** nodeId → answer. Snapshot-backed so widgets recompose on edit. */
    val answers = mutableStateMapOf<String, SurveyAnswer>()

    private val backStack = ArrayDeque<String>()

    var currentNodeId by mutableStateOf(SurveyLogicHandler.firstNodeId(survey, emptyMap()))
        private set

    var isComplete by mutableStateOf(currentNodeId == SURVEY_FINISHED)
        private set

    var redirectUrl by mutableStateOf<String?>(null)
        private set

    val currentNode: SurveyNode?
        get() = survey.nodeById(currentNodeId)

    val currentBlock: SurveyBlock?
        get() = currentNode?.let { survey.blockFor(it) }

    val canGoBack: Boolean
        get() = backStack.isNotEmpty() && survey.settings.pagination.backButton

    /** Coarse progress estimate based on traversal depth, not graph topology. */
    val progress: Float
        get() {
            if (survey.nodes.isEmpty()) return 0f
            return ((backStack.size + 1).toFloat() / survey.nodes.size).coerceIn(0f, 1f)
        }

    /** Whether the current node may be left — required questions must be answered. */
    fun canAdvance(): Boolean {
        val block = currentBlock ?: return false
        if (block.type.isContent) return true
        if (!block.required) return true
        return answers[currentNodeId]?.isAnswered == true
    }

    fun setAnswer(nodeId: String, answer: SurveyAnswer) {
        answers[nodeId] = answer
    }

    /** Records the current answer and moves to the branching-decided next node. */
    fun advance() {
        if (isComplete) return
        val from = currentNodeId
        if (from == SURVEY_FINISHED) return
        val navigation = SurveyLogicHandler.nextStep(survey, from, answers)
        backStack.addLast(from)
        redirectUrl = navigation.redirectUrl
        if (navigation.nextNodeId == SURVEY_FINISHED) {
            isComplete = true
        } else {
            currentNodeId = navigation.nextNodeId
        }
    }

    fun back() {
        val prev = backStack.removeLastOrNull() ?: return
        currentNodeId = prev
        isComplete = false
    }

    /** The collected answers as a serialisable map, for the `Completed` event. */
    fun responsePayload(): Map<String, Any?> =
        answers.mapValues { (_, answer) -> answer.toMap() }

    class Factory(private val survey: SurveyConfigModel) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SurveyViewModel(survey) as T
    }
}
