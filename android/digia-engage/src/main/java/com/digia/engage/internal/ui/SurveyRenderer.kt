package com.digia.engage.internal.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.digia.engage.internal.ActiveSurveyState
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.model.BlockMedia
import com.digia.engage.internal.model.BottomSheetHeightMode
import com.digia.engage.internal.model.DialogWidthPreset
import com.digia.engage.internal.model.MediaPosition
import com.digia.engage.internal.model.SurveyBlock
import com.digia.engage.internal.model.SurveyBlockType
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyDisplayType
import com.digia.engage.internal.model.SurveyNode
import kotlinx.coroutines.delay

/** Frame-settling buffer added before the survey is shown. */
private const val RENDER_DELAY_MS = 150L

/**
 * Top-level survey overlay — mounted once inside `DigiaHost`. Mirrors the
 * dashboard `BlockEditor` visual language: a card with thin progress bar,
 * category pill, title/body, type-specific content, and footer CTAs.
 */
@Composable
internal fun SurveyRenderer() {
    val state by DigiaInstance.surveyState.collectAsState()
    val active = state ?: return
    key(active.token) {
        SurveySession(active)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurveySession(state: ActiveSurveyState) {
    val survey = state.config
    val vm: SurveyViewModel = viewModel(
        key = "digia_survey_${state.token}",
        factory = SurveyViewModel.Factory(survey),
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.token) {
        delay(survey.timeDelayMs + RENDER_DELAY_MS)
        DigiaInstance.reportSurveyStarted()
        visible = true
    }
    if (!visible) return

    val accent = Color(survey.theme.accentColor)
    val background = Color(survey.theme.backgroundColor)
    val display = survey.settings.display

    fun finish(completed: Boolean) {
        if (completed) DigiaInstance.markSurveyCompleted(vm.responsePayload())
        else DigiaInstance.markSurveyDismissed()
    }

    val context = LocalContext.current
    LaunchedEffect(vm.redirectUrl) {
        val url = vm.redirectUrl ?: return@LaunchedEffect
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    LaunchedEffect(vm.isComplete) {
        if (vm.isComplete) finish(completed = true)
    }
    if (vm.isComplete) return

    when (display.type) {
        SurveyDisplayType.BOTTOM_SHEET -> {
            val sheet = display.bottomSheet
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = {
                    if (sheet.backdropDismissible || sheet.draggable) finish(completed = false)
                },
                sheetState = sheetState,
                containerColor = background,
                shape = RoundedCornerShape(
                    topStart = sheet.cornerRadius.dp,
                    topEnd = sheet.cornerRadius.dp,
                ),
            ) {
                SurveyBody(
                    vm = vm,
                    survey = survey,
                    accent = accent,
                    modifier = sheetHeightModifier(sheet.heightMode, sheet.customHeight)
                        .navigationBarsPadding(),
                    onClose = { finish(completed = false) },
                    showCloseButton = sheet.backdropDismissible,
                )
            }
        }
        SurveyDisplayType.DIALOG -> {
            val dialog = display.dialog
            Dialog(
                onDismissRequest = { if (dialog.backdropDismissible) finish(completed = false) },
                properties = DialogProperties(
                    dismissOnBackPress = dialog.backdropDismissible,
                    dismissOnClickOutside = dialog.backdropDismissible,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dialog.backdropOpacity))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { if (dialog.backdropDismissible) finish(completed = false) },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = background,
                        shape = RoundedCornerShape(dialog.cornerRadius.dp),
                        modifier = dialogWidthModifier(dialog.width, dialog.customWidth)
                            .padding(24.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {},
                    ) {
                        SurveyBody(
                            vm = vm,
                            survey = survey,
                            accent = accent,
                            modifier = Modifier,
                            onClose = { finish(completed = false) },
                            showCloseButton = dialog.showCloseButton,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sheetHeightModifier(mode: BottomSheetHeightMode, customPercent: Int): Modifier {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    return when (mode) {
        BottomSheetHeightMode.WRAP -> Modifier
        BottomSheetHeightMode.HALF -> Modifier.heightIn(max = (screenHeight * 0.5f).dp)
        BottomSheetHeightMode.FULL -> Modifier.fillMaxHeight()
        BottomSheetHeightMode.CUSTOM ->
            Modifier.heightIn(max = (screenHeight * (customPercent.coerceIn(10, 100) / 100f)).dp)
    }
}

@Composable
private fun dialogWidthModifier(preset: DialogWidthPreset, customPx: Int): Modifier {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val widthDp = when (preset) {
        DialogWidthPreset.SMALL -> (screenWidth * 0.6f).dp
        DialogWidthPreset.MEDIUM -> (screenWidth * 0.8f).dp
        DialogWidthPreset.LARGE -> (screenWidth * 0.95f).dp
        DialogWidthPreset.CUSTOM -> customPx.coerceAtLeast(200).dp
    }
    return Modifier.width(widthDp)
}

@Composable
private fun SurveyBody(
    vm: SurveyViewModel,
    survey: SurveyConfigModel,
    accent: Color,
    modifier: Modifier,
    onClose: () -> Unit,
    showCloseButton: Boolean,
) {
    val node = vm.currentNode ?: return
    val block = survey.blockFor(node) ?: return
    val pagination = survey.settings.pagination

    BackHandler(enabled = true) {
        when {
            vm.canGoBack -> vm.back()
            survey.settings.display.dismissible -> onClose()
        }
    }

    val position = visibleIndexOf(survey, node) + 1
    val total = survey.nodes.size.coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Top row: progress + counter + close
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val showBarHere = pagination.progressbar &&
                !(pagination.onlyShowOnQuestionBlock && block.type.isContent)
            if (showBarHere) {
                ProgressBar(progress = position.toFloat() / total, accent = accent, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (pagination.numberOfPages && !block.type.isContent) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "$position/$total",
                    color = SurveyTokens.TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = ComposeFontWeight.SemiBold,
                )
            }
            if (showCloseButton && survey.settings.display.dismissible) {
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close survey",
                        tint = SurveyTokens.TextTertiary,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .heightIn(max = if (block.flexibleHeight) Float.MAX_VALUE.dp else 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (block.showMedia && block.media.position == MediaPosition.TOP) {
                BlockMediaImage(block.media)
            }

            CategoryPill(block, accent)

            BlockTitle(block, accent)

            if (block.showMedia && block.media.position == MediaPosition.INLINE) {
                BlockMediaImage(block.media)
            }

            when (block.type) {
                SurveyBlockType.WELCOME -> WelcomeCta(accent = accent, onStart = {
                    DigiaInstance.reportSurveyAnswered(node.id, emptyMap())
                    vm.advance()
                })
                SurveyBlockType.RESULT_PAGE -> ResultPagePanel(accent = accent, onDone = { vm.advance() })
                SurveyBlockType.TEXT_MEDIA -> if (!block.media.hasUrl) MediaPlaceholder()
                else -> SurveyQuestionContent(
                    block = block,
                    answer = vm.answers[node.id],
                    accent = accent,
                    onAnswer = { vm.setAnswer(node.id, it) },
                )
            }
        }

        // Footer CTAs. Welcome + Result render their own inline CTAs (Start / Done);
        // every other block uses the bottom Back / Next pair — Next is the only way
        // to advance the survey so it always renders.
        val hasInlineCta = block.type == SurveyBlockType.WELCOME ||
            block.type == SurveyBlockType.RESULT_PAGE
        if (!hasInlineCta) {
            Spacer(Modifier.height(18.dp))
            FooterRow(
                accent = accent,
                canGoBack = vm.canGoBack,
                onBack = { vm.back() },
                nextEnabled = vm.canAdvance(),
                nextLabel = footerNextLabel(survey, node, block),
                onNext = {
                    if (!block.type.isContent) {
                        vm.answers[node.id]?.takeIf { it.isAnswered }?.let { ans ->
                            DigiaInstance.reportSurveyAnswered(node.id, ans.toMap())
                        }
                    }
                    vm.advance()
                },
            )
        }
    }
}

// ── chrome pieces ──────────────────────────────────────────────────────────

@Composable
private fun ProgressBar(progress: Float, accent: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(SurveyTokens.SurfaceSunken),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(accent),
        )
    }
}

@Composable
private fun CategoryPill(block: SurveyBlock, accent: Color) {
    if (block.type.isContent) return
    val label = categoryLabel(block.type) ?: return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = accent,
            fontSize = 10.5.sp,
            fontWeight = ComposeFontWeight.Bold,
        )
    }
}

@Composable
private fun BlockTitle(block: SurveyBlock, accent: Color) {
    if (block.title.text.isNotBlank()) {
        Text(
            text = block.title.text,
            style = block.title.style.toTextStyle(accent, default = TitleDefaults),
        )
    }
    val body = block.body
    if (body != null && body.text.isNotBlank()) {
        Text(
            text = body.text,
            style = body.style.toTextStyle(accent, default = BodyDefaults),
        )
    }
}

@Composable
private fun BlockMediaImage(media: BlockMedia) {
    if (!media.hasUrl) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, SurveyTokens.Border, RoundedCornerShape(10.dp)),
    ) {
        AsyncImage(
            model = media.url,
            contentDescription = media.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MediaPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurveyTokens.SurfaceSunken),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "— image / video —",
            color = SurveyTokens.TextTertiary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun WelcomeCta(accent: Color, onStart: () -> Unit) {
    Button(
        onClick = onStart,
        colors = ButtonDefaults.buttonColors(containerColor = accent),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text("Start →", fontWeight = ComposeFontWeight.SemiBold)
    }
}

@Composable
private fun ResultPagePanel(accent: Color, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurveyTokens.SurfaceSunken)
                .padding(14.dp),
        ) {
            Text(
                text = "✓ Response recorded. Aggregate results display here for users who completed.",
                color = SurveyTokens.TextSecondary,
                fontSize = 13.sp,
            )
        }
        OutlinedButton(
            onClick = onDone,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Done", color = SurveyTokens.TextPrimary)
        }
    }
}

@Composable
private fun FooterRow(
    accent: Color,
    canGoBack: Boolean,
    onBack: () -> Unit,
    nextEnabled: Boolean,
    nextLabel: String,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        if (canGoBack) {
            TextButton(onClick = onBack) {
                Text("← Back", color = SurveyTokens.TextSecondary)
            }
            Spacer(Modifier.weight(1f))
        }
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                disabledContainerColor = accent.copy(alpha = 0.35f),
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 18.dp, vertical = 10.dp,
            ),
        ) {
            Text(nextLabel, color = Color.White, fontWeight = ComposeFontWeight.SemiBold)
        }
    }
}

// ── helpers ────────────────────────────────────────────────────────────────

/** Position of [node] within the survey's node list (0-based). */
private fun visibleIndexOf(survey: SurveyConfigModel, node: SurveyNode): Int =
    survey.nodes.indexOfFirst { it.id == node.id }.coerceAtLeast(0)

/**
 * Footer Next-button label. "Finish" on the terminal node (the only node whose
 * default branch ends the survey and has no `node` target); "Next" otherwise.
 */
private fun footerNextLabel(
    survey: SurveyConfigModel,
    node: SurveyNode,
    block: SurveyBlock,
): String {
    if (block.type == SurveyBlockType.TEXT_MEDIA) return "Next"
    val target = node.branching.defaultTarget
    val noRules = node.branching.rules.isEmpty()
    val terminates = noRules && when (target.kind) {
        com.digia.engage.internal.model.BranchTargetKind.END -> true
        com.digia.engage.internal.model.BranchTargetKind.NEXT ->
            survey.nodes.indexOfFirst { it.id == node.id } == survey.nodes.lastIndex
        else -> false
    }
    return if (terminates) "Finish" else "Next"
}

private fun categoryLabel(type: SurveyBlockType): String? = when (type) {
    SurveyBlockType.SINGLE_SELECT -> "Select one answer"
    SurveyBlockType.MULTI_SELECT -> "Select all that apply"
    SurveyBlockType.RATING -> "Rate it"
    SurveyBlockType.NPS -> "Promoter score"
    SurveyBlockType.REACTION -> "Reaction poll"
    SurveyBlockType.THIS_OR_THAT -> "This or that"
    SurveyBlockType.TIER_LIST -> "Tier list"
    SurveyBlockType.UPVOTE -> "Upvote"
    SurveyBlockType.SHORT_TEXT -> "Short text"
    SurveyBlockType.LONG_TEXT -> "Long text"
    SurveyBlockType.NUMBER -> "Number"
    SurveyBlockType.EMAIL -> "Email"
    SurveyBlockType.DATE -> "Date picker"
    SurveyBlockType.WELCOME,
    SurveyBlockType.TEXT_MEDIA,
    SurveyBlockType.RESULT_PAGE -> null
}
