package com.digia.digiaui.framework.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.DefaultVirtualWidgetRegistry
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.component.DUIComponent
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.init.DigiaUIManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data class representing a dialog request
 */
data class DialogRequest(
    val componentId: String,
    val args: JsonLike?,
    val barrierDismissible: Boolean,
    val barrierColor: Color?,
    val onDismiss: ((Any?) -> Unit)?
)

/**
 * Manager for showing dialogs
 * 
 * Manages dialog display state and provides a way to show/hide dialogs
 * from action processors.
 */
class DialogManager {
    private val _currentRequest = MutableStateFlow<DialogRequest?>(null)
    val currentRequest: StateFlow<DialogRequest?> = _currentRequest.asStateFlow()

    /**
     * Show a dialog with the specified component
     */
    fun show(
        componentId: String,
        args: JsonLike? = null,
        barrierDismissible: Boolean = true,
        barrierColor: Color? = null,
        onDismiss: ((Any?) -> Unit)? = null
    ) {
        _currentRequest.value = DialogRequest(
            componentId = componentId,
            args = args,
            barrierDismissible = barrierDismissible,
            barrierColor = barrierColor,
            onDismiss = onDismiss
        )
    }

    /**
     * Dismiss the current dialog
     */
    fun dismiss(result: Any? = null) {
        val request = _currentRequest.value
        _currentRequest.value = null
        request?.onDismiss?.invoke(result)
    }

    /**
     * Clear the current request without triggering onDismiss
     */
    fun clear() {
        _currentRequest.value = null
    }
}

/**
 * Composable that observes dialog state and displays dialogs
 */
@Composable
fun DialogHost(
    dialogManager: DialogManager,
    registry: DefaultVirtualWidgetRegistry,
    resources: UIResources
) {
    val currentRequest by dialogManager.currentRequest.collectAsState()

    currentRequest?.let { request ->

        Dialog(
            onDismissRequest = {
                if (request.barrierDismissible) {
                    dialogManager.dismiss()
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = request.barrierDismissible,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {

            val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
            LaunchedEffect(Unit) {
                dialogWindowProvider?.window?.setDimAmount(0f)
            }

            Box(Modifier.fillMaxSize()) {

                // ✅ Barrier layer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(request.barrierColor ?: Color.Black.copy(alpha = 0.3F))
                        .then(
                            if (request.barrierDismissible) { Modifier.clickable( indication = null, interactionSource = remember { MutableInteractionSource() } ) { dialogManager.dismiss() } } else Modifier )

                )

                // ✅ Dialog content (separate so clicks don’t dismiss)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight().pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent()
                                        }
                                    }
                                }
                        ) {
                            DUIFactory.getInstance().CreateComponent(
                                componentId = request.componentId,
                                args = request.args,
                            )
                        }
                    }

            }
        }
    }
}

/**
 * Parse color string to Compose Color
 * Supports hex colors (#RRGGBB, #AARRGGBB) and named colors
 */
private fun parseColor(colorString: String?): Color? {
    if (colorString == null) return null
    
    return try {
        when {
            colorString.startsWith("#") -> {
                val hex = colorString.substring(1)
                when (hex.length) {
                    6 -> Color(android.graphics.Color.parseColor("#FF$hex"))
                    8 -> Color(android.graphics.Color.parseColor("#$hex"))
                    else -> null
                }
            }
            else -> null // Could add named color support here
        }
    } catch (e: Exception) {
        null
    }
}
