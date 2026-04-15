package com.digia.digiaui.framework.dialog

import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DialogRequest(
    val componentId: String,
    val args: JsonLike?,
    val barrierDismissible: Boolean,
    val barrierColor: Color?,
    val onDismiss: ((Any?) -> Unit)?
)

class DialogManager {
    private val _currentRequest = MutableStateFlow<DialogRequest?>(null)
    val currentRequest: StateFlow<DialogRequest?> = _currentRequest.asStateFlow()

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

    fun dismiss(result: Any? = null) {
        val request = _currentRequest.value
        _currentRequest.value = null
        request?.onDismiss?.invoke(result)
    }

    fun clear() {
        _currentRequest.value = null
    }
}

@Composable
fun DialogHost(
    dialogManager: DialogManager,
    registry: com.digia.digiaui.framework.DefaultVirtualWidgetRegistry,
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
            LaunchedEffect(request) {
                dialogWindowProvider?.window?.apply {
                    setDimAmount(0f)
                    setLayout(MATCH_PARENT, MATCH_PARENT)
                    setBackgroundDrawable(ColorDrawable(Color.Transparent.toArgb()))
                }
            }

            Box(Modifier.fillMaxSize()) {

                // Barrier layer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(request.barrierColor ?: Color.Black.copy(alpha = 0.54F))
                        .then(
                            if (request.barrierDismissible) { Modifier.clickable( indication = null, interactionSource = remember { MutableInteractionSource() } ) { dialogManager.dismiss() } } else Modifier )

                )

                // Dialog content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 40.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val dialogShape = RoundedCornerShape(28.dp)
                    Box(
                        modifier = Modifier
                            .widthIn(min = 280.dp, max = 560.dp)
                            .wrapContentHeight()
                            .shadow(elevation = 6.dp, shape = dialogShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                dialogShape
                            )
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent()
                                    }
                                }
                            },
                        contentAlignment = Alignment.TopStart
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


