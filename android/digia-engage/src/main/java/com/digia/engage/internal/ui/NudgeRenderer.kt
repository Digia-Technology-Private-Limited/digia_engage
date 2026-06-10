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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import com.digia.engage.internal.extractVariables
import com.digia.engage.internal.model.NudgeDisplayType
import com.digia.engage.internal.model.NudgeSurface
import kotlin.math.roundToInt

/**
 * Top-level nudge overlay — mounted once inside `DigiaHost`. Mirrors Flutter's
 * NudgePresentation strategy: a bottom-sheet or dialog "chrome" wrapping [NudgeView].
 *
 * The scrim covers the full display (status- and navigation-bar regions included),
 * matching Flutter, where `showDialog`/`showModalBottomSheet` render into the root
 * overlay. The Compose equivalent is `DialogProperties(decorFitsSystemWindows = false)`
 * — no manual window manipulation required.
 */
@Composable
internal fun NudgeRenderer() {
    val state by DigiaInstance.controller.nudgeOverlay.collectAsState()
    android.util.Log.d("DigiaDebug", "[NudgeRenderer] recomposed nudgeOverlay=${state?.payload?.id ?: "null"}")
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

    // Merge campaign defaultVariables + trigger variables (trigger wins)
    val variables = remember(state) {
        val trigger = extractVariables(state.payload.content)
        if (trigger != null) state.defaultVariables + trigger else state.defaultVariables
    }

    fun dismiss() = DigiaInstance.markNudgeDismissed()

    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalNudgeVariables provides variables) {
            NudgeView(state.config.content)
        }
    }

    when (surface.displayType) {
        NudgeDisplayType.BOTTOM_SHEET -> BottomSheetChrome(surface, ::dismiss, content)
        NudgeDisplayType.DIALOG -> DialogChrome(surface, ::dismiss, content)
    }
}

/** Full-screen barrier + window flags, mirroring Flutter's root-overlay barrier. */
private fun fullScreenDialogProperties() = DialogProperties(
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

// ─── bottom-sheet strategy ───────────────────────────────────────────────────────
// Mirrors Flutter showModalBottomSheet(useSafeArea: true, isScrollControlled: true):
// transparent route, top-rounded surface inside the safe area, scrim fills the rest.

@Composable
private fun BottomSheetChrome(
    surface: NudgeSurface,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = 150f * (screenHeight / 800f).coerceAtLeast(1f)
    val draggableState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
    }

    val barrierColor = surface.barrierColor?.let { Color(it) } ?: Color(0x4D000000)
    val bgColor = surface.backgroundColor?.let { Color(it) } ?: Color.White

    Dialog(
        onDismissRequest = { if (surface.backdropDismissible) onDismiss() },
        properties = fullScreenDialogProperties(),
    ) {
        BackHandler(enabled = true) { if (surface.backdropDismissible) onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(barrierColor)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (surface.backdropDismissible) onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = bgColor,
                shape = RoundedCornerShape(
                    topStart = surface.cornerRadius.dp,
                    topEnd = surface.cornerRadius.dp,
                ),
                // SafeArea equivalent: keep the surface above the gesture bar / keyboard
                // while the full-screen scrim shows through the inset strip below it.
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                    .then(
                        if (surface.draggable && surface.showHandle) Modifier.draggable(
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
                Box {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        if (surface.showHandle) DragHandle()
                        Box(modifier = Modifier.padding(surface.padding.dp)) { content() }
                    }
                    if (surface.showCloseButton) {
                        CloseButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }
    }
}

// ─── dialog strategy ─────────────────────────────────────────────────────────────
// Mirrors Flutter showDialog + Dialog(insetPadding: 24): centred, width-constrained,
// fully rounded surface, scrim defaults to ~black54.

@Composable
private fun DialogChrome(
    surface: NudgeSurface,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val barrierColor = surface.barrierColor?.let { Color(it) } ?: Color(0x4D000000)
    val bgColor = surface.backgroundColor?.let { Color(it) } ?: Color.White

    Dialog(
        onDismissRequest = { if (surface.backdropDismissible) onDismiss() },
        properties = fullScreenDialogProperties(),
    ) {
        BackHandler(enabled = true) { if (surface.backdropDismissible) onDismiss() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(barrierColor)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (surface.backdropDismissible) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = bgColor,
                shape = RoundedCornerShape(surface.cornerRadius.dp),
                // Flutter insetPadding(24) + keep within the safe area on tall content.
                modifier = Modifier
                    .systemBarsPadding()
                    .imePadding()
                    .padding(24.dp)
                    .width((screenWidth * surface.widthFraction).dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(surface.padding.dp),
                    ) { content() }
                    if (surface.showCloseButton) {
                        CloseButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }
    }
}

// ─── shared affordances (mirror Flutter _DragHandle / _CloseButton) ───────────────

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(100))
                .background(Color(0xFFE0E0E6)),
        )
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(12.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0x14000000))
            .clickable(onClick = onClick),
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
