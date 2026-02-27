package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike
import com.gsrathoreniks.scratchify.api.Scratchify
import com.gsrathoreniks.scratchify.api.ScratchifyController
import com.gsrathoreniks.scratchify.api.config.ScratchifyBrushConfig
import com.gsrathoreniks.scratchify.api.config.ScratchifyConfig
import com.gsrathoreniks.scratchify.api.config.ScratchifyHapticConfig
import com.gsrathoreniks.scratchify.api.config.ScratchifyAnimationConfig
import com.gsrathoreniks.scratchify.api.config.BrushShape
import com.gsrathoreniks.scratchify.api.config.RevealAnimationType
import com.gsrathoreniks.scratchify.api.config.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.graphics.toColorInt
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.asSafe
import com.digia.digiaui.utils.asSafe
import com.digia.digiaui.framework.utils.toDp
import com.digia.digiaui.framework.utils.toPercentFraction

data class ScratchCardProps(
    val height: String? = null, 
    val width: String? = null,
    val revealFullAtPercent: ExprOr<Float>? = null,
    val isScratchingEnabled: ExprOr<Boolean>? = null,
    val gridResolution: ExprOr<Int>? = null,
    val enableTapToScratch: ExprOr<Boolean>? = null,
    val brushSize: ExprOr<String>? = null, // Size in dp as string (e.g., "20dp")
    val brushColor: ExprOr<String>? = null, // Color as hex string
    val brushOpacity: ExprOr<Float>? = null,
    val brushShape: ExprOr<String>? = null, // "circle", "square", "star", "heart", "diamond"
    val enableHapticFeedback: ExprOr<Boolean>? = null,
    val revealAnimationType: ExprOr<String>? = null, // "fade", "scale", "slide_up", "slide_down", "slide_left", "slide_right", "bounce", "zoom_out", "none"
    val animationDurationMs: ExprOr<Int>? = null,
    val enableProgressAnimation: ExprOr<Boolean>? = null,
    val onScratchComplete: ActionFlow? = null
){
    companion object {
        fun fromJson(json: JsonLike): ScratchCardProps {
            return ScratchCardProps(
                height = asSafe<String>(json["height"]),
                width = asSafe<String>(json["width"]),
                revealFullAtPercent = ExprOr.fromValue(json["revealFullAtPercent"]),
                isScratchingEnabled = ExprOr.fromValue(json["isScratchingEnabled"]),
                gridResolution = ExprOr.fromValue(json["gridResolution"]),
                enableTapToScratch = ExprOr.fromValue(json["enableTapToScratch"]),
                brushSize = ExprOr.fromValue(json["brushSize"]),
                brushColor = ExprOr.fromValue(json["brushColor"]),
                brushOpacity = ExprOr.fromValue(json["brushOpacity"]),
                brushShape = ExprOr.fromValue(json["brushShape"]),
                enableHapticFeedback = ExprOr.fromValue(json["enableHapticFeedback"]),
                revealAnimationType = ExprOr.fromValue(json["revealAnimationType"]),
                animationDurationMs = ExprOr.fromValue(json["animationDurationMs"]),
                enableProgressAnimation = ExprOr.fromValue(json["enableProgressAnimation"]),
                onScratchComplete = asSafe<JsonLike>(json["onScratchComplete"])?.let {
                    ActionFlow.fromJson(it)
                }
            )
        }
    }
}

class VWScratchCard(
    refName: String? = null,
    commonProps: CommonProps? = null,
    parent: VirtualNode? = null,
    parentProps: Props? = null,
    props: ScratchCardProps,
    slots: ((VirtualCompositeNode<ScratchCardProps>) -> Map<String, List<VirtualNode>>?)? = null
) : VirtualCompositeNode<ScratchCardProps>(
    props = props,
    commonProps = commonProps,
    parent = parent,
    refName = refName,
    parentProps = parentProps,
    _slots = slots
) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val base = slot("base")
        val overlay = slot("overlay")

        if(base==null || overlay==null) {
            Empty()
            return
        }

        val context = LocalContext.current
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current
val revealFullAtPercent=payload.evalExpr(props.revealFullAtPercent)?.let {
        it-> it/100f
} ?: 0.75f

        val hasCompleted = remember { mutableStateOf(false) }

        val isBaseRevealed = remember { mutableStateOf(false) }


        val config = ScratchifyConfig(
            revealFullAtPercent = revealFullAtPercent,
            isScratchingEnabled = payload.evalExpr(props.isScratchingEnabled) ?: true,
            gridResolution = payload.evalExpr(props.gridResolution) ?: 150,
            enableTapToScratch = payload.evalExpr(props.enableTapToScratch) ?: true,
            brushConfig = createBrushConfig(payload),
            hapticConfig = createHapticConfig(payload),
            animationConfig = createAnimationConfig(payload)
        )




        val controller = remember {
            ScratchifyController()
        }


        LaunchedEffect(controller.scratchProgress) {
            if(controller.scratchProgress>0f &&
                !isBaseRevealed.value
            ){
                isBaseRevealed.value=true
            }
            if(controller.scratchProgress>=revealFullAtPercent&&
                !hasCompleted.value
            ){
                hasCompleted.value=true
                // Trigger onScratchComplete action
                props.onScratchComplete?.let { actionFlow ->
                    payload.executeAction(
                        context = context,
                        actionFlow = actionFlow,
                        actionExecutor = actionExecutor,
                        stateContext = stateContext,
                        resourcesProvider = resources,
                        incomingScopeContext = null,
                    )
                }
            }
        }

        val modifier = Modifier.buildModifier(payload).let { baseModifier ->
            var mod = baseModifier
            // Handle width
            props.width?.let { widthValue ->
                val widthPercent = widthValue.toPercentFraction()
                if (widthPercent != null) {
                    mod = mod.fillMaxWidth(widthPercent)
                } else {
                    widthValue.toDp()?.let { dpValue ->
                        mod = mod.width(dpValue)
                    }
                }
            }
            // Handle height
            props.height?.let { heightValue ->
                val heightPercent = heightValue.toPercentFraction()
                if (heightPercent != null) {
                    mod = mod.fillMaxHeight(heightPercent)
                } else {
                    heightValue.toDp()?.let { dpValue ->
                        mod = mod.height(dpValue)
                    }
                }
            }
            mod
        }

        Scratchify(
            modifier = modifier,
           config = config,
            controller = controller,
            contentToReveal = {
                // Base content (second child)
                if (isBaseRevealed.value) {
                    base.ToWidget(payload)
                }
            },
            overlayContent = {
                // Overlay content (first child)
                overlay.ToWidget(payload)
            },


        )
    }

    private fun createBrushConfig(payload: RenderPayload): ScratchifyBrushConfig {
        val brushSizeStr = payload.evalExpr(props.brushSize) ?: "4dp"
        val brushSize = parseDpValue(brushSizeStr) ?: 4.dp

        val brushColorStr = payload.evalExpr(props.brushColor)
        val brushColor = if (brushColorStr != null) {
            try {
                Color(brushColorStr.toColorInt())
            } catch (e: Exception) {
                Color.Cyan
            }
        } else {
            Color.Cyan
        }

        val brushShapeStr = payload.evalExpr(props.brushShape) ?: "circle"
        val brushShape = when (brushShapeStr.lowercase()) {
            "circle" -> BrushShape.Circle
            "square" -> BrushShape.Square
            "star" -> BrushShape.Star(points = 5)
            "heart" -> BrushShape.Heart
            "diamond" -> BrushShape.Diamond
            else -> BrushShape.Circle
        }

        return ScratchifyBrushConfig(
            brushSize = brushSize,
            brushColor = brushColor,
            opacity = payload.evalExpr(props.brushOpacity) ?: 1f,
            brushShape = brushShape
        )
    }

    private fun createHapticConfig(payload: RenderPayload): ScratchifyHapticConfig {
        val enableHaptic = payload.evalExpr(props.enableHapticFeedback) ?: true

        return ScratchifyHapticConfig(
            isEnabled = enableHaptic,
            onScratchStarted = HapticFeedbackType.LIGHT,
            onScratchProgress = HapticFeedbackType.NONE,
            onScratchCompleted = HapticFeedbackType.SUCCESS,
            progressHapticInterval = 0.25f
        )
    }

    private fun createAnimationConfig(payload: RenderPayload): ScratchifyAnimationConfig {
        val animationTypeStr = payload.evalExpr(props.revealAnimationType) ?: "fade"
        val revealAnimationType = when (animationTypeStr.lowercase().replace("_", "")) {
            "fade" -> RevealAnimationType.FADE
            "scale" -> RevealAnimationType.SCALE
            "slideup" -> RevealAnimationType.SLIDE_UP
            "slidedown" -> RevealAnimationType.SLIDE_DOWN
            "slideleft" -> RevealAnimationType.SLIDE_LEFT
            "slideright" -> RevealAnimationType.SLIDE_RIGHT
            "bounce" -> RevealAnimationType.BOUNCE
            "zoomout" -> RevealAnimationType.ZOOM_OUT
            "none" -> RevealAnimationType.NONE
            else -> RevealAnimationType.FADE
        }

        return ScratchifyAnimationConfig(
            revealAnimationType = revealAnimationType,
            animationDurationMs = payload.evalExpr(props.animationDurationMs) ?: 500,
            enableProgressAnimation = payload.evalExpr(props.enableProgressAnimation) ?: true
        )
    }

    private fun parseDpValue(value: String): Dp? {
        return try {
            if (value.endsWith("dp")) {
                val numStr = value.removeSuffix("dp")
                numStr.toFloat().dp
            } else {
                value.toFloat().dp
            }
        } catch (e: Exception) {
            null
        }
    }
}

fun scratchCardBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {
    return VWScratchCard(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = ScratchCardProps.fromJson(data.props.value),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        }
    )
}