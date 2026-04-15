package com.digia.digiaui.framework.bottomsheet

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.ActionProvider
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.ToUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import resourceColor

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

class BottomSheetManager {
    private val _currentRequest = MutableStateFlow<BottomSheetRequest?>(null)
    val currentRequest: StateFlow<BottomSheetRequest?> = _currentRequest.asStateFlow()

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

    fun dismiss(result: Any? = null) {
        val request = _currentRequest.value
        _currentRequest.value = null
        request?.onDismiss?.invoke(result)
    }

    fun clear() {
        _currentRequest.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun BottomSheetHost(
    bottomSheetManager: BottomSheetManager,
    resources: UIResources
) {
    val currentRequest by bottomSheetManager.currentRequest.collectAsState()

    currentRequest?.let { request ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val shape = ToUtils.borderRadius(
            request.borderRadius,
            or = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        )
        val resolvedBorderColor = request.borderColor
            ?.let { resourceColor(it, resources) }
        val resolvedBorderWidth = request.borderWidth ?: 0f
        val hasBorder = resolvedBorderColor != null && resolvedBorderWidth > 0f

        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val maxHeight = screenHeight * request.maxHeightRatio
        val density = LocalDensity.current

        ModalBottomSheet(
            onDismissRequest = bottomSheetManager::dismiss,
            sheetState = sheetState,
            shape = shape,
            containerColor = request.backgroundColor?.let { resourceColor(it, resources) }
                ?: BottomSheetDefaults.ContainerColor,
            scrimColor = request.barrierColor?.let { resourceColor(it, resources) }
                ?: Color.Black.copy(alpha = 0.54f),
            dragHandle = null,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(max = maxHeight)
                    .then(
                        if (request.useSafeArea)
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        else Modifier
                    )
                    .then(
                        if (hasBorder) {
                            val borderDensity = density
                            Modifier.drawWithContent {
                                drawContent()

                                val topStartPx =
                                    shape.topStart.toPx(size, borderDensity)
                                val topEndPx =
                                    shape.topEnd.toPx(size, borderDensity)
                                val strokePx =
                                    with(borderDensity) { resolvedBorderWidth.dp.toPx() }
                                val half = strokePx / 2f
                                val bottomY = size.height + half

                                val path = Path().apply {
                                    moveTo(half, bottomY)
                                    lineTo(half, topStartPx)
                                    arcTo(
                                        rect = Rect(
                                            half,
                                            half,
                                            topStartPx * 2f - half,
                                            topStartPx * 2f - half
                                        ),
                                        startAngleDegrees = 180f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(size.width - topEndPx, half)
                                    arcTo(
                                        rect = Rect(
                                            size.width - topEndPx * 2f + half,
                                            half,
                                            size.width - half,
                                            topEndPx * 2f - half
                                        ),
                                        startAngleDegrees = 270f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(size.width - half, bottomY)
                                }
                                drawPath(
                                    path = path,
                                    color = resolvedBorderColor!!,
                                    style = Stroke(width = strokePx)
                                )
                            }
                        } else Modifier
                    )
            ) {
                val borderPadding = if (hasBorder) resolvedBorderWidth.dp else 0.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(
                            start = borderPadding,
                            top = borderPadding,
                            end = borderPadding
                        )
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, _ -> change.consume() }
                        }
                ) {
                    ActionProvider(actionExecutor = ActionExecutor()) {
                        DUIFactory.getInstance().CreateComponent(
                            componentId = request.componentId,
                            args = request.args
                        )
                    }
                }
            }
        }
    }
}

