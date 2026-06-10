package com.digia.engage.internal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.digia.engage.internal.DigiaInstance
import com.digia.engage.internal.NudgeOverlayState
import com.digia.engage.internal.model.NudgeContainerConfig
import com.digia.engage.internal.model.NudgeTemplateType
import com.digia.engage.internal.ui.nudge.NudgeColumnContent
import kotlin.math.roundToInt

@Composable
internal fun NudgeRenderer() {
    val state by DigiaInstance.controller.nudgeOverlay.collectAsState()
    val active = state ?: return
    key(active.payload.id) {
        NudgeSession(active)
    }
}

@Composable
private fun NudgeSession(state: NudgeOverlayState) {
    val container = state.config.container

    LaunchedEffect(state.payload.id) {
        DigiaInstance.reportNudgeImpression()
    }

    fun dismiss() = DigiaInstance.markNudgeDismissed()

    val content: @Composable () -> Unit = {
        NudgeColumnContent(state.config.layout, ::dismiss)
    }

    when (state.config.templateType) {
        NudgeTemplateType.BOTTOM_SHEET -> BottomSheetChrome(container, ::dismiss, content)
        NudgeTemplateType.DIALOG -> DialogChrome(container, ::dismiss, content)
    }
}

@Composable
private fun BottomSheetChrome(
    container: NudgeContainerConfig,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalConfiguration.current) {
        150f * (screenHeightDp / 800f).coerceAtLeast(1f)
    }
    val draggableState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
    }

    Dialog(
        onDismissRequest = { if (container.dismissOnOutsideTap) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BackHandler(enabled = true) { if (container.dismissOnOutsideTap) onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(parseColor(container.scrimColor))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (container.dismissOnOutsideTap) onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = parseColor(container.bgColor),
                shape = RoundedCornerShape(
                    topStart = container.cornerRadius.dp,
                    topEnd = container.cornerRadius.dp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeight * container.maxHeightRatio).dp)
                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                    .then(
                        if (container.dragHandle) Modifier.draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                if (dragOffset > dismissThresholdPx) onDismiss() else dragOffset = 0f
                            },
                        ) else Modifier,
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (container.dragHandle) {
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
                                    .background(Color(0x33000000)),
                            )
                        }
                    }
                    Box(modifier = Modifier.padding(container.padding.dp)) { content() }
                }
            }
        }
    }
}

@Composable
private fun DialogChrome(
    container: NudgeContainerConfig,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    Dialog(
        onDismissRequest = { if (container.dismissOnOutsideTap) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BackHandler(enabled = true) { if (container.dismissOnOutsideTap) onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(parseColor(container.scrimColor))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (container.dismissOnOutsideTap) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = parseColor(container.bgColor),
                shape = RoundedCornerShape(container.cornerRadius.dp),
                modifier = Modifier
                    .width((container.width ?: (screenWidth * 0.85f)).dp)
                    .padding(0.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                        .verticalScroll(rememberScrollState())
                        .padding(container.padding.dp),
                ) { content() }
            }
        }
    }
}

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color.Transparent }
