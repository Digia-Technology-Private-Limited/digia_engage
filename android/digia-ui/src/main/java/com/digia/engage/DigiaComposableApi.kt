package com.digia.engage

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.BalloonAlign
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.compose.Balloon
import com.skydoves.balloon.compose.rememberBalloonBuilder
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.UIActionType
import com.digia.digiaui.framework.bottomsheet.BottomSheetHost
import com.digia.digiaui.framework.dialog.DialogHost
import com.digia.digiaui.init.DigiaUIManager
import com.digia.engage.internal.AnchoredOverlayState
import com.digia.engage.internal.DigiaInstance

@Composable
fun DigiaHost(content: @Composable () -> Unit) {
    val isUiReady by DigiaInstance.isUiReady.collectAsState()
    val activePayload by DigiaInstance.controller.activePayload.collectAsState()
    val activeAnchoredOverlay by DigiaInstance.controller.activeAnchoredOverlay.collectAsState()
    val currentScreen by DigiaInstance.currentScreen.collectAsState()
    val digiaUiManager = remember { DigiaUIManager.getInstance() }
    val duiFactory = remember { DUIFactory.getInstance() }

    LaunchedEffect(isUiReady) {
        if (isUiReady) {
            DigiaInstance.ensureRenderEngineInitialized()
        }
    }

    LaunchedEffect(activePayload?.id, isUiReady) {
        if (!isUiReady) return@LaunchedEffect
        when (val payload = activePayload) {
            null -> {
                digiaUiManager.dialogManager?.dismiss()
                digiaUiManager.bottomSheetManager?.dismiss()
            }
            else -> {
                val command = (payload.content["command"] as? String)?.trim()?.uppercase()

                if (command == "SHOW_TOOLTIP" || command == "SHOW_SPOTLIGHT") {
                    val anchorKey = payload.content["anchorKey"] as? String ?: run {
                        DigiaInstance.controller.dismiss()
                        return@LaunchedEffect
                    }

                    val anchoredView = AnchorRegistry.getView(anchorKey) ?: run {
                        DigiaInstance.controller.dismiss()
                        return@LaunchedEffect
                    }
                    // Defer one Looper cycle so any pending layout pass finishes before we
                    // read the anchor's on-screen position.
                    suspendCoroutine<Unit> { cont ->
                        anchoredView.post { cont.resume(Unit) }
                    }
                    val loc = IntArray(2)
                    anchoredView.getLocationOnScreen(loc)
                    val rect = Rect(loc[0], loc[1], loc[0] + anchoredView.width, loc[1] + anchoredView.height)

                    val cornerRadius = (anchoredView as? com.digia.engage.DigiaAnchorView)
                        ?.spotlightCornerRadius ?: 0f
                    DigiaInstance.controller.showAnchored(
                        AnchoredOverlayState(
                            payload = payload,
                            anchorKey = anchorKey,
                            anchorRect = rect,
                            command = command,
                            cornerRadius = cornerRadius,
                        )
                    )
                    DigiaInstance.controller.dismiss()
                    DigiaInstance.reportOverlayImpression(payload)
                    return@LaunchedEffect
                }

                val actionType = resolveActionType(payload) ?: return@LaunchedEffect
                val viewId = payload.content["viewId"] as? String ?: return@LaunchedEffect
                val args = payload.content["args"].toStringAnyMap()

                when (actionType) {
                    UIActionType.SHOW_DIALOG ->
                        digiaUiManager.dialogManager?.show(
                            componentId = viewId,
                            args = args,
                            onDismiss = { DigiaInstance.markOverlayDismissed(payload.id) },
                        )
                    UIActionType.SHOW_BOTTOM_SHEET ->
                        digiaUiManager.bottomSheetManager?.show(
                            componentId = viewId,
                            args = args,
                            onDismiss = { DigiaInstance.markOverlayDismissed(payload.id) },
                        )
                    else -> Unit
                }
                DigiaInstance.reportOverlayImpression(payload)
            }
        }
    }

    LaunchedEffect(currentScreen) {
        val overlay = activeAnchoredOverlay ?: return@LaunchedEffect
        DigiaInstance.markAnchoredOverlayDismissed(overlay.payload.id)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (isUiReady) {
            digiaUiManager.dialogManager?.let { manager ->
                DialogHost(
                    dialogManager = manager,
                    registry = duiFactory.getRegistry(),
                    resources = duiFactory.getResources(),
                )
            }

            digiaUiManager.bottomSheetManager?.let { manager ->
                BottomSheetHost(
                    bottomSheetManager = manager,
                    resources = duiFactory.getResources(),
                )
            }
        }

        AnchoredOverlayLayer(
            state = activeAnchoredOverlay,
            factory = duiFactory,
            onDismiss = { state ->
                DigiaInstance.markAnchoredOverlayDismissed(state.payload.id)
            },
        )
    }
}

@Composable
private fun AnchoredOverlayLayer(
    state: AnchoredOverlayState?,
    factory: DUIFactory,
    onDismiss: (AnchoredOverlayState) -> Unit,
) {
    if (state == null) return

    val context = LocalContext.current
    val isSpotlight = state.command == "SHOW_SPOTLIGHT"
    val viewId = state.payload.content["viewId"] as? String ?: return
    val args = state.payload.content["args"].toStringAnyMap()
    val anchorView = AnchorRegistry.getView(state.anchorKey) ?: return

    val builder = rememberBalloonBuilder(key = state.payload.id) {
        setWidthRatio(0.85f)
        setHeight(BalloonSizeSpec.WRAP)
        setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
        setIsVisibleArrow(true)
        setArrowSize(10)
        setArrowPosition(0.5f)
        setArrowColor(0xFF7A7A7A.toInt())
        setArrowColorMatchBalloon(false)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setElevation(0)
        setPadding(0)
        setCornerRadius(12f)
        setDismissWhenTouchOutside(true)
    }

    Balloon(
        builder = builder,
        balloonContent = {
            factory.CreateComponent(componentId = viewId, args = args)
        },
        onBalloonWindowInitialized = { window ->
            window.setOnBalloonDismissListener { onDismiss(state) }
        },
    ) { balloonWindow ->
        LaunchedEffect(state.payload.id) {
            val loc = IntArray(2)
            anchorView.getLocationOnScreen(loc)
            val screenHeight = context.resources.displayMetrics.heightPixels
            val spaceBelow = screenHeight - loc[1] - anchorView.height
            if (spaceBelow >= loc[1]) {
                balloonWindow.showAlign(BalloonAlign.BOTTOM, anchorView, emptyList(), 0, 0)
            } else {
                balloonWindow.showAlign(BalloonAlign.TOP, anchorView, emptyList(), 0, 0)
            }
        }
    }

    // Spotlight scrim: 4-rect approach drawn in the Compose layer below the Balloon popup.
    // Balloon's PopupWindow sits above and handles tap-to-dismiss via setDismissWhenTouchOutside,
    // which fires onDismiss and clears the scrim state.
    if (isSpotlight) {
        val localView = LocalView.current
        val rootLoc = IntArray(2).also { localView.getLocationOnScreen(it) }
        val rootOffsetXPx = rootLoc[0]
        val rootOffsetYPx = rootLoc[1]
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val scrim = Color.Black.copy(alpha = 0.7f)
            val cl = (state.anchorRect.left - rootOffsetXPx).toFloat()
            val ct = (state.anchorRect.top - rootOffsetYPx).toFloat()
            val cr = (state.anchorRect.right - rootOffsetXPx).toFloat()
            val cb = (state.anchorRect.bottom - rootOffsetYPx).toFloat()
            drawRect(scrim)
            val r = state.cornerRadius
            if (r > 0f) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(cl, ct),
                    size = Size(cr - cl, cb - ct),
                    cornerRadius = CornerRadius(r, r),
                    blendMode = BlendMode.Clear,
                )
            } else {
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(cl, ct),
                    size = Size(cr - cl, cb - ct),
                    blendMode = BlendMode.Clear,
                )
            }
        }
    }
}

@Composable
fun DigiaSlot(placementKey: String, modifier: Modifier = Modifier) {
    val isUiReady by DigiaInstance.isUiReady.collectAsState()
    val isRenderEngineReady by DigiaInstance.isRenderEngineReady.collectAsState()
    val slots by DigiaInstance.controller.slotPayloads.collectAsState()
    val factory = remember { DUIFactory.getInstance() }
    val payload = slots[placementKey]

    if (!isUiReady || !isRenderEngineReady || payload == null) return

    val viewId = payload.content["viewId"] as? String ?: return
    val args = payload.content["args"].toStringAnyMap()

    Box(modifier = modifier) {
        factory.CreateComponent(
            componentId = viewId,
            args = args,
        )
    }
}

@Composable
fun DigiaScreen(name: String) {
    LaunchedEffect(name) { Digia.setCurrentScreen(name) }
}

private fun Any?.toStringAnyMap(): Map<String, Any?>? {
    val rawMap = this as? Map<*, *> ?: return null
    return buildMap(rawMap.size) {
        for ((key, value) in rawMap) {
            val stringKey = key as? String
            if (stringKey != null) {
                put(stringKey, value)
            }
        }
    }
}

private fun resolveActionType(payload: InAppPayload): UIActionType? {
    return when ((payload.content["command"] as? String)?.trim()?.uppercase()) {
        "SHOW_DIALOG" -> UIActionType.SHOW_DIALOG
        "SHOW_BOTTOM_SHEET" -> UIActionType.SHOW_BOTTOM_SHEET
        else -> null
    }
}
