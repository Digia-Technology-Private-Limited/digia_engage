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
import com.digia.digiaui.framework.bottomsheet.BottomSheetHost
import com.digia.digiaui.framework.dialog.DialogHost
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
                val type = resolvePayloadType(payload)
                val componentId = payload.content["componentId"] as? String ?: return@LaunchedEffect
                val args = payload.content["args"].toStringAnyMap()

                when (type) {
                    "dialog" -> digiaUiManager.dialogManager?.show(
                        componentId = componentId,
                        args = args,
                        onDismiss = { DigiaInstance.markDismissed(payload.id) },
                    )
                    "bottom_sheet" -> digiaUiManager.bottomSheetManager?.show(
                        componentId = componentId,
                        args = args,
                        onDismiss = { DigiaInstance.markDismissed(payload.id) },
                    )
                    else -> Unit
                }
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

    val componentId = payload.content["componentId"] as? String ?: return
    val args = payload.content["args"].toStringAnyMap()

    Box(modifier = modifier) {
        factory.CreateComponent(
            componentId = componentId,
            args = args,
        )
    }
}

@Composable
fun DigiaScreen(name: String) {
    LaunchedEffect(name) {
        Digia.setCurrentScreen(name)
    }
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

private fun resolvePayloadType(payload: InAppPayload): String {
    return (payload.content["type"] as? String)?.lowercase() ?: "dialog"
}
