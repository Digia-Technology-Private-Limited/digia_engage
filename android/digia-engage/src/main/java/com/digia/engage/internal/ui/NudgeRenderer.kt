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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.digia.engage.internal.model.NudgeDisplayType
import com.digia.engage.internal.model.NudgeSurface
import com.digia.engage.internal.ui.nudge.LocalNudgeVariables
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
    val surface = state.config.surface

    LaunchedEffect(state.payload.id) {
        DigiaInstance.reportNudgeImpression()
    }

    fun dismiss() = DigiaInstance.markNudgeDismissed()

    // Carry the merged trigger variables down the render tree so each
    // `{{ placeholder }}` node interpolates at draw time (mirrors Flutter's
    // `VariableScopeProvider`).
    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalNudgeVariables provides state.defaultVariables) {
            NudgeColumnContent(state.config.content, ::dismiss)
        }
    }

    when (state.config.surface.displayType) {
        NudgeDisplayType.BOTTOM_SHEET -> BottomSheetChrome(surface, ::dismiss, content)
        NudgeDisplayType.DIALOG -> DialogChrome(surface, ::dismiss, content)
    }
}

@Composable
private fun BottomSheetChrome(
    surface: NudgeSurface,
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
        onDismissRequest = { if (surface.backdropDismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(enabled = true) { if (surface.backdropDismissible) onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColorOf(surface))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (surface.backdropDismissible) onDismiss() }
                // navigationBarsPadding here (not inside the Surface) so the
                // scrim fills the full window but the sheet surface stops above
                // the navigation bar — matching Flutter's SafeArea behaviour.
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = backgroundColorOf(surface),
                shape = RoundedCornerShape(
                    topStart = surface.cornerRadius.dp,
                    topEnd = surface.cornerRadius.dp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (screenHeight * NUDGE_SHEET_MAX_HEIGHT_RATIO).dp)
                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
                Box {
                    Column {
                        if (surface.showHandle) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (surface.draggable) Modifier.draggable(
                                            state = draggableState,
                                            orientation = Orientation.Vertical,
                                            onDragStopped = {
                                                if (dragOffset > dismissThresholdPx) onDismiss()
                                                else dragOffset = 0f
                                            },
                                        ) else Modifier,
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color(0xFFE0E0E6)),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Box(modifier = Modifier.padding(surface.padding.dp)) { content() }
                        }
                    }
                    if (surface.showCloseButton) {
                        NudgeCloseButton(
                            onDismiss = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogChrome(
    surface: NudgeSurface,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    Dialog(
        onDismissRequest = { if (surface.backdropDismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(enabled = true) { if (surface.backdropDismissible) onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColorOf(surface))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (surface.backdropDismissible) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = backgroundColorOf(surface),
                shape = RoundedCornerShape(surface.cornerRadius.dp),
                modifier = Modifier
                    .width((screenWidth * surface.widthFraction).dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                            .verticalScroll(rememberScrollState())
                            .padding(surface.padding.dp),
                    ) { content() }
                    if (surface.showCloseButton) {
                        NudgeCloseButton(
                            onDismiss = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }
    }
}

/// Surface background — null config inherits white (mirrors Flutter/iOS).
private fun backgroundColorOf(surface: NudgeSurface): Color =
    surface.backgroundColor?.let(::parseColor) ?: Color.White

/// Scrim/barrier — null config inherits ~40% black (mirrors Flutter/iOS default).
private fun scrimColorOf(surface: NudgeSurface): Color =
    surface.barrierColor?.let(::parseColor) ?: Color(0x66000000)

/// The "×" close affordance shown when `showCloseButton` is set. Mirrors
/// Flutter's `_CloseButton` and iOS's `closeButton`: a 26dp circle at the
/// surface's top-trailing corner.
@Composable
private fun NudgeCloseButton(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, end = 12.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0x14000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Close",
            tint = Color(0xFF66667A),
            modifier = Modifier.size(16.dp),
        )
    }
}

/// Parses a `#RRGGBB` / `#AARRGGBB` hex string (with or without the leading
/// `#`), mirroring iOS's `Color(hex:)` and Flutter's `Color(0xAARRGGBB)`
/// semantics. Returns transparent for anything unparseable.
private fun parseColor(hex: String): Color {
    val sanitized = hex.filter { it.isLetterOrDigit() }
    if (sanitized.length != 6 && sanitized.length != 8) return Color.Transparent
    return runCatching { Color(android.graphics.Color.parseColor("#$sanitized")) }
        .getOrElse { Color.Transparent }
}

/// Bottom-sheet height cap (matches iOS); short content hugs, tall scrolls.
private const val NUDGE_SHEET_MAX_HEIGHT_RATIO = 0.85f
