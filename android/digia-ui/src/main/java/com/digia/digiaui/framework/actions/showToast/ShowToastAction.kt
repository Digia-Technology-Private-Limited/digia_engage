package com.digia.digiaui.framework.actions.showToast

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.actions.base.ActionType
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.utils.ToUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Show toast action */
data class ShowToastAction(
        override var actionId: ActionId? = null,
        override var disableActionIf: ExprOr<Boolean>? = null,
        val message: ExprOr<String>?,
        val duration: ExprOr<Int>? = null,
        val style: JsonLike? = null
) : Action {
    override val actionType = ActionType.SHOW_TOAST

    override fun toJson(): JsonLike =
            mapOf(
                    "type" to actionType.value,
                    "message" to message?.toJson(),
                    "duration" to duration?.toJson(),
                    "style" to style,
            )

    companion object {
        fun fromJson(json: JsonLike): ShowToastAction {
            return ShowToastAction(
                    message = ExprOr.fromValue(json["message"]),
                    duration = ExprOr.fromValue(json["duration"]),
                    style = json["style"] as? JsonLike
            )
        }
    }
}



/** Processor for show toast action */
class ShowToastProcessor : ActionProcessor<ShowToastAction>() {
    override suspend fun execute(
        context: Context,
        action: ShowToastAction,
        scopeContext: ScopeContext?,
        stateContext: com.digia.digiaui.framework.state.StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ) {

        // Evaluate message
        val message = action.message?.evaluate(scopeContext) ?: ""

        // Evaluate duration
        val duration = action.duration?.evaluate(scopeContext) ?: 2

        // Parse style properties
        val style: JsonLike = action.style ?: emptyMap()

        val bgColor = ExprOr.fromJson<String>(style["bgColor"])
            ?.evaluate<String>(scopeContext)
            ?.let { ColorUtil.tryFromHexString(it) }
            ?: Color.Black

        val borderRadius = ToUtils.borderRadius(
            style["borderRadius"] ?: "12, 12, 12, 12",
            or = RoundedCornerShape(12.dp)
        )

        val textStyle =
       style["textStyle"] as? Map<String, Any?>


        val height = NumUtil.toDouble(style["height"])?.dp
        val width = NumUtil.toDouble(style["width"])?.dp

        val padding = ToUtils.edgeInsets(
            style["padding"] ?: "24, 12, 24, 12",
            or = androidx.compose.foundation.layout.PaddingValues(24.dp, 12.dp)
        )
        val margin = ToUtils.edgeInsets(style["margin"])
        val alignment = toAlignment(style["alignment"] as? String)

        val scope = CoroutineScope(Dispatchers.Main)

        scope.launch {
            DUISnackbarManager.showToast(
                visuals = DUIToastVisuals(
                    messageText = message,
                    bgColor = bgColor,
                    textStyle = TextStyle(color = Color.White),
                    shape = borderRadius,
                    padding = padding,
                    margin = margin,
                    width = width,
                    height = height,
                    alignment = alignment,
                    duration =
                        if (duration <= 0) SnackbarDuration.Indefinite
                        else SnackbarDuration.Short,
                    scopeContext = scopeContext,
                    resourceProvider = resourcesProvider
                ),
                autoDismissMs =
                    if (duration <= 0) null else duration * 1000L
            )
        }


    }

    private fun toAlignment(value: String?): Alignment {
        return when (value?.lowercase()) {
            "center" -> Alignment.Center
            "topleft", "top_left" -> Alignment.TopStart
            "topcenter", "top_center" -> Alignment.TopCenter
            "topright", "top_right" -> Alignment.TopEnd
            "centerleft", "center_left" -> Alignment.CenterStart
            "centerright", "center_right" -> Alignment.CenterEnd
            "bottomleft", "bottom_left" -> Alignment.BottomStart
            "bottomcenter", "bottom_center" -> Alignment.BottomCenter
            "bottomright", "bottom_right" -> Alignment.BottomEnd
            else -> Alignment.Center
        }
    }
}


object DUISnackbarManager {
    val hostState = SnackbarHostState()

    fun showToast(visuals: DUIToastVisuals, autoDismissMs: Long?) {
        CoroutineScope(Dispatchers.Main).launch {

            launch {
                hostState.showSnackbar(visuals = visuals)
            }

         launch {
             if (autoDismissMs != null) {
                 delay(autoDismissMs)
                 hostState.currentSnackbarData?.dismiss()
             }
         }
        }
    }
}

data class DUIToastVisuals(
    val messageText: String,
    val bgColor: Color,
    val textStyle: TextStyle,
    val shape: Shape,
    val padding: PaddingValues,

    // 🔥 NEW (now actually used)
    val margin: PaddingValues? = null,
    val width: Dp? = null,
    val height: Dp? = null,
    val alignment: Alignment? = Alignment.BottomCenter,

    override val duration: SnackbarDuration = SnackbarDuration.Indefinite,
    val scopeContext: ScopeContext?,
    val resourceProvider: UIResources?,
) : SnackbarVisuals {

    override val message: String get() = messageText
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
}




@Composable
fun DUISnackbarHost() {
    SnackbarHost(
        hostState = DUISnackbarManager.hostState,
        modifier = Modifier.fillMaxSize()
    ) { snackbarData ->

        val visuals = snackbarData.visuals as? DUIToastVisuals

        if (visuals != null) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(visuals.margin ?: PaddingValues(16.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    color = visuals.bgColor,
                    shape = visuals.shape,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .then(
                            if (visuals.width != null || visuals.height != null) {
                                Modifier.size(
                                    width = visuals.width ?: Dp.Unspecified,
                                    height = visuals.height ?: Dp.Unspecified
                                )
                            } else Modifier
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(visuals.padding), contentAlignment = visuals.alignment ?: Alignment.Center,) {
                        Text(
                            text = visuals.messageText,
                            style = visuals.textStyle,
//                            modifier = Modifier.align(alignment = visuals.alignment ?: Alignment.Center).padding(visuals.padding)
                        )
                    }
                }
            }

        } else {
            Snackbar(snackbarData)
        }
    }
}
