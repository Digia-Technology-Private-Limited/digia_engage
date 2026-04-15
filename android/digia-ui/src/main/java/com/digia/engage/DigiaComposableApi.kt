package com.digia.engage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.UIActionType
import com.digia.digiaui.framework.bottomsheet.BottomSheetHost
import com.digia.digiaui.framework.dialog.DialogHost
import com.digia.digiaui.init.DigiaUIManager
import com.digia.engage.internal.DigiaInstance

@Composable
fun DigiaInitialPage() {
    val isRenderEngineReady by DigiaInstance.isRenderEngineReady.collectAsState()
    if (!isRenderEngineReady) return

    remember { DUIFactory.getInstance() }.CreateInitialPage()
}

@Composable
fun DigiaHost(content: @Composable () -> Unit) {
    val isUiReady by DigiaInstance.isUiReady.collectAsState()
    val activePayload by DigiaInstance.controller.activePayload.collectAsState()
    val digiaUiManager = remember { DigiaUIManager.getInstance() }
    val duiFactory = remember { DUIFactory.getInstance() }

    if (isUiReady) {
        DigiaInstance.ensureRenderEngineInitialized()
    }
    val isRenderEngineReady by DigiaInstance.isRenderEngineReady.collectAsState()

    var previousOverlayPayloadId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activePayload?.id, isUiReady) {
        if (!isUiReady) return@LaunchedEffect
        val currentId = activePayload?.id
        if (previousOverlayPayloadId != null && currentId == null) {
            digiaUiManager.dialogManager?.dismiss()
            digiaUiManager.bottomSheetManager?.dismiss()
        }
        when (val payload = activePayload) {
            null -> Unit
            else -> {
                val actionType = resolveActionType(payload) ?: run {
                    previousOverlayPayloadId = currentId
                    return@LaunchedEffect
                }
                val viewId = payload.content["viewId"] as? String ?: run {
                    previousOverlayPayloadId = currentId
                    return@LaunchedEffect
                }
                val args = payload.content["args"].toStringAnyMap()

                when (actionType) {
                    UIActionType.SHOW_DIALOG ->
                            digiaUiManager.dialogManager?.show(
                                    componentId = viewId,
                                    args = args,
                                    onDismiss = { DigiaInstance.markDismissed(payload.id) },
                            )
                    UIActionType.SHOW_BOTTOM_SHEET ->
                            digiaUiManager.bottomSheetManager?.show(
                                    componentId = viewId,
                                    args = args,
                                    onDismiss = { DigiaInstance.markDismissed(payload.id) },
                            )
                    else -> Unit
                }
                DigiaInstance.reportOverlayImpression(payload)
            }
        }
        previousOverlayPayloadId = currentId
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isUiReady && isRenderEngineReady) {
            content()
        }

        if (isRenderEngineReady) {
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
