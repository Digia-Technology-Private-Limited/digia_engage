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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digia.engage.internal.DigiaFontConfig
import com.digia.engage.internal.SurveyAnswer
import com.digia.engage.internal.model.AnswerLayout
import com.digia.engage.internal.model.ElementStyle
import com.digia.engage.internal.model.FontWeight
import com.digia.engage.internal.model.NpsStyle
import com.digia.engage.internal.model.SurveyBlock
import com.digia.engage.internal.model.SurveyBlockType
import com.digia.engage.internal.model.SurveyOption
import com.digia.engage.internal.model.TextAlign

/** Synthetic option id for a choice question's "Other" entry. */
internal const val OTHER_CHOICE_ID = "__other__"

// ── visual tokens (mirror dashboard tokens) ─────────────────────────────────

internal object SurveyTokens {
    val Border = Color(0xFFE4E6EB)
    val BorderStrong = Color(0xFFCDD2DA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSunken = Color(0xFFF4F5F8)
    val TextPrimary = Color(0xFF1A1D24)
    val TextSecondary = Color(0xFF55606E)
    val TextTertiary = Color(0xFF8A93A1)
}

// ── shared text style helpers ──────────────────────────────────────────────

internal data class TextDefaults(
    val sizeSp: Int,
    val weight: ComposeFontWeight,
    val color: Color,
    val align: ComposeTextAlign = ComposeTextAlign.Start,
)

internal val TitleDefaults = TextDefaults(
    sizeSp = 20, weight = ComposeFontWeight.Bold, color = SurveyTokens.TextPrimary,
)
internal val BodyDefaults = TextDefaults(
    sizeSp = 14, weight = ComposeFontWeight.Normal, color = SurveyTokens.TextSecondary,
)
internal val OptionDefaults = TextDefaults(
    sizeSp = 16, weight = ComposeFontWeight.Medium, color = SurveyTokens.TextPrimary,
)

internal fun ElementStyle.toTextStyle(accent: Color, default: TextDefaults): TextStyle {
    val parsedColor = colorHex.takeIf { it.isNotBlank() }?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    }
    return TextStyle(
        fontSize = if (sizePx > 0f) sizePx.sp else default.sizeSp.sp,
        fontWeight = when (weight) {
            FontWeight.REGULAR -> ComposeFontWeight.Normal
            FontWeight.MEDIUM -> ComposeFontWeight.Medium
            FontWeight.SEMIBOLD -> ComposeFontWeight.SemiBold
            FontWeight.BOLD -> ComposeFontWeight.Bold
        },
        color = parsedColor ?: default.color,
        fontFamily = DigiaFontConfig.composeFontFamily(),
        textAlign = when (align) {
            TextAlign.LEFT -> ComposeTextAlign.Start
            TextAlign.CENTER -> ComposeTextAlign.Center
            TextAlign.RIGHT -> ComposeTextAlign.End
        },
    )
}

internal fun TextDefaults.toStyle(): TextStyle =
    TextStyle(
        fontSize = sizeSp.sp,
        fontWeight = weight,
        color = color,
        fontFamily = DigiaFontConfig.composeFontFamily(),
        textAlign = align,
    )

// ── dispatch ───────────────────────────────────────────────────────────────

@Composable
internal fun SurveyQuestionContent(
    block: SurveyBlock,
    answer: SurveyAnswer?,
    accent: Color,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    when (block.type) {
        SurveyBlockType.RATING -> StarRatingQuestion(
            range = 5, accent = accent, answer = answer, onAnswer = onAnswer,
        )
        SurveyBlockType.NPS -> NpsQuestion(
            accent = accent, style = block.npsStyle, answer = answer, onAnswer = onAnswer,
        )
        SurveyBlockType.NPS_EMOJI -> NpsFaceQuestion(
            accent = accent, style = block.npsStyle, faceSize = 28, answer = answer, onAnswer = onAnswer,
        )
        SurveyBlockType.NPS_SMILEY -> NpsFaceQuestion(
            accent = accent, style = block.npsStyle, faceSize = 30, answer = answer, onAnswer = onAnswer,
        )
        SurveyBlockType.REACTION -> ReactionQuestion(
            block = block, accent = accent, answer = answer, onAnswer = onAnswer,
        )
        SurveyBlockType.THIS_OR_THAT -> ThisOrThatQuestion(
            block = block, accent = accent, answer = answer, onAnswer = onAnswer,
        )
        SurveyBlockType.TIER_LIST -> TierListQuestion(
            block = block, accent = accent, answer = answer, onAnswer = onAnswer,
        )
        SurveyBlockType.SINGLE_SELECT,
        SurveyBlockType.MULTI_SELECT,
        SurveyBlockType.UPVOTE -> ChoiceCardQuestion(
            block = block, accent = accent, answer = answer, onAnswer = onAnswer,
        )
        SurveyBlockType.SHORT_TEXT -> TextQuestion(
            accent = accent, answer = answer, onAnswer = onAnswer,
            keyboard = KeyboardType.Text, singleLine = true, placeholder = "Type your answer…",
        )
        SurveyBlockType.LONG_TEXT -> TextQuestion(
            accent = accent, answer = answer, onAnswer = onAnswer,
            keyboard = KeyboardType.Text, singleLine = false, placeholder = "Type your answer…",
            minHeightDp = 100,
        )
        SurveyBlockType.NUMBER -> TextQuestion(
            accent = accent, answer = answer, onAnswer = onAnswer,
            keyboard = KeyboardType.Number, singleLine = true, placeholder = "0", maxWidthDp = 200,
            validator = { input -> validateNumber(input, block.numberMin, block.numberMax) },
        )
        SurveyBlockType.EMAIL -> TextQuestion(
            accent = accent, answer = answer, onAnswer = onAnswer,
            keyboard = KeyboardType.Email, singleLine = true, placeholder = "you@example.com",
            validator = ::validateEmail,
        )
        SurveyBlockType.DATE -> TextQuestion(
            accent = accent, answer = answer, onAnswer = onAnswer,
            keyboard = KeyboardType.Text, singleLine = true, placeholder = "YYYY-MM-DD",
            maxWidthDp = 240,
            validator = ::validateDate,
        )
        SurveyBlockType.WELCOME,
        SurveyBlockType.TEXT_MEDIA,
        SurveyBlockType.RESULT_PAGE -> Unit
    }
}

// ── star rating: 5 rounded tiles, fill-up style ────────────────────────────

@Composable
private fun StarRatingQuestion(
    range: Int,
    accent: Color,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    val selected = answer?.values?.firstOrNull()?.toIntOrNull() ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 1..range) {
            val isOn = i <= selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isOn) accent.copy(alpha = 0.12f) else SurveyTokens.SurfaceSunken)
                    .clickable { onAnswer(SurveyAnswer(values = listOf(i.toString()))) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "$i",
                    tint = if (isOn) accent else SurveyTokens.TextTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ── NPS: 11 square tiles, single-select ────────────────────────────────────

/** Sentiment band colour for an NPS score (detractors ≤6, passives 7–8, promoters ≥9). */
private fun npsBandColor(style: NpsStyle, score: Int): Color {
    val hex = when {
        score <= 6 -> style.scaleColors.detractors
        score <= 8 -> style.scaleColors.passives
        else -> style.scaleColors.promoters
    }
    return hex.toComposeColorOrNull() ?: Color.Gray
}

private fun String.toComposeColorOrNull(): Color? {
    if (isBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Composable
private fun NpsQuestion(
    accent: Color,
    style: NpsStyle?,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    val nps = style ?: defaultNps()
    val selected = answer?.values?.firstOrNull()?.toIntOrNull()
    val sel = nps.selectedTile
    val baseRadius = (if (nps.isCircle) 999f else nps.borderRadius).dp
    val selRadius = (if (sel.isCircle) 999f else sel.borderRadius).dp
    val baseBg = nps.backgroundColorHex.toComposeColorOrNull() ?: Color.Transparent
    val baseBorder = nps.borderColorHex.toComposeColorOrNull() ?: SurveyTokens.Border
    val textColor = nps.textStyle.colorHex.toComposeColorOrNull() ?: SurveyTokens.TextPrimary
    val textWeight = nps.textStyle.toTextStyle(accent, OptionDefaults).fontWeight ?: ComposeFontWeight.SemiBold
    val textSize = if (nps.textStyle.sizePx > 0f) nps.textStyle.sizePx.sp else 13.sp
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0..10) {
                val isOn = selected == i
                val band = npsBandColor(nps, i)
                // Selected tile takes its own style; empty colours fall back to
                // the sentiment band so the default look is preserved.
                val fill = if (isOn) sel.backgroundColorHex.toComposeColorOrNull() ?: band else baseBg
                val borderColor = if (isOn) sel.borderColorHex.toComposeColorOrNull() ?: band else baseBorder
                val borderWidth = (if (isOn) sel.borderWidth else nps.borderWidth).dp
                val radius = if (isOn) selRadius else baseRadius
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(radius))
                        .background(fill)
                        .border(borderWidth, borderColor, RoundedCornerShape(radius))
                        .clickable { onAnswer(SurveyAnswer(values = listOf(i.toString()))) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = i.toString(),
                        color = if (isOn) Color.White else textColor,
                        fontWeight = textWeight,
                        fontSize = textSize,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Not likely", color = SurveyTokens.TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("Extremely likely", color = SurveyTokens.TextTertiary, fontSize = 11.sp)
        }
    }
}

/** Default NPS style when a block carries none (mirrors dashboard `defaultNpsStyle`). */
private fun defaultNps(): NpsStyle = NpsStyle(
    shape = "square", borderRadius = 8f, borderWidth = 1f,
    borderColorHex = "#E4E6EB", backgroundColorHex = "#F4F5F8",
    selectedTile = com.digia.engage.internal.model.NpsTileStyle.DEFAULT,
    textStyle = ElementStyle(sizePx = 13f, weight = FontWeight.SEMIBOLD, align = TextAlign.CENTER, colorHex = "#1A1D24"),
    scaleColors = NpsStyle.DEFAULT_SCALE,
    tierEmojis = NpsStyle.DEFAULT_TIERS,
    selectedBgColorHex = "#FFFFFF",
    faces = emptyList(),
    showFaceLabels = true,
)

// ── NPS face scale: emoji / smiley rounded-square tiles, single-select ──────

@Composable
private fun NpsFaceQuestion(
    accent: Color,
    style: NpsStyle?,
    faceSize: Int,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    val nps = style ?: defaultNps()
    val faces = nps.faces
    val sel = nps.selectedTile
    val baseRadius = (if (nps.isCircle) 999f else nps.borderRadius).dp
    val selRadius = (if (sel.isCircle) 999f else sel.borderRadius).dp
    val baseBg = nps.backgroundColorHex.toComposeColorOrNull() ?: SurveyTokens.SurfaceSunken
    val baseBorder = nps.borderColorHex.toComposeColorOrNull() ?: SurveyTokens.Border
    val labelColor = nps.textStyle.colorHex.toComposeColorOrNull() ?: SurveyTokens.TextPrimary
    val labelWeight = nps.textStyle.toTextStyle(accent, OptionDefaults).fontWeight ?: ComposeFontWeight.SemiBold
    val labelSize = if (nps.textStyle.sizePx > 0f) nps.textStyle.sizePx.sp else 13.sp
    val selectedValue = answer?.values?.firstOrNull()?.toIntOrNull()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        faces.forEachIndexed { index, face ->
            val value = index + 1
            val isOn = selectedValue == value
            // Selected face takes its own style; empty colours fall back to the
            // accent so the default highlight is preserved.
            val fill = if (isOn) sel.backgroundColorHex.toComposeColorOrNull() ?: accent.copy(alpha = 0.12f) else baseBg
            val borderColor = if (isOn) sel.borderColorHex.toComposeColorOrNull() ?: accent else baseBorder
            val borderWidth = (if (isOn) sel.borderWidth else nps.borderWidth).dp
            val radius = if (isOn) selRadius else baseRadius
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(if (isOn) 1.1f else 1f)
                        .clip(RoundedCornerShape(radius))
                        .background(fill)
                        .border(borderWidth, borderColor, RoundedCornerShape(radius))
                        .clickable { onAnswer(SurveyAnswer(values = listOf(value.toString()))) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = face.emoji, fontSize = faceSize.sp, lineHeight = faceSize.sp)
                }
                if (nps.showFaceLabels && face.label.isNotBlank()) {
                    Text(
                        text = face.label,
                        color = if (isOn) accent else labelColor,
                        fontWeight = labelWeight,
                        fontSize = labelSize,
                    )
                }
            }
        }
    }
}

// ── reaction: large emoji circles ──────────────────────────────────────────

@Composable
private fun ReactionQuestion(
    block: SurveyBlock,
    accent: Color,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    val selectedId = answer?.values?.firstOrNull()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        block.options.forEach { option ->
            val isOn = selectedId == option.id
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isOn) accent.copy(alpha = 0.14f) else SurveyTokens.SurfaceSunken)
                    .border(
                        width = if (isOn) 2.dp else 1.5.dp,
                        color = if (isOn) accent else SurveyTokens.Border,
                        shape = CircleShape,
                    )
                    .clickable { onAnswer(SurveyAnswer(values = listOf(option.id))) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                )
            }
        }
    }
}

// ── this-or-that: two gradient cards ───────────────────────────────────────

@Composable
private fun ThisOrThatQuestion(
    block: SurveyBlock,
    accent: Color,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    val gradients = listOf(
        Brush.linearGradient(listOf(Color(0xFFFF9966), Color(0xFFFF5E62))),
        Brush.linearGradient(listOf(Color(0xFF6B6CFF), Color(0xFF4945FF))),
    )
    val options = block.options.take(2)
    val selectedId = answer?.values?.firstOrNull()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isOn = selectedId == option.id
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(gradients[index % gradients.size])
                    .border(
                        width = if (isOn) 3.dp else 0.dp,
                        color = if (isOn) accent else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onAnswer(SurveyAnswer(values = listOf(option.id))) }
                    .padding(14.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = option.label,
                    color = Color.White,
                    fontWeight = ComposeFontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

// ── tier list: S/A/B/C rows + draggable chip rail (tap to cycle tier) ──────

@Composable
private fun TierListQuestion(
    block: SurveyBlock,
    accent: Color,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    // Encoding: answer values are "tier:optionId" pairs; default tier is "-".
    val tiers = listOf("S" to Color(0xFFFF5E62), "A" to Color(0xFFFFA351), "B" to Color(0xFF5BC678), "C" to Color(0xFF5089E0))
    val placements: MutableMap<String, String> = remember(block.id) {
        mutableStateMapOf<String, String>().apply {
            answer?.values?.forEach { pair ->
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2) put(parts[1], parts[0])
            }
        }
    }
    fun emit() {
        val list = placements.entries
            .filter { it.value in tiers.map { t -> t.first } }
            .map { "${it.value}:${it.key}" }
        onAnswer(SurveyAnswer(values = list))
    }
    fun cycle(optionId: String) {
        val ordered = listOf("-") + tiers.map { it.first }
        val current = placements[optionId] ?: "-"
        val next = ordered[(ordered.indexOf(current) + 1) % ordered.size]
        placements[optionId] = next
        emit()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiers.forEach { (label, color) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.White, fontSize = 18.sp, fontWeight = ComposeFontWeight.ExtraBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurveyTokens.SurfaceSunken)
                        .border(1.dp, SurveyTokens.Border, RoundedCornerShape(6.dp))
                        .padding(6.dp),
                ) {
                    TierChipsFor(block.options.filter { placements[it.id] == label }, ::cycle)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, SurveyTokens.Border, RoundedCornerShape(6.dp))
                .padding(10.dp),
        ) {
            TierChipsFor(
                block.options.filter { (placements[it.id] ?: "-") == "-" },
                ::cycle,
                placeholder = "Tap a chip to assign a tier",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierChipsFor(
    items: List<SurveyOption>,
    onTap: (String) -> Unit,
    placeholder: String? = null,
) {
    if (items.isEmpty()) {
        if (placeholder != null) {
            Text(placeholder, color = SurveyTokens.TextTertiary, fontSize = 11.sp)
        }
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { opt ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurveyTokens.Surface)
                    .border(1.dp, SurveyTokens.Border, RoundedCornerShape(4.dp))
                    .clickable { onTap(opt.id) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(opt.label, fontSize = 12.sp, color = SurveyTokens.TextPrimary)
            }
        }
    }
}

// ── single/multi/upvote: marker-on-left card rows with layout support ──────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceCardQuestion(
    block: SurveyBlock,
    accent: Color,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
) {
    val multi = block.type.isMultiSelect
    val selected = remember(block.id) { mutableStateMapOf<String, Boolean>() }
    val otherSelected = selected[OTHER_CHOICE_ID] == true
    var otherText by remember(block.id) { mutableStateOf(answer?.comment.orEmpty()) }

    // Hydrate selection from incoming answer on first composition.
    remember(answer) {
        selected.clear()
        answer?.values?.forEach { selected[it] = true }
        Unit
    }

    fun emit() {
        val ids = selected.entries.filter { it.value }.map { it.key }
        onAnswer(SurveyAnswer(values = ids, comment = if (otherSelected) otherText else null))
    }
    fun toggle(id: String) {
        if (multi) {
            selected[id] = !(selected[id] ?: false)
        } else {
            selected.keys.toList().forEach { selected[it] = false }
            selected[id] = true
        }
        emit()
    }

    val options = block.options +
        if (block.allowOther) listOf(SurveyOption(OTHER_CHOICE_ID, "Other…")) else emptyList()

    val card: @Composable (SurveyOption, Modifier) -> Unit = { option, mod ->
        ChoiceCardRow(
            option = option,
            selected = selected[option.id] == true,
            multi = multi,
            accent = accent,
            optionStyle = block.optionStyle,
            showMedia = block.showAnswerMedia,
            showDescription = block.showAnswerDescriptions,
            wide = true,
            onClick = { toggle(option.id) },
            modifier = mod,
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (block.answerLayout) {
            AnswerLayout.ROW -> FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    card(option, Modifier.weight(1f).widthIn(min = 150.dp))
                }
            }
            AnswerLayout.GRID -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { option ->
                            Box(modifier = Modifier.weight(1f)) {
                                card(option, Modifier.fillMaxWidth())
                            }
                        }
                        // Pad the last row to two columns so a trailing item
                        // keeps its half-width instead of stretching.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            AnswerLayout.COLUMN -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    card(option, Modifier.fillMaxWidth())
                }
            }
        }

        if (block.allowOther && otherSelected) {
            OutlinedTextField(
                value = otherText,
                onValueChange = { otherText = it; emit() },
                placeholder = { Text("Please specify…") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChoiceCardRow(
    option: SurveyOption,
    selected: Boolean,
    multi: Boolean,
    accent: Color,
    optionStyle: ElementStyle?,
    showMedia: Boolean,
    showDescription: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.08f) else SurveyTokens.Surface)
            .border(
                width = 1.5.dp,
                color = if (selected) accent else SurveyTokens.Border,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(if (multi) RoundedCornerShape(4.dp) else CircleShape)
                    .background(if (selected) accent else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (selected) accent else SurveyTokens.BorderStrong,
                        shape = if (multi) RoundedCornerShape(4.dp) else CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            if (showMedia && option.media?.hasUrl == true) {
                coil.compose.AsyncImage(
                    model = option.media.url,
                    contentDescription = option.media.alt,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = option.label,
                style = optionStyle?.toTextStyle(accent, OptionDefaults) ?: OptionDefaults.toStyle(),
                modifier = if (wide) Modifier.weight(1f) else Modifier,
            )
        }
        if (showDescription && !option.description.isNullOrBlank()) {
            Text(
                text = option.description,
                color = SurveyTokens.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 32.dp),
            )
        }
    }
}

// ── text inputs ────────────────────────────────────────────────────────────

/**
 * Validation result for an input. `error == null` means accept the input and
 * forward it as an answer. A non-null `error` blocks the value from being
 * emitted (so the survey can't advance) and surfaces the message under the
 * field. A blank input is always treated as "not answered" — `required` is
 * enforced separately by the view-model.
 */
private data class InputValidation(val error: String?)

@Composable
private fun TextQuestion(
    accent: Color,
    answer: SurveyAnswer?,
    onAnswer: (SurveyAnswer) -> Unit,
    keyboard: KeyboardType,
    singleLine: Boolean,
    placeholder: String? = null,
    minHeightDp: Int = 0,
    maxWidthDp: Int = 0,
    validator: ((String) -> InputValidation)? = null,
) {
    var text by remember { mutableStateOf(answer?.values?.firstOrNull().orEmpty()) }
    var liveError by remember { mutableStateOf<String?>(null) }

    fun handle(newText: String) {
        text = newText
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) {
            liveError = null
            onAnswer(SurveyAnswer(values = emptyList()))
            return
        }
        val validation = validator?.invoke(trimmed)
        liveError = validation?.error
        if (validation == null || validation.error == null) {
            onAnswer(SurveyAnswer(values = listOf(trimmed)))
        } else {
            // Reject invalid input: clear the answer so canAdvance stays false.
            onAnswer(SurveyAnswer(values = emptyList()))
        }
    }

    val modifier = Modifier.fillMaxWidth()
        .let { if (maxWidthDp > 0) it.widthIn(max = maxWidthDp.dp) else it }
        .let { if (minHeightDp > 0) it.defaultMinSize(minHeight = minHeightDp.dp) else it }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = ::handle,
            modifier = modifier,
            placeholder = placeholder?.let { { Text(it, color = SurveyTokens.TextTertiary) } },
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            isError = liveError != null,
        )
        liveError?.let { msg ->
            Spacer(Modifier.height(4.dp))
            Text(msg, color = Color(0xFFD92D20), fontSize = 12.sp)
        }
    }
}

// ── validators ─────────────────────────────────────────────────────────────

// RFC-5322-ish simple email regex: local@domain.tld with one or more labels.
private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

// Strict ISO date YYYY-MM-DD with a calendar-aware day check.
private val DATE_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2}) ?$")

private fun validateNumber(input: String, min: Double?, max: Double?): InputValidation {
    val n = input.toDoubleOrNull()
        ?: return InputValidation("Enter a valid number")
    if (min != null && n < min) {
        return InputValidation("Must be at least ${formatBound(min)}")
    }
    if (max != null && n > max) {
        return InputValidation("Must be at most ${formatBound(max)}")
    }
    if (min != null && max != null && min > max) {
        return InputValidation("Invalid range configured")
    }
    return InputValidation(null)
}

/** Trim trailing `.0` so whole-number bounds display as "5" instead of "5.0". */
private fun formatBound(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private fun validateEmail(input: String): InputValidation =
    if (EMAIL_REGEX.matches(input)) InputValidation(null)
    else InputValidation("Enter a valid email address")

private fun validateDate(input: String): InputValidation {
    val match = DATE_REGEX.matchEntire(input)
        ?: return InputValidation("Use format YYYY-MM-DD")
    val (yearS, monthS, dayS) = match.destructured
    val year = yearS.toInt()
    val month = monthS.toInt()
    val day = dayS.toInt()
    if (month !in 1..12) return InputValidation("Month must be 01–12")
    val maxDay = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> 31
    }
    if (day !in 1..maxDay) return InputValidation("Day must be 01–$maxDay")
    return InputValidation(null)
}
