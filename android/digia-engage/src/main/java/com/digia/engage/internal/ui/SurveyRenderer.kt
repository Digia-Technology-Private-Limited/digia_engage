package com.digia.engage.internal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digia.engage.internal.ActiveSurveyState
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyQuestionType
import com.digia.engage.internal.model.SurveySurface
import kotlinx.coroutines.delay

/** Frame-settling buffer added before the survey is shown. */
private const val RENDER_DELAY_MS = 150L

/**
 * Top-level survey overlay — mounted once inside `DigiaHost`. Observes
 * [DigiaInstance.surveyState] and renders the active survey campaign as a
 * bottom-sheet or dialog multi-step flow with branching.
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
        DigiaInstance.reportSurveyImpression()
        visible = true
    }
    if (!visible) return

    val accent = Color(survey.theme.accentColor)
    val background = Color(survey.theme.backgroundColor)

    fun finish(completed: Boolean) {
        if (completed) DigiaInstance.markSurveyCompleted(vm.responsePayload())
        else DigiaInstance.markSurveyDismissed()
    }

    LaunchedEffect(vm.isComplete) {
        if (vm.isComplete) finish(completed = true)
    }
    if (vm.isComplete) return

    when (survey.surface) {
        SurveySurface.BOTTOM_SHEET -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { finish(completed = false) },
                sheetState = sheetState,
                containerColor = background,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = null,
            ) {
                SurveyBody(
                    vm = vm,
                    survey = survey,
                    accent = accent,
                    modifier = Modifier.navigationBarsPadding(),
                    onClose = { finish(completed = false) },
                )
            }
        }
        SurveySurface.DIALOG -> {
            Dialog(
                onDismissRequest = { if (survey.dismissible) finish(completed = false) },
                properties = DialogProperties(
                    dismissOnBackPress = survey.dismissible,
                    dismissOnClickOutside = survey.dismissible,
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
                        ) { if (survey.dismissible) finish(completed = false) },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = background,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
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
                        )
                    }
                }
            }
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
) {
    val step = vm.currentStep ?: return

    BackHandler(enabled = true) {
        when {
            vm.canGoBack -> vm.back()
            survey.dismissible -> onClose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (survey.showProgress) {
                LinearProgressIndicator(
                    progress = vm.progress,
                    color = accent,
                    trackColor = accent.copy(alpha = 0.15f),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (survey.dismissible) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close survey",
                        tint = Color(0xFF9A9A9A),
                    )
                }
            }
        }

        Spacer(Modifier.size(16.dp))

        Column(
            modifier = Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!survey.title.isNullOrBlank()) {
                Text(
                    text = survey.title,
                    fontSize = 13.sp,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(6.dp))
            }
            if (!step.question.isNullOrBlank()) {
                Text(
                    text = step.question,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                )
            }
            if (!step.subheader.isNullOrBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(text = step.subheader, fontSize = 14.sp, color = Color(0xFF6A6A6A))
            }

            Spacer(Modifier.size(16.dp))

            if (step.type == SurveyQuestionType.THANK_YOU) {
                if (!step.metadata.bodyText.isNullOrBlank()) {
                    Text(
                        text = step.metadata.bodyText,
                        fontSize = 15.sp,
                        color = Color(0xFF4A4A4A),
                    )
                }
            } else {
                SurveyQuestionContent(
                    step = step,
                    answer = vm.answers[step.id],
                    accent = accent,
                    onAnswer = { vm.setAnswer(step.id, it) },
                )
            }
        }

        Spacer(Modifier.size(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (vm.canGoBack) {
                TextButton(onClick = { vm.back() }) {
                    Text("Back", color = Color(0xFF6A6A6A))
                }
                Spacer(Modifier.weight(1f))
            }
            val isThankYou = step.type == SurveyQuestionType.THANK_YOU
            Button(
                onClick = {
                    if (!isThankYou) {
                        vm.answers[step.id]?.takeIf { it.isAnswered }?.let { answer ->
                            DigiaInstance.reportSurveyAnswered(step.id, answer.toMap())
                        }
                    }
                    vm.advance()
                },
                enabled = vm.canAdvance(),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Text(step.buttonLabel ?: if (isThankYou) "Done" else "Next")
            }
        }
    }
}
