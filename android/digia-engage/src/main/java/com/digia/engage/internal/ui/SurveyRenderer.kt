package com.digia.engage.internal.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.digia.engage.internal.DigiaFontConfig
import com.digia.engage.internal.ActiveSurveyState
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.model.BlockMedia
import com.digia.engage.internal.model.BottomSheetHeightMode
import com.digia.engage.internal.model.CtaArrangement
import com.digia.engage.internal.model.CtaLayout
import com.digia.engage.internal.model.CtaSettings
import com.digia.engage.internal.model.DialogWidthPreset
import com.digia.engage.internal.model.MediaPosition
import com.digia.engage.internal.model.SurveyBlock
import com.digia.engage.internal.model.SurveyBlockType
import com.digia.engage.internal.model.PaginationStyle
import com.digia.engage.internal.model.ProgressIndicatorStyle
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyDisplayType
import com.digia.engage.internal.model.SurveyNode
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
        ProvideSurveyFont {
            SurveySession(active)
        }
    }
}

/**
 * Applies the global [DigiaConfig.fontFamily][com.digia.engage.DigiaConfig.fontFamily]
 * to all survey text, matching campaigns and guides. Raw `Text(...)` calls inherit
 * the family through [LocalTextStyle]; styled text built via `toTextStyle` / `toStyle`
 * sets the family directly. CompositionLocals propagate into the survey's `Dialog`
 * sub-composition, so this single wrap covers both dialog and bottom-sheet displays.
 * A no-op when no global family is configured, preserving the prior system-font look.
 */
@Composable
private fun ProvideSurveyFont(content: @Composable () -> Unit) {
    val family = DigiaFontConfig.composeFontFamily()
    if (family == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = family)),
            content = content,
        )
    }
}

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
        if (completed) DigiaInstance.markSurveyCompleted(vm.responsePayload(), vm.answers.toMap())
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
            // We deliberately avoid Material3 ModalBottomSheet: its
            // onDismissRequest can't reject scrim taps, so `backdrop_dismissible
            // = false` is impossible to honour. Implement the sheet as a
            // Dialog with a bottom-anchored panel and a manual scrim, with our
            // own drag-to-dismiss + drag-handle.
            val sheet = display.bottomSheet
            // Drag-to-dismiss state. Positive offset = the sheet has been
            // pulled down by `dragOffset` pixels. We dismiss past 150dp.
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val dismissThresholdPx = with(LocalConfiguration.current) { 150f * (screenHeightDp / 800f).coerceAtLeast(1f) }
            val draggableState = rememberDraggableState { delta ->
                dragOffset = (dragOffset + delta).coerceAtLeast(0f)
            }
            Dialog(
                onDismissRequest = { if (sheet.backdropDismissible) finish(completed = false) },
                properties = DialogProperties(
                    dismissOnBackPress = sheet.backdropDismissible,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { if (sheet.backdropDismissible) finish(completed = false) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        color = background,
                        shape = RoundedCornerShape(
                            topStart = sheet.cornerRadius.dp,
                            topEnd = sheet.cornerRadius.dp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(sheetHeightModifier(sheet.heightMode, sheet.customHeight))
                            .offset { IntOffset(0, dragOffset.roundToInt()) }
                            .then(
                                if (sheet.draggable) Modifier.draggable(
                                    state = draggableState,
                                    orientation = Orientation.Vertical,
                                    onDragStopped = {
                                        if (dragOffset > dismissThresholdPx) {
                                            finish(completed = false)
                                        } else {
                                            dragOffset = 0f
                                        }
                                    },
                                ) else Modifier,
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {},
                    ) {
                        Column(modifier = Modifier.navigationBarsPadding()) {
                            if (sheet.showHandle) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(SurveyTokens.Border),
                                    )
                                }
                            }
                            SurveyPanel(
                                vm = vm,
                                survey = survey,
                                accent = accent,
                                onClose = { finish(completed = false) },
                                onCompletedClose = { DigiaInstance.dismissCompletedSurvey() },
                                showCloseButton = sheet.backdropDismissible,
                            )
                        }
                    }
                }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {},
                    ) {
                        SurveyPanel(
                            vm = vm,
                            survey = survey,
                            accent = accent,
                            onClose = { finish(completed = false) },
                            onCompletedClose = { DigiaInstance.dismissCompletedSurvey() },
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

/**
 * Gates the welcome screen (fixed intro chrome, not a graph node) before the
 * node flow. Shows [WelcomeScreen] first when a visible welcome block exists,
 * then hands off to [SurveyBody].
 */
@Composable
private fun SurveyPanel(
    vm: SurveyViewModel,
    survey: SurveyConfigModel,
    accent: Color,
    onClose: () -> Unit,
    onCompletedClose: () -> Unit,
    showCloseButton: Boolean,
) {
    val welcome = survey.welcomeBlock()
    var welcomeDone by remember { mutableStateOf(false) }
    if (welcome != null && !welcomeDone) {
        WelcomeScreen(
            block = welcome,
            cta = survey.settings.cta,
            accent = accent,
            showClose = showCloseButton && survey.settings.display.dismissible,
            onStart = { welcomeDone = true },
            onClose = onClose,
        )
    } else {
        SurveyBody(
            vm = vm,
            survey = survey,
            accent = accent,
            modifier = Modifier,
            onClose = onClose,
            onCompletedClose = onCompletedClose,
            showCloseButton = showCloseButton,
        )
    }
}

@Composable
private fun WelcomeScreen(
    block: SurveyBlock,
    cta: CtaSettings,
    accent: Color,
    showClose: Boolean,
    onStart: () -> Unit,
    onClose: () -> Unit,
) {
    val blockBg = block.backgroundColorHex.toComposeColorOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (blockBg != null) Modifier.background(blockBg) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showClose) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close survey",
                        tint = SurveyTokens.TextTertiary,
                    )
                }
            }
        }
        if (block.showMedia && block.media.position == MediaPosition.TOP) {
            BlockMediaImage(block.media)
        }
        BlockTitle(block, accent)
        if (block.showMedia && block.media.position == MediaPosition.INLINE) {
            BlockMediaImage(block.media)
        }
        val stacked = cta.layout == CtaLayout.STACKED
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = ctaBg(cta, accent)),
            shape = RoundedCornerShape(cta.cornerRadius.dp),
            modifier = Modifier
                .padding(top = 2.dp)
                .then(if (stacked) Modifier.fillMaxWidth() else Modifier),
        ) {
            Text(cta.startLabel, color = ctaText(cta), fontWeight = ComposeFontWeight.SemiBold)
        }
    }
}

@Composable
private fun SurveyBody(
    vm: SurveyViewModel,
    survey: SurveyConfigModel,
    accent: Color,
    modifier: Modifier,
    onClose: () -> Unit,
    onCompletedClose: () -> Unit,
    showCloseButton: Boolean,
) {
    val node = vm.currentNode ?: return
    val block = survey.blockFor(node) ?: return
    val pagination = survey.settings.pagination
    val timerCfg = survey.settings.timer
    val cta = survey.settings.cta

    BackHandler(enabled = true) {
        when {
            vm.canGoBack -> vm.back()
            survey.settings.display.dismissible -> onClose()
        }
    }

    val position = visibleIndexOf(survey, node) + 1
    val total = survey.nodes.size.coerceAtLeast(1)
    var completionReported by remember { mutableStateOf(false) }

    fun reportCompletionIfResultIsNext() {
        if (!completionReported && vm.nextBlockIsResultPage()) {
            DigiaInstance.reportSurveyCompleted(vm.responsePayload(), vm.answers.toMap())
            completionReported = true
        }
    }

    // ── Timer state ────────────────────────────────────────────────────────
    // Ticks once per second whenever the timer is enabled. Pauses on content
    // blocks when `pauseOnNonTimerBlock` is set; reaches zero → auto-finish.
    var remainingSecs by remember { mutableIntStateOf(timerCfg.timeLimitSeconds) }
    val timerPaused = timerCfg.pauseOnNonTimerBlock && block.type.isContent
    if (timerCfg.enabled && timerCfg.timeLimitSeconds > 0) {
        LaunchedEffect(timerPaused) {
            while (remainingSecs > 0 && !timerPaused) {
                delay(1000)
                remainingSecs = (remainingSecs - 1).coerceAtLeast(0)
            }
            if (remainingSecs == 0) onClose()
        }
    }

    // ── Auto-advance ───────────────────────────────────────────────────────
    // Single-pick blocks (rating/nps/single_select/reaction) advance themselves
    // ~250 ms after the user picks. Multi-pick and text inputs still need the
    // explicit Next CTA. autoAdvance is gated by `settings.autoAdvance`.
    val currentAnswer = vm.answers[node.id]
    if (survey.settings.autoAdvance && block.type.isAutoAdvanceCandidate &&
        currentAnswer?.isAnswered == true) {
        LaunchedEffect(node.id, currentAnswer) {
            delay(250)
            if (vm.currentNode?.id == node.id) {
                DigiaInstance.reportSurveyAnswered(node.id, currentAnswer.toMap())
                reportCompletionIfResultIsNext()
                vm.advance()
            }
        }
    }

    val blockBg = block.backgroundColorHex.toComposeColorOrNull()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (blockBg != null) Modifier.background(blockBg) else Modifier)
            .imePadding()
            .padding(14.dp),
    ) {
        // Top row: progress + counter + timer + close
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val showBarHere = pagination.progressbar &&
                !(pagination.onlyShowOnQuestionBlock && block.type.isContent)
            if (showBarHere) {
                ProgressBar(
                    progress = position.toFloat() / total,
                    style = pagination.paginationStyle,
                    segments = total,
                    currentSegment = position,
                    accent = accent,
                    indicator = pagination.progressIndicatorStyle,
                    modifier = Modifier.weight(1f),
                )
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
            if (timerCfg.enabled && timerCfg.timeLimitSeconds > 0) {
                Spacer(Modifier.width(10.dp))
                TimerChip(
                    remainingSecs = remainingSecs,
                    warningAtSecs = timerCfg.warningAtSeconds,
                    accent = accent,
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
                SurveyBlockType.RESULT_PAGE -> ResultPagePanel(cta = cta, accent = accent, onDone = onCompletedClose)
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
        // every other block uses the bottom Back / Next pair. The Next button is
        // omitted only when `settings.chooseButton == false` AND auto-advance is
        // about to fire (single-pick + autoAdvance enabled). Text inputs, multi-
        // select, and TEXT_MEDIA can never auto-advance, so they always show Next
        // even when chooseButton is off — otherwise the survey would be stuck.
        val hasInlineCta = block.type == SurveyBlockType.WELCOME ||
            block.type == SurveyBlockType.RESULT_PAGE
        val canAutoAdvanceThisBlock = survey.settings.autoAdvance &&
            block.type.isAutoAdvanceCandidate
        val showNext = !hasInlineCta &&
            (survey.settings.chooseButton || !canAutoAdvanceThisBlock)
        if (showNext) {
            Spacer(Modifier.height(18.dp))
            FooterRow(
                cta = cta,
                accent = accent,
                canGoBack = vm.canGoBack,
                onBack = { vm.back() },
                nextEnabled = vm.canAdvance(),
                nextLabel = footerNextLabel(survey, node, block, cta),
                onNext = {
                    if (!block.type.isContent) {
                        vm.answers[node.id]?.takeIf { it.isAnswered }?.let { ans ->
                            DigiaInstance.reportSurveyAnswered(node.id, ans.toMap())
                        }
                    }
                    reportCompletionIfResultIsNext()
                    vm.advance()
                },
            )
        }
    }
}

// ── chrome pieces ──────────────────────────────────────────────────────────

@Composable
private fun ProgressBar(
    progress: Float,
    style: PaginationStyle,
    segments: Int,
    currentSegment: Int,
    accent: Color,
    indicator: ProgressIndicatorStyle,
    modifier: Modifier,
) {
    val activeColor = indicator.activeColorHex.toComposeColorOrNull() ?: accent
    val trackColor = indicator.trackColorHex.toComposeColorOrNull() ?: SurveyTokens.SurfaceSunken
    val barHeight = indicator.height.dp
    val barShape = RoundedCornerShape(indicator.cornerRadius.dp)
    if (style == PaginationStyle.SEGMENTED && segments > 1) {
        Row(
            modifier = modifier.height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (i in 1..segments) {
                val on = i <= currentSegment
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(barShape)
                        .background(if (on) activeColor else trackColor),
                )
            }
        }
        return
    }
    Box(
        modifier = modifier
            .height(barHeight)
            .clip(barShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(activeColor),
        )
    }
}

@Composable
private fun TimerChip(remainingSecs: Int, warningAtSecs: Int, accent: Color) {
    val warn = warningAtSecs > 0 && remainingSecs <= warningAtSecs
    val tint = if (warn) Color(0xFFD92D20) else accent
    val minutes = remainingSecs / 60
    val seconds = remainingSecs % 60
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "%d:%02d".format(minutes, seconds),
            color = tint,
            fontSize = 11.sp,
            fontWeight = ComposeFontWeight.SemiBold,
        )
    }
}

@Composable
private fun CategoryPill(block: SurveyBlock, accent: Color) {
    if (block.type.isContent || !block.showTag) return
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val body = block.body
    if (body != null && body.text.isNotBlank()) {
        Text(
            text = body.text,
            style = body.style.toTextStyle(accent, default = BodyDefaults),
            modifier = Modifier.fillMaxWidth(),
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
            contentScale = when (media.boxFit) {
                "contain" -> ContentScale.Fit
                "fill" -> ContentScale.FillBounds
                else -> ContentScale.Crop
            },
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
private fun ResultPagePanel(cta: CtaSettings, accent: Color, onDone: () -> Unit) {
    val stacked = cta.layout == CtaLayout.STACKED
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = ctaBg(cta, accent)),
            shape = RoundedCornerShape(cta.cornerRadius.dp),
            modifier = if (stacked) Modifier.fillMaxWidth() else Modifier,
        ) {
            Text(cta.doneLabel, color = ctaText(cta), fontWeight = ComposeFontWeight.SemiBold)
        }
    }
}

/** Resolved CTA background — explicit hex, else the theme accent. */
private fun ctaBg(cta: CtaSettings, accent: Color): Color =
    cta.bgColorHex.toComposeColorOrNull() ?: accent

/** Resolved CTA text colour — explicit hex, else white. */
private fun ctaText(cta: CtaSettings): Color =
    cta.textColorHex.toComposeColorOrNull() ?: Color.White

private fun String.toComposeColorOrNull(): Color? {
    if (isBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Composable
private fun FooterRow(
    cta: CtaSettings,
    accent: Color,
    canGoBack: Boolean,
    onBack: () -> Unit,
    nextEnabled: Boolean,
    nextLabel: String,
    onNext: () -> Unit,
) {
    val bg = ctaBg(cta, accent)
    val textColor = ctaText(cta)
    val shape = RoundedCornerShape(cta.cornerRadius.dp)
    val contentPad = androidx.compose.foundation.layout.PaddingValues(
        horizontal = 18.dp, vertical = 10.dp,
    )

    val nextButton: @Composable (Modifier) -> Unit = { mod ->
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = bg,
                disabledContainerColor = bg.copy(alpha = 0.35f),
            ),
            shape = shape,
            contentPadding = contentPad,
            modifier = mod,
        ) {
            Text(nextLabel, color = textColor, fontWeight = ComposeFontWeight.SemiBold)
        }
    }

    if (cta.layout == CtaLayout.STACKED) {
        // Full-width buttons, primary on top, Back below.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            nextButton(Modifier.fillMaxWidth())
            if (canGoBack) {
                OutlinedButton(
                    onClick = onBack,
                    shape = shape,
                    contentPadding = contentPad,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(cta.backLabel, color = SurveyTokens.TextPrimary)
                }
            }
        }
        return
    }

    // Inline row, distributed by the chosen arrangement.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = ctaArrangement(cta.arrangement),
    ) {
        if (canGoBack) {
            TextButton(onClick = onBack) {
                Text(cta.backLabel, color = SurveyTokens.TextSecondary)
            }
        }
        nextButton(Modifier)
    }
}

private fun ctaArrangement(arrangement: CtaArrangement): Arrangement.Horizontal =
    when (arrangement) {
        CtaArrangement.SPACE_BETWEEN -> Arrangement.SpaceBetween
        CtaArrangement.SPACE_EVENLY -> Arrangement.SpaceEvenly
        CtaArrangement.CENTER -> Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        CtaArrangement.START -> Arrangement.spacedBy(12.dp, Alignment.Start)
        CtaArrangement.END -> Arrangement.spacedBy(12.dp, Alignment.End)
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
    cta: CtaSettings,
): String {
    if (block.type == SurveyBlockType.TEXT_MEDIA) return cta.nextLabel
    val target = node.branching.defaultTarget
    val noRules = node.branching.rules.isEmpty()
    val terminates = noRules && when (target.kind) {
        com.digia.engage.internal.model.BranchTargetKind.END -> true
        com.digia.engage.internal.model.BranchTargetKind.NEXT ->
            survey.nodes.indexOfFirst { it.id == node.id } == survey.nodes.lastIndex
        else -> false
    }
    return if (terminates) cta.doneLabel else cta.nextLabel
}

private fun categoryLabel(type: SurveyBlockType): String? = when (type) {
    SurveyBlockType.SINGLE_SELECT -> "Select one answer"
    SurveyBlockType.MULTI_SELECT -> "Select all that apply"
    SurveyBlockType.RATING -> "Rate it"
    SurveyBlockType.NPS,
    SurveyBlockType.NPS_EMOJI,
    SurveyBlockType.NPS_SMILEY -> "Promoter score"
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
