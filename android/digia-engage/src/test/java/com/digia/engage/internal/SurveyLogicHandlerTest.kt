package com.digia.engage.internal

import com.digia.engage.internal.model.SurveyAction
import com.digia.engage.internal.model.SurveyBoolOp
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyDisplayLogic
import com.digia.engage.internal.model.SurveyLogic
import com.digia.engage.internal.model.SurveyOperator
import com.digia.engage.internal.model.SurveyQuestionType
import com.digia.engage.internal.model.SurveyStepModel
import com.digia.engage.internal.model.SurveySurface
import com.digia.engage.internal.model.SurveyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyLogicHandlerTest {

    private fun step(
        id: String,
        type: SurveyQuestionType = SurveyQuestionType.SINGLE_CHOICE,
        logic: List<SurveyLogic> = emptyList(),
        displayLogic: List<SurveyDisplayLogic> = emptyList(),
        displayLogicOperator: SurveyBoolOp = SurveyBoolOp.AND,
    ) = SurveyStepModel(
        id = id,
        sequenceOrder = 0,
        type = type,
        logic = logic,
        displayLogic = displayLogic,
        displayLogicOperator = displayLogicOperator,
    )

    private fun survey(vararg steps: SurveyStepModel) = SurveyConfigModel(
        id = "s",
        surface = SurveySurface.BOTTOM_SHEET,
        steps = steps.toList(),
        title = null,
        showProgress = true,
        dismissible = true,
        theme = SurveyTheme(accentColor = 0, backgroundColor = 0),
        timeDelayMs = 0,
    )

    @Test
    fun `no logic advances linearly`() {
        val s = survey(step("q1"), step("q2"), step("q3"))
        assertEquals(1, SurveyLogicHandler.nextStepIndex(s, 0, emptyMap()))
        assertEquals(2, SurveyLogicHandler.nextStepIndex(s, 1, emptyMap()))
    }

    @Test
    fun `last step with no logic finishes`() {
        val s = survey(step("q1"), step("q2"))
        assertEquals(SurveyLogicHandler.FINISHED, SurveyLogicHandler.nextStepIndex(s, 1, emptyMap()))
    }

    @Test
    fun `end_survey action finishes immediately`() {
        val s = survey(
            step(
                "q1",
                logic = listOf(
                    SurveyLogic(
                        operator = SurveyOperator.IS_ANSWERED,
                        action = SurveyAction.END_SURVEY,
                    ),
                ),
            ),
            step("q2"),
        )
        val answers = mapOf("q1" to SurveyAnswer(values = listOf("a")))
        assertEquals(SurveyLogicHandler.FINISHED, SurveyLogicHandler.nextStepIndex(s, 0, answers))
    }

    @Test
    fun `equals rule jumps to target step`() {
        val s = survey(
            step(
                "q1",
                logic = listOf(
                    SurveyLogic(
                        operator = SurveyOperator.EQUALS,
                        values = listOf("yes"),
                        action = SurveyAction.GO_TO_STEP,
                        targetStepId = "q3",
                    ),
                ),
            ),
            step("q2"),
            step("q3"),
        )
        val answers = mapOf("q1" to SurveyAnswer(values = listOf("yes")))
        assertEquals(2, SurveyLogicHandler.nextStepIndex(s, 0, answers))
    }

    @Test
    fun `non-matching rule falls through to next step`() {
        val s = survey(
            step(
                "q1",
                logic = listOf(
                    SurveyLogic(
                        operator = SurveyOperator.EQUALS,
                        values = listOf("yes"),
                        action = SurveyAction.GO_TO_STEP,
                        targetStepId = "q3",
                    ),
                ),
            ),
            step("q2"),
            step("q3"),
        )
        val answers = mapOf("q1" to SurveyAnswer(values = listOf("no")))
        assertEquals(1, SurveyLogicHandler.nextStepIndex(s, 0, answers))
    }

    @Test
    fun `first matching rule wins`() {
        val s = survey(
            step(
                "q1",
                logic = listOf(
                    SurveyLogic(SurveyOperator.IS_ANSWERED, action = SurveyAction.END_SURVEY),
                    SurveyLogic(
                        SurveyOperator.IS_ANSWERED,
                        action = SurveyAction.GO_TO_STEP,
                        targetStepId = "q2",
                    ),
                ),
            ),
            step("q2"),
        )
        val answers = mapOf("q1" to SurveyAnswer(values = listOf("a")))
        assertEquals(SurveyLogicHandler.FINISHED, SurveyLogicHandler.nextStepIndex(s, 0, answers))
    }

    @Test
    fun `hidden step is skipped by display logic`() {
        val s = survey(
            step("q1"),
            step(
                "q2",
                displayLogic = listOf(
                    SurveyDisplayLogic(
                        dependsOnStepId = "q1",
                        operator = SurveyOperator.EQUALS,
                        values = listOf("show"),
                    ),
                ),
            ),
            step("q3"),
        )
        val answers = mapOf("q1" to SurveyAnswer(values = listOf("hide")))
        assertEquals(2, SurveyLogicHandler.nextStepIndex(s, 0, answers))
    }

    @Test
    fun `visible step is shown when display logic passes`() {
        val q2 = step(
            "q2",
            displayLogic = listOf(
                SurveyDisplayLogic("q1", SurveyOperator.EQUALS, values = listOf("show")),
            ),
        )
        assertTrue(SurveyLogicHandler.isVisible(q2, mapOf("q1" to SurveyAnswer(listOf("show")))))
        assertFalse(SurveyLogicHandler.isVisible(q2, mapOf("q1" to SurveyAnswer(listOf("no")))))
    }

    @Test
    fun `greater_than compares numeric answers`() {
        val s = survey(
            step(
                "q1",
                type = SurveyQuestionType.NPS,
                logic = listOf(
                    SurveyLogic(
                        operator = SurveyOperator.GREATER_THAN,
                        values = listOf("8"),
                        action = SurveyAction.END_SURVEY,
                    ),
                ),
            ),
            step("q2"),
        )
        assertEquals(
            SurveyLogicHandler.FINISHED,
            SurveyLogicHandler.nextStepIndex(s, 0, mapOf("q1" to SurveyAnswer(listOf("9")))),
        )
        assertEquals(
            1,
            SurveyLogicHandler.nextStepIndex(s, 0, mapOf("q1" to SurveyAnswer(listOf("7")))),
        )
    }

    @Test
    fun `includes_any matches multi-select answers`() {
        val s = survey(
            step(
                "q1",
                type = SurveyQuestionType.MULTIPLE_CHOICE,
                logic = listOf(
                    SurveyLogic(
                        operator = SurveyOperator.INCLUDES_ANY,
                        values = listOf("c2", "c4"),
                        action = SurveyAction.GO_TO_STEP,
                        targetStepId = "q3",
                    ),
                ),
            ),
            step("q2"),
            step("q3"),
        )
        val answers = mapOf("q1" to SurveyAnswer(values = listOf("c1", "c2")))
        assertEquals(2, SurveyLogicHandler.nextStepIndex(s, 0, answers))
    }

    @Test
    fun `is_not_answered is true for an unanswered step`() {
        val s = survey(
            step(
                "q1",
                logic = listOf(
                    SurveyLogic(
                        operator = SurveyOperator.IS_NOT_ANSWERED,
                        action = SurveyAction.END_SURVEY,
                    ),
                ),
            ),
            step("q2"),
        )
        assertEquals(SurveyLogicHandler.FINISHED, SurveyLogicHandler.nextStepIndex(s, 0, emptyMap()))
    }

    @Test
    fun `firstVisibleIndex skips a leading hidden step`() {
        val s = survey(
            step(
                "q1",
                displayLogic = listOf(SurveyDisplayLogic("qX", SurveyOperator.IS_ANSWERED)),
            ),
            step("q2"),
        )
        assertEquals(1, SurveyLogicHandler.firstVisibleIndex(s, emptyMap()))
    }
}
