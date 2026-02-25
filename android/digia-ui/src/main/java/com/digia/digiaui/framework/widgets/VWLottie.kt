package com.digia.digiaui.framework.widgets


import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike
import androidx.compose.material.icons.outlined.Info
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.init.DigiaUIManager
import LocalUIResources

/** Lottie widget properties */
data class LottieProps(

    val lottiePath: ExprOr<String>? = null, // Fallback for backward compatibility
    val height: ExprOr<Double>? = null,
    val width: ExprOr<Double>? = null,
    val alignment: ExprOr<String>? = null,
    val fit: ExprOr<String>? = null,
    val animate: ExprOr<Boolean>? = null,
    val animationType: ExprOr<String>? = null,
    val frameRate: ExprOr<Double>? = null,
    val onComplete: ActionFlow? = null
) {
    companion object {
        fun fromJson(json: JsonLike): LottieProps {

            val srcMap = json["src"] as? Map<*, *>
            val rawPath = srcMap?.get("lottiePath") ?: json["lottiePath"]
            return LottieProps(

                lottiePath = ExprOr.fromValue(rawPath),
                height = ExprOr.fromValue(json["height"]),
                width = ExprOr.fromValue(json["width"]),
                alignment = ExprOr.fromValue(json["alignment"]),
                fit = ExprOr.fromValue(json["fit"]),
                animate = ExprOr.fromValue(json["animate"]),
                animationType = ExprOr.fromValue(json["animationType"]),
                frameRate = ExprOr.fromValue(json["frameRate"]),
                onComplete = (json["onComplete"] as? JsonLike)?.let { ActionFlow.fromJson(it) },
                )
        }
    }
}

/** Virtual Lottie widget for rendering Lottie animations */
class VWLottie(
    refName: String?,
    commonProps: CommonProps?,
    parent: VirtualNode?,
    parentProps: Props? = null,
    props: LottieProps
) : VirtualLeafNode<LottieProps>(
    props = props,
    commonProps = commonProps,
    parent = parent,
    refName = refName,
    parentProps = parentProps
) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current

        // Extract lottiePath from src or fallback to lottiePath prop
        val lottiePath =
            payload.evalExpr(props.lottiePath)


        // Evaluate other properties
        val height = payload.evalExpr(props.height)
        val width = payload.evalExpr(props.width)
        val alignmentStr = payload.evalExpr(props.alignment)
        val fitStr = payload.evalExpr(props.fit)
        val animate = payload.evalExpr(props.animate) ?: true
        val animationTypeStr = payload.evalExpr(props.animationType) ?: "loop"
        val frameRateVal = payload.evalExpr(props.frameRate) ?: 60.0

        // Convert to Compose types
        val alignment = toAlignment(alignmentStr)
        val contentScale = toContentScale(fitStr)
        val (repeat, reverse) = getAnimationType(animationTypeStr)

        // Build modifier with size constraints
        var modifier = Modifier.buildModifier(payload)
        if (width != null || height != null) {
            val widthDp = width?.dp ?: 150.dp
            val heightDp = height?.dp ?: 150.dp
            modifier = modifier.size(width = widthDp, height = heightDp)
        }

        // Render lottie animation if path is valid
        if (lottiePath.isNullOrEmpty()) {
            // Show error placeholder similar to Flutter
            ErrorPlaceholder(modifier)
            return
        }

        RenderLottie(
            path = lottiePath,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            animate = animate,
            repeat = repeat,
            reverse = reverse,
            frameRate = frameRateVal,
            payload = payload,
            context = context,
            actionExecutor = actionExecutor,
            stateContext = stateContext
        )
    }

    @Composable
    private fun RenderLottie(
        path: String,
        modifier: Modifier,
        alignment: Alignment,
        contentScale: ContentScale,
        animate: Boolean,
        repeat: Boolean,
        reverse: Boolean,
        frameRate: Double,
        payload: RenderPayload,
        context: android.content.Context,
        actionExecutor: com.digia.digiaui.framework.actions.ActionExecutor,
        stateContext: com.digia.digiaui.framework.state.StateContext?,
    ) {
        val resources = LocalUIResources.current
        
        // Determine composition spec based on path type
        val compositionSpec = when {
            path.startsWith("http") || path.startsWith("https") -> {
                val host= DigiaUIManager.getInstance().host
                var resourceUrl=path
                if(host?.resourceProxyUrl!=null){
                    resourceUrl="${host.resourceProxyUrl}${java.net.URLEncoder.encode(path,"UTF-8")}"
                }
                // Network URL
                LottieCompositionSpec.Url(resourceUrl)
            }
            else -> {
                // Asset path
                LottieCompositionSpec.Asset(path)
            }
        }

        val composition by rememberLottieComposition(compositionSpec)

        // Configure animation based on properties
        val iterations = if (repeat) LottieConstants.IterateForever else 1
        val reverseOnRepeat = reverse && repeat

        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = iterations,
            isPlaying = animate,
            reverseOnRepeat = reverseOnRepeat,
            speed = frameRate.toFloat() / 60f // Normalize to default 60 fps
        )

        // Track completion for 'once' animation type
        var hasTriggeredOnComplete by remember { mutableStateOf(false) }
        LaunchedEffect(progress, composition) {
            if (!repeat && animate && composition != null) {
                // When animation completes (progress reaches 1.0 for non-repeating)
                if (progress >= 1.0f && !hasTriggeredOnComplete) {
                    hasTriggeredOnComplete = true
                    props.onComplete?.let {
                        payload.executeAction(
                            context = context,
                            actionFlow = it,
                            actionExecutor = actionExecutor,
                            stateContext = stateContext,
                            resourcesProvider =null,
                            incomingScopeContext = null,
                        )
                    }
                }
            }
        }

        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale
        )
    }

    @Composable
    private fun ErrorPlaceholder(modifier: Modifier) {
        // Simple error indication - could be enhanced
        androidx.compose.foundation.layout.Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                contentDescription = "Error loading Lottie",
                tint = androidx.compose.ui.graphics.Color.Red
            )
        }
    }

    /**
     * Converts animation type string to repeat and reverse flags
     * - "loop": repeat forever, no reverse
     * - "once": play once, no repeat
     * - "boomerang": repeat forever with reverse
     */
    private fun getAnimationType(animationType: String): Pair<Boolean, Boolean> {
        return when (animationType) {
            "boomerang" -> Pair(true, true)
            "once" -> Pair(false, false)
            "loop" -> Pair(true, false)
            else -> Pair(true, false)
        }
    }

    /**
     * Converts alignment string to Compose Alignment
     */
    private fun toAlignment(value: String?): Alignment {
        return when (value) {
            "topCenter" -> Alignment.TopCenter
            "topStart", "topLeft" -> Alignment.TopStart
            "topEnd", "topRight" -> Alignment.TopEnd
            "centerStart", "centerLeft" -> Alignment.CenterStart
            "center" -> Alignment.Center
            "centerEnd", "centerRight" -> Alignment.CenterEnd
            "bottomStart", "bottomLeft" -> Alignment.BottomStart
            "bottomCenter" -> Alignment.BottomCenter
            "bottomEnd", "bottomRight" -> Alignment.BottomEnd
            else -> Alignment.Center
        }
    }

    /**
     * Converts fit string to ContentScale
     */
    private fun toContentScale(value: String?): ContentScale {
        return when (value) {
            "contain", "fit" -> ContentScale.Fit
            "cover" -> ContentScale.Crop
            "fill", "fillBounds" -> ContentScale.FillBounds
            "fitWidth" -> ContentScale.FillWidth
            "fitHeight" -> ContentScale.FillHeight
            "none" -> ContentScale.None
            else -> ContentScale.Fit
        }
    }
}

/** Builder function for Lottie widget */
fun lottieBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {
    return VWLottie(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = LottieProps.fromJson(data.props.value)
    )
}