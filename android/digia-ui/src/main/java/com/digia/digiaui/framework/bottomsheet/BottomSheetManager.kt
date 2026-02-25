package com.digia.digiaui.framework.bottomsheet

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.DefaultVirtualWidgetRegistry
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.ToUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import resourceColor

/**
 * Data class representing a bottom sheet request
 */
data class BottomSheetRequest(
    val componentId: String,
    val args: JsonLike?,
    val backgroundColor: String?,
    val barrierColor: String?,
    val borderColor: String?,
    val borderWidth: Float?,
    val borderRadius: Any?,
    val maxHeightRatio: Float,
    val useSafeArea: Boolean,
    val onDismiss: ((Any?) -> Unit)?
)

/**
 * Manager for showing bottom sheets
 * 
 * Manages bottom sheet display state and provides a way to show/hide bottom sheets
 * from action processors.
 */
class BottomSheetManager {
    private val _currentRequest = MutableStateFlow<BottomSheetRequest?>(null)
    val currentRequest: StateFlow<BottomSheetRequest?> = _currentRequest.asStateFlow()

    /**
     * Show a bottom sheet with the specified component
     */
    fun show(
        componentId: String,
        args: JsonLike? = null,
        backgroundColor: String? = null,
        barrierColor: String? = null,
        borderColor: String? = null,
        borderWidth: Float? = null,
        borderRadius: Any? = null,
        maxHeightRatio: Float = 1f,
        useSafeArea: Boolean = true,
        onDismiss: ((Any?) -> Unit)? = null
    ) {
        _currentRequest.value = BottomSheetRequest(
            componentId = componentId,
            args = args,
            backgroundColor = backgroundColor,
            barrierColor = barrierColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            borderRadius = borderRadius,
            maxHeightRatio = maxHeightRatio,
            useSafeArea = useSafeArea,
            onDismiss = onDismiss
        )
    }

    /**
     * Dismiss the current bottom sheet
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
 * Composable that observes bottom sheet state and displays bottom sheets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun BottomSheetHost(
    bottomSheetManager: BottomSheetManager,
    resources: UIResources
) {
    val currentRequest by bottomSheetManager.currentRequest.collectAsState()



    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
//        confirmValueChange = { targetValue ->
//         targetValue != SheetValue.Hidden
//        }
    )


    LaunchedEffect(currentRequest) {
        if (currentRequest != null) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    currentRequest?.let { request ->
        val shape = ToUtils.borderRadius(request.borderRadius, or = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        val resolvedBorderColor = request.borderColor
            ?.let { token -> resolveColorToken(token, resources) }
        val resolvedBorderWidth = (request.borderWidth ?: 0f)

        val configuration = LocalConfiguration.current
        val maxHeight =
            configuration.screenHeightDp.dp * request.maxHeightRatio

        ModalBottomSheet(
            onDismissRequest = bottomSheetManager::dismiss,
            sheetState = sheetState,
            shape = shape,
            containerColor = resolveColorToken(request.backgroundColor, resources)
                ?: Color.White,
            scrimColor = resolveColorToken(request.barrierColor, resources) ?: Color.Black.copy(alpha = 0.3f),
            dragHandle = { null},
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                   .heightIn(max = maxHeight)   // ✅ HERE
                    .wrapContentHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                // Only consume vertical drags (sheet dragging)
                                change.consume()
                            },
                            onDragStart = { offset ->
                                // Consume drag start too
                                // offset.consume()
                            }
                        )
                    }
                    .then(
                        if (resolvedBorderColor != null && resolvedBorderWidth > 0f) {
                            Modifier.border(resolvedBorderWidth.dp, resolvedBorderColor, shape)
                        } else Modifier
                    )
                    .then(
                        if (request.useSafeArea) {
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        } else Modifier
                    )
            ) {
                DUIFactory.getInstance().CreateComponent(
                    componentId = request.componentId,
                    args = request.args
                )
            }
        }

    }
}

/**
 * Parse color string to Compose Color
 * Supports hex colors (#RRGGBB, #AARRGGBB) and named colors
 */
private fun resolveColorToken(token: String?, resources: UIResources,): Color? {
    if (token.isNullOrBlank()) return null
    // Supports:
    // - resource tokens (looked up in UIResources)
    // - hex (#RRGGBB/#AARRGGBB)
    // - rgba(...) (via ColorUtil)
    return resourceColor(token, resources)
}
