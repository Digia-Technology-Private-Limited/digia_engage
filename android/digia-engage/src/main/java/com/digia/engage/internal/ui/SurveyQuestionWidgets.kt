package com.digia.engage.internal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digia.engage.internal.SurveyAnswer
import com.digia.engage.internal.model.SurveyInputType
import com.digia.engage.internal.model.SurveyQuestionType
import com.digia.engage.internal.model.SurveyRatingStyle
import com.digia.engage.internal.model.SurveyStepModel

/** Synthetic option id for a choice question's "Other" entry. */
internal const val OTHER_CHOICE_ID = "__other__"

private val emojiPalette5 = listOf("😣", "🙁", "😐", "🙂", "😍")

/** Renders the input UI for a single [step] and reports edits via [onAnswer]. */
@Composable
internal fun SurveyQuestionContent(
    step: SurveyStepModel,
    answer: SurveyAnswer?,
    accent: Color,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    when (step.type) {
        SurveyQuestionType.NPS -> ScaleQuestion(
            min = 0,
            max = 10,
            style = SurveyRatingStyle.NUMBERS,
            lowLabel = step.metadata.lowLabel,
            highLabel = step.metadata.highLabel,
            answer = answer,
            accent = accent,
            onAnswer = onAnswer,
        )
        SurveyQuestionType.RATING -> ScaleQuestion(
            min = 1,
            max = step.metadata.range,
            style = step.metadata.ratingStyle,
            lowLabel = step.metadata.lowLabel,
            highLabel = step.metadata.highLabel,
            answer = answer,
            accent = accent,
            onAnswer = onAnswer,
        )
        SurveyQuestionType.CSAT -> ScaleQuestion(
            min = 1,
            max = step.metadata.range,
            style = if (step.metadata.range in 2..5) SurveyRatingStyle.EMOJIS
            else SurveyRatingStyle.NUMBERS,
            lowLabel = step.metadata.lowLabel,
            highLabel = step.metadata.highLabel,
            answer = answer,
            accent = accent,
            onAnswer = onAnswer,
        )
        SurveyQuestionType.SINGLE_CHOICE -> ChoiceQuestion(
            step = step, multiSelect = false, answer = answer, accent = accent, onAnswer = onAnswer,
        )
        SurveyQuestionType.MULTIPLE_CHOICE -> ChoiceQuestion(
            step = step, multiSelect = true, answer = answer, accent = accent, onAnswer = onAnswer,
        )
        SurveyQuestionType.OPEN_TEXT -> TextQuestion(
            step = step, singleLine = false, answer = answer, onAnswer = onAnswer,
        )
        SurveyQuestionType.SINGLE_INPUT -> TextQuestion(
            step = step, singleLine = true, answer = answer, onAnswer = onAnswer,
        )
        SurveyQuestionType.MATRIX -> MatrixQuestion(
            step = step, answer = answer, accent = accent, onAnswer = onAnswer,
        )
        SurveyQuestionType.THANK_YOU -> Unit
    }
}

// ── scale: NPS / rating / CSAT ──────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleQuestion(
    min: Int,
    max: Int,
    style: SurveyRatingStyle,
    lowLabel: String?,
    highLabel: String?,
    answer: SurveyAnswer?,
    accent: Color,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    val selected = answer?.values?.firstOrNull()?.toIntOrNull()
    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (value in min..max) {
                val isFill = style == SurveyRatingStyle.STARS || style == SurveyRatingStyle.HEARTS
                ScalePoint(
                    value = value,
                    style = style,
                    isSelected = selected != null &&
                        (if (isFill) value <= selected else value == selected),
                    accent = accent,
                    onClick = { onAnswer(SurveyAnswer(values = listOf(value.toString()))) },
                )
            }
        }
        if (!lowLabel.isNullOrBlank() || !highLabel.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = lowLabel.orEmpty(),
                    fontSize = 12.sp,
                    color = Color(0xFF8A8A8A),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = highLabel.orEmpty(),
                    fontSize = 12.sp,
                    color = Color(0xFF8A8A8A),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScalePoint(
    value: Int,
    style: SurveyRatingStyle,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    when (style) {
        SurveyRatingStyle.STARS, SurveyRatingStyle.HEARTS -> {
            val filled = if (style == SurveyRatingStyle.STARS) Icons.Filled.Star
            else Icons.Filled.Favorite
            val empty = if (style == SurveyRatingStyle.STARS) Icons.Filled.StarBorder
            else Icons.Filled.FavoriteBorder
            Icon(
                imageVector = if (isSelected) filled else empty,
                contentDescription = "$value",
                tint = if (isSelected) accent else Color(0xFFBDBDBD),
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onClick),
            )
        }
        SurveyRatingStyle.EMOJIS -> {
            val emoji = emojiPalette5.getOrElse(
                ((value - 1).coerceAtLeast(0) * (emojiPalette5.size - 1) / 4).coerceIn(0, 4),
            ) { "🙂" }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) accent else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 26.sp)
            }
        }
        SurveyRatingStyle.NUMBERS -> {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isSelected) accent else Color(0xFFF1F1F1),
                        shape = CircleShape,
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString(),
                    color = if (isSelected) Color.White else Color(0xFF3A3A3A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

// ── single / multiple choice ────────────────────────────────────────────────

@Composable
private fun ChoiceQuestion(
    step: SurveyStepModel,
    multiSelect: Boolean,
    answer: SurveyAnswer?,
    accent: Color,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    val selected = answer?.values?.toSet() ?: emptySet()
    var otherText by remember(step.id) { mutableStateOf(answer?.comment.orEmpty()) }
    val otherSelected = OTHER_CHOICE_ID in selected

    fun emit(newValues: Set<String>, comment: String?) {
        onAnswer(SurveyAnswer(values = newValues.toList(), comment = comment))
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (choice in step.metadata.choices) {
            ChoiceRow(
                label = choice.label,
                checked = choice.id in selected,
                multiSelect = multiSelect,
                accent = accent,
            ) {
                val next = when {
                    multiSelect && choice.id in selected -> selected - choice.id
                    multiSelect -> selected + choice.id
                    else -> setOf(choice.id)
                }
                emit(next, otherText.takeIf { OTHER_CHOICE_ID in next })
            }
        }
        if (step.metadata.otherChoiceEnabled) {
            ChoiceRow(
                label = "Other",
                checked = otherSelected,
                multiSelect = multiSelect,
                accent = accent,
            ) {
                val next = when {
                    multiSelect && otherSelected -> selected - OTHER_CHOICE_ID
                    multiSelect -> selected + OTHER_CHOICE_ID
                    else -> setOf(OTHER_CHOICE_ID)
                }
                emit(next, otherText.takeIf { OTHER_CHOICE_ID in next })
            }
            if (otherSelected) {
                OutlinedTextField(
                    value = otherText,
                    onValueChange = {
                        otherText = it
                        emit(selected, it)
                    },
                    placeholder = {
                        Text(step.metadata.otherChoicePlaceholder ?: "Tell us more")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    checked: Boolean,
    multiSelect: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSelect) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        } else {
            RadioButton(selected = checked, onClick = onToggle)
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (checked) accent else Color(0xFF2A2A2A),
        )
    }
}

// ── open text / single input ────────────────────────────────────────────────

@Composable
private fun TextQuestion(
    step: SurveyStepModel,
    singleLine: Boolean,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    var text by remember(step.id) { mutableStateOf(answer?.values?.firstOrNull().orEmpty()) }
    val keyboard = when (step.metadata.inputType) {
        SurveyInputType.EMAIL -> KeyboardType.Email
        SurveyInputType.PHONE -> KeyboardType.Phone
        SurveyInputType.NUMBER -> KeyboardType.Number
        SurveyInputType.DATE -> KeyboardType.Number
        SurveyInputType.TEXT -> KeyboardType.Text
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            val limit = step.metadata.maxLength
            val clipped = if (limit != null && it.length > limit) it.take(limit) else it
            text = clipped
            onAnswer(SurveyAnswer(values = listOf(clipped)))
        },
        placeholder = { Text(step.metadata.placeholder ?: "Type your answer") },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── matrix ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatrixQuestion(
    step: SurveyStepModel,
    answer: SurveyAnswer?,
    accent: Color,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    // Cells are encoded "rowId:choiceId" so the answer stays a flat value list.
    val cells = answer?.values?.mapNotNull {
        val parts = it.split(":", limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }?.toMap() ?: emptyMap()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in step.metadata.matrixRows) {
            Text(
                row.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2A2A2A),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in step.metadata.choices) {
                    val isSelected = cells[row.id] == col.id
                    val select = {
                        val next = (cells + (row.id to col.id)).map { (r, c) -> "$r:$c" }
                        onAnswer(SurveyAnswer(values = next))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = select)
                            .padding(end = 8.dp),
                    ) {
                        RadioButton(selected = isSelected, onClick = select)
                        Text(
                            col.label,
                            fontSize = 13.sp,
                            color = if (isSelected) accent else Color(0xFF555555),
                        )
                    }
                }
            }
        }
    }
}
