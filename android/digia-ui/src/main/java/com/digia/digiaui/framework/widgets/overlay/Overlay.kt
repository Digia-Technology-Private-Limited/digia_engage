package com.digia.digiaui.framework.widgets.overlay

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow



@Composable
fun Overlay(
    modifier: Modifier = Modifier,
    controller: DSOverlayController? = null,
    showOnTap: Boolean = true,
    dismissOnTapOutside: Boolean = true,
    dismissOnTapInside: Boolean = false,
    offset: IntOffset = IntOffset.Zero,
    childAlignment: Alignment = Alignment.TopStart,
    popupAlignment: Alignment = Alignment.TopStart,
    popup: @Composable (DSOverlayController) -> Unit,
    content: @Composable () -> Unit
) {
    val overlayState = remember { OverlayState() }
    val resolvedController = controller ?: remember { DSOverlayController() }

    // Attach/detach controller like Flutter's initState/dispose.
    DisposableEffect(resolvedController) {
        resolvedController.attach(overlayState)
        onDispose { resolvedController.attach(null) }
    }

    var anchorBounds by remember { mutableStateOf<IntRect?>(null) }

    Box(modifier = modifier) {

        Box(
            modifier = Modifier.onGloballyPositioned {
                    anchorBounds = it.boundsInWindowIntRect()
                }
                .then(
                    if (showOnTap) {
                        Modifier.clickable { overlayState.show() }
                    } else Modifier
                )
        ) {
            content()
        }

        if (overlayState.isVisible && anchorBounds != null) {
            OverlayPopup(
                anchorBounds = anchorBounds!!,
                offset = offset,
                childAlignment = childAlignment,
                popupAlignment = popupAlignment,
                dismissOnTapOutside = dismissOnTapOutside,
                dismissOnTapInside = dismissOnTapInside,
                onDismiss = { overlayState.hide() }
            ) {
                popup(resolvedController)
            }
        }
    }
}


@Composable
private fun OverlayPopup(
    anchorBounds: IntRect,
    offset: IntOffset,
    childAlignment: Alignment,
    popupAlignment: Alignment,
    dismissOnTapOutside: Boolean,
    dismissOnTapInside: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val positionProvider = remember(offset, childAlignment, popupAlignment) {
        AnchorPopupPositionProvider(
            offset = offset,
            targetAnchor = childAlignment,
            followerAnchor = popupAlignment,
        )
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = dismissOnTapOutside,
            dismissOnClickOutside = dismissOnTapOutside,
            dismissOnBackPress = dismissOnTapOutside,
        ),
        onDismissRequest = onDismiss,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Only add an input-capturing scrim when we intend to dismiss on outside taps.
            if (dismissOnTapOutside) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { onDismiss() }
                        }
                )
            }

            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .then(
                        if (dismissOnTapInside) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures { onDismiss() }
                            }
                        } else Modifier
                    )
            ) {
                content()
            }
        }
    }
}

private class AnchorPopupPositionProvider(
    private val offset: IntOffset,
    private val targetAnchor: Alignment,
    private val followerAnchor: Alignment,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // Point inside the anchor.
        val targetPoint = targetAnchor.align(
            size = IntSize.Zero,
            space = anchorBounds.size,
            layoutDirection = layoutDirection,
        )

        // Point inside the popup content.
        val followerPoint = followerAnchor.align(
            size = IntSize.Zero,
            space = popupContentSize,
            layoutDirection = layoutDirection,
        )

        return IntOffset(
            x = anchorBounds.left + targetPoint.x - followerPoint.x + offset.x,
            y = anchorBounds.top + targetPoint.y - followerPoint.y + offset.y,
        )
    }
}

private fun LayoutCoordinates.boundsInWindowIntRect(): IntRect {
    val pos = positionInWindow()
    return IntRect(
        left = pos.x.toInt(),
        top = pos.y.toInt(),
        right = (pos.x + size.width).toInt(),
        bottom = (pos.y + size.height).toInt(),
    )
}
