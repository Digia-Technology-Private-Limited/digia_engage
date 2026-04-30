package com.digia.engage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.UIActionType
import com.digia.digiaui.framework.bottomsheet.BottomSheetHost
import com.digia.digiaui.framework.coachmark.CoachmarkHost
import com.digia.digiaui.framework.dialog.DialogHost
import com.digia.digiaui.framework.coachmark.CoachmarkStep
import com.digia.digiaui.framework.coachmark.CoachmarkRequest
import com.digia.digiaui.framework.tooltip.toTooltipPosition
import com.digia.digiaui.init.DigiaUIManager
import com.digia.engage.internal.DigiaInstance

@Composable
fun DigiaHost(content: @Composable () -> Unit) {
    val isUiReady by DigiaInstance.isUiReady.collectAsState()
    val activePayload by DigiaInstance.controller.activePayload.collectAsState()
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
                    UIActionType.SHOW_COACHMARK -> {
                        val placementKey = (payload.content["placementKey"] as? String)
                            ?: (payload.content["targetKey"] as? String)

                        val spotlightPadding = (payload.content["spotlightPadding"] as? Number)?.toFloat() ?: 16f
                        val spotlightRadius = (payload.content["spotlightRadius"] as? Number)?.toFloat() ?: 8f
                        val dimColorStr = payload.content["dimColor"] as? String
                        val dimColor = try {
                            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(dimColorStr))
                        } catch (_: Exception) {
                            androidx.compose.ui.graphics.Color(0xB4000000)
                        }

                        val step = CoachmarkStep(
                            targetKey = placementKey,
                            componentId = viewId,
                            args = args,
                            spotlightPadding = spotlightPadding,
                            spotlightRadius = spotlightRadius,
                        )

                        digiaUiManager.coachmarkManager?.show(
                            CoachmarkRequest(
                                steps = listOf(step),
                                dimColor = dimColor,
                                onDismiss = { DigiaInstance.markOverlayDismissed(payload.id) }
                            )
                        )
                    }
                    UIActionType.SHOW_TOOLTIP -> {
                        val targetKey = (payload.content["placementKey"] as? String)
                            ?: (payload.content["targetKey"] as? String)
                        val positionStr = payload.content["position"] as? String
                        val arrowColorHex = payload.content["arrowColor"] as? String ?: "#FFFFFF"
                        digiaUiManager.tooltipManager?.show(
                            componentId   = viewId,
                            args          = args,
                            targetKey     = targetKey,
                            position      = positionStr.toTooltipPosition(),
                            arrowColorHex = arrowColorHex,
                            onDismiss     = { DigiaInstance.markOverlayDismissed(payload.id) },
                        )
                    }
                    UIActionType.SHOW_FLOATER -> {
                        val anchorX = (payload.content["anchorX"] as? Number)?.toFloat() ?: 0.8f
                        val anchorY = (payload.content["anchorY"] as? Number)?.toFloat() ?: 0.5f
                        val draggable = payload.content["draggable"] as? Boolean ?: true
                        digiaUiManager.floaterManager?.show(
                            componentId = viewId,
                            args = args,
                            anchorX = anchorX,
                            anchorY = anchorY,
                            draggable = draggable,
                            onDismiss = { DigiaInstance.markOverlayDismissed(payload.id) },
                        )
                    }
                    UIActionType.SHOW_PIP -> {
                        duiFactory.ShowUIAction(
                            actionType    = UIActionType.SHOW_PIP,
                            componentId   = viewId,
                            componentArgs = payload.content["args"] as Map<String, Any?>?,
                            onDismiss     = { DigiaInstance.markOverlayDismissed(payload.id) },
                        )
                    }
                    else -> Unit
                }
                DigiaInstance.reportOverlayImpression(payload)
            }
        }
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

            digiaUiManager.coachmarkManager?.let { manager ->
                CoachmarkHost(
                        coachmarkManager = manager,
                        registry = duiFactory.getRegistry(),
                        resources = duiFactory.getResources(),
                )
            }

            digiaUiManager.tooltipManager?.let { manager ->
                com.digia.digiaui.framework.tooltip.TooltipHost(
                        tooltipManager = manager,
                        registry = duiFactory.getRegistry(),
                        resources = duiFactory.getResources(),
                )
            }

            digiaUiManager.floaterManager?.let { manager ->
                com.digia.digiaui.framework.floater.FloaterHost(
                        floaterManager = manager,
                        registry = duiFactory.getRegistry(),
                        resources = duiFactory.getResources(),
                )
            }

            digiaUiManager.pipManager?.let { manager ->
                com.digia.digiaui.framework.pip.PipHost(
                        pipManager = manager,
                        registry = duiFactory.getRegistry(),
                        resources = duiFactory.getResources(),
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
        "SHOW_COACHMARK" -> UIActionType.SHOW_COACHMARK
        "SHOW_TOOLTIP" -> UIActionType.SHOW_TOOLTIP
        "SHOW_FLOATER" -> UIActionType.SHOW_FLOATER
        "SHOW_PIP" -> UIActionType.SHOW_PIP
        else -> null
    }
}
