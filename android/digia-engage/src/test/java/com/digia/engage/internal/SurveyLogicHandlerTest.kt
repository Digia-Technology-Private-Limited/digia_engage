package com.digia.engage.internal

import com.digia.engage.internal.model.AnswerLayout
import com.digia.engage.internal.model.BoolOp
import com.digia.engage.internal.model.BranchRule
import com.digia.engage.internal.model.BranchTarget
import com.digia.engage.internal.model.BranchTargetKind
import com.digia.engage.internal.model.BranchingType
import com.digia.engage.internal.model.BlockMedia
import com.digia.engage.internal.model.Condition
import com.digia.engage.internal.model.ConditionExpr
import com.digia.engage.internal.model.ConditionGroup
import com.digia.engage.internal.model.ConditionOperator
import com.digia.engage.internal.model.NodeBranching
import com.digia.engage.internal.model.RichText
import com.digia.engage.internal.model.SurveyBlock
import com.digia.engage.internal.model.SurveyBlockType
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyNode
import com.digia.engage.internal.model.SurveySettings
import com.digia.engage.internal.model.SurveyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the node-graph branching runtime ([SurveyLogicHandler]) against the
 * current block/node model. Node id == block id throughout for readability.
 */
class SurveyLogicHandlerTest {

    private fun block(
        id: String,
        type: SurveyBlockType = SurveyBlockType.SINGLE_SELECT,
        showWhen: ConditionExpr? = null,
        hidden: Boolean = false,
    ) = SurveyBlock(
        id = id,
        type = type,
        title = RichText(""),
        body = null,
        options = emptyList(),
        optionStyle = null,
        npsStyle = null,
        required = false,
        hidden = hidden,
        showMedia = false,
        media = BlockMedia.EMPTY,
        showTag = true,
        showAnswerMedia = false,
        showAnswerDescriptions = false,
        shuffle = false,
        allowOther = false,
        flexibleHeight = false,
        answerLayout = AnswerLayout.COLUMN,
        backgroundColorHex = "",
        numberMin = null,
        numberMax = null,
        showWhen = showWhen,
    )

    private fun linearNode(id: String) = SurveyNode(id, id, NodeBranching.LINEAR_NEXT)

    private fun conditionalNode(
        id: String,
        rules: List<BranchRule>,
        default: BranchTarget = BranchTarget.NEXT,
    ) = SurveyNode(id, id, NodeBranching(BranchingType.BY_CONDITION, rules, null, default))

    private fun cond(
        operator: ConditionOperator,
        values: List<String> = emptyList(),
        nodeId: String? = null,
    ) = Condition(nodeId, operator, values)

    private fun whenAny(vararg conditions: Condition) =
        ConditionExpr(BoolOp.OR, listOf(ConditionGroup(BoolOp.AND, conditions.toList())))

    private fun rule(id: String, whenExpr: ConditionExpr, target: BranchTarget) =
        BranchRule(id, whenExpr, target)

    private fun nodeTarget(id: String) = BranchTarget(BranchTargetKind.NODE, id, "")

    private fun survey(
        nodes: List<SurveyNode>,
        blocks: List<SurveyBlock> = nodes.map { block(it.blockId) },
        rootNodeId: String = nodes.first().id,
    ) = SurveyConfigModel(
        id = "s",
        name = null,
        blocks = blocks,
        nodes = nodes,
        rootNodeId = rootNodeId,
        settings = SurveySettings.DEFAULT,
        // Construct directly (not SurveyTheme.DEFAULT) — the companion default
        // calls android.graphics.Color.parseColor, unavailable in JVM unit tests.
        theme = SurveyTheme(accentColor = 0, backgroundColor = 0),
        uiTemplateId = null,
        timeDelayMs = 0,
    )

    private fun answer(vararg values: String) = SurveyAnswer(values = values.toList())

    @Test
    fun `linear flow advances node by node`() {
        val s = survey(listOf(linearNode("q1"), linearNode("q2"), linearNode("q3")))
        assertEquals("q1", SurveyLogicHandler.firstNodeId(s, emptyMap()))
        assertEquals("q2", SurveyLogicHandler.nextStep(s, "q1", emptyMap()).nextNodeId)
        assertEquals("q3", SurveyLogicHandler.nextStep(s, "q2", emptyMap()).nextNodeId)
    }

    @Test
    fun `last node with linear next finishes`() {
        val s = survey(listOf(linearNode("q1"), linearNode("q2")))
        assertEquals(SURVEY_FINISHED, SurveyLogicHandler.nextStep(s, "q2", emptyMap()).nextNodeId)
    }

    @Test
    fun `end target finishes immediately`() {
        val s = survey(
            listOf(
                conditionalNode(
                    "q1",
                    rules = listOf(rule("r1", whenAny(cond(ConditionOperator.IS_ANSWERED)), BranchTarget.END)),
                ),
                linearNode("q2"),
            ),
        )
        assertEquals(
            SURVEY_FINISHED,
            SurveyLogicHandler.nextStep(s, "q1", mapOf("q1" to answer("a"))).nextNodeId,
        )
    }

    @Test
    fun `equals rule jumps to target node`() {
        val s = survey(
            listOf(
                conditionalNode(
                    "q1",
                    rules = listOf(
                        rule("r1", whenAny(cond(ConditionOperator.EQUALS, listOf("yes"))), nodeTarget("q3")),
                    ),
                ),
                linearNode("q2"),
                linearNode("q3"),
            ),
        )
        assertEquals("q3", SurveyLogicHandler.nextStep(s, "q1", mapOf("q1" to answer("yes"))).nextNodeId)
    }

    @Test
    fun `non-matching rule falls through to next node`() {
        val s = survey(
            listOf(
                conditionalNode(
                    "q1",
                    rules = listOf(
                        rule("r1", whenAny(cond(ConditionOperator.EQUALS, listOf("yes"))), nodeTarget("q3")),
                    ),
                ),
                linearNode("q2"),
                linearNode("q3"),
            ),
        )
        assertEquals("q2", SurveyLogicHandler.nextStep(s, "q1", mapOf("q1" to answer("no"))).nextNodeId)
    }

    @Test
    fun `first matching rule wins`() {
        val s = survey(
            listOf(
                conditionalNode(
                    "q1",
                    rules = listOf(
                        rule("r1", whenAny(cond(ConditionOperator.IS_ANSWERED)), BranchTarget.END),
                        rule("r2", whenAny(cond(ConditionOperator.IS_ANSWERED)), nodeTarget("q2")),
                    ),
                ),
                linearNode("q2"),
            ),
        )
        assertEquals(
            SURVEY_FINISHED,
            SurveyLogicHandler.nextStep(s, "q1", mapOf("q1" to answer("a"))).nextNodeId,
        )
    }

    @Test
    fun `showWhen gates block visibility`() {
        val q2 = block(
            "q2",
            showWhen = whenAny(cond(ConditionOperator.EQUALS, listOf("show"), nodeId = "q1")),
        )
        assertTrue(SurveyLogicHandler.isVisible(q2, "q2", mapOf("q1" to answer("show"))))
        assertFalse(SurveyLogicHandler.isVisible(q2, "q2", mapOf("q1" to answer("no"))))
    }

    @Test
    fun `greater_than compares numeric answers`() {
        val s = survey(
            listOf(
                conditionalNode(
                    "q1",
                    rules = listOf(
                        rule("r1", whenAny(cond(ConditionOperator.GREATER_THAN, listOf("8"))), BranchTarget.END),
                    ),
                ),
                linearNode("q2"),
            ),
            blocks = listOf(block("q1", SurveyBlockType.NPS), block("q2")),
        )
        assertEquals(
            SURVEY_FINISHED,
            SurveyLogicHandler.nextStep(s, "q1", mapOf("q1" to answer("9"))).nextNodeId,
        )
        assertEquals("q2", SurveyLogicHandler.nextStep(s, "q1", mapOf("q1" to answer("7"))).nextNodeId)
    }

    @Test
    fun `includes_any matches multi-select answers`() {
        val s = survey(
            listOf(
                conditionalNode(
                    "q1",
                    rules = listOf(
                        rule(
                            "r1",
                            whenAny(cond(ConditionOperator.INCLUDES_ANY, listOf("c2", "c4"))),
                            nodeTarget("q3"),
                        ),
                    ),
                ),
                linearNode("q2"),
                linearNode("q3"),
            ),
            blocks = listOf(block("q1", SurveyBlockType.MULTI_SELECT), block("q2"), block("q3")),
        )
        assertEquals("q3", SurveyLogicHandler.nextStep(s, "q1", mapOf("q1" to answer("c1", "c2"))).nextNodeId)
    }

    @Test
    fun `is_not_answered is true for an unanswered node`() {
        val s = survey(
            listOf(
                conditionalNode(
                    "q1",
                    rules = listOf(
                        rule("r1", whenAny(cond(ConditionOperator.IS_NOT_ANSWERED)), BranchTarget.END),
                    ),
                ),
                linearNode("q2"),
            ),
        )
        assertEquals(SURVEY_FINISHED, SurveyLogicHandler.nextStep(s, "q1", emptyMap()).nextNodeId)
    }

    @Test
    fun `firstNodeId skips a leading showWhen-hidden node`() {
        val s = survey(
            listOf(linearNode("q1"), linearNode("q2")),
            blocks = listOf(
                block("q1", showWhen = whenAny(cond(ConditionOperator.IS_ANSWERED, nodeId = "qX"))),
                block("q2"),
            ),
        )
        assertEquals("q2", SurveyLogicHandler.firstNodeId(s, emptyMap()))
    }

    @Test
    fun `hidden flag does not alter node traversal`() {
        val s = survey(
            listOf(linearNode("q1"), linearNode("q2")),
            blocks = listOf(block("q1", hidden = true), block("q2")),
        )
        assertEquals("q1", SurveyLogicHandler.firstNodeId(s, emptyMap()))
        assertTrue(SurveyLogicHandler.isVisible(s.blocks.first(), "q1", emptyMap()))
    }
}
