package com.digia.digiaui.framework.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData

class VWLinearProgressBar(
        props: Props,
        commonProps: CommonProps?,
        parentProps: Props?,
        parent: VirtualNode?,
        refName: String? = null
) :
        VirtualLeafNode<Props>(
                props = props,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val isReverse = payload.eval<Boolean>(props.get("isReverse")) ?: false
        val rotationModifier = if (isReverse) Modifier.rotate(180f) else Modifier

        Box(modifier = rotationModifier) { ProgressContent(payload = payload) }
    }

    @Composable
    private fun ProgressContent(payload: RenderPayload) {
        val type = props.getString("type") ?: "indeterminate"
        val width = props.getDouble("width") ?: 200.0
        val thickness = props.getDouble("thickness") ?: 10.0
        val borderRadius = props.getDouble("borderRadius") ?: 4.0
        val indicatorColor = payload.evalColor(props.get("indicatorColor")) ?: Color(0xFF2196F3)
        val bgColor = payload.evalColor(props.get("bgColor")) ?: Color(0xFFE0E0E0)

        val modifier =
                Modifier.width(width.dp)
                        .height(thickness.dp)
                        .clip(RoundedCornerShape(borderRadius.dp))

        if (type == "indeterminate") {
            LinearProgressIndicator(
                    modifier = modifier,
                    color = indicatorColor,
                    trackColor = bgColor,
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp
            )
        } else {
            DeterminateProgress(
                    modifier = modifier,
                    indicatorColor = indicatorColor,
                    bgColor = bgColor,
                    payload = payload
            )
        }
    }

    @Composable
    private fun DeterminateProgress(
            modifier: Modifier,
            indicatorColor: Color,
            bgColor: Color,
            payload: RenderPayload
    ) {
        val progressValue = payload.eval<Double>(props.get("progressValue"))
        val animationEnabled = props.getBool("animation") ?: false
        val animateFromLastPercent = props.getBool("animateFromLastPercent") ?: false

        val targetProgress = ((progressValue ?: 50.0) / 100.0).toFloat().coerceIn(0f, 1f)

        val progressAnimatable = remember { Animatable(0f) }

        LaunchedEffect(targetProgress, animationEnabled, animateFromLastPercent) {
            if (animationEnabled) {
                if (!animateFromLastPercent) {
                    progressAnimatable.snapTo(0f)
                }
                progressAnimatable.animateTo(
                        targetValue = targetProgress,
                        animationSpec = tween(durationMillis = 500)
                )
            } else {
                progressAnimatable.snapTo(targetProgress)
            }
        }

        LinearProgressIndicator(
                progress = { progressAnimatable.value },
                modifier = modifier,
                color = indicatorColor,
                trackColor = bgColor,
                gapSize = 0.dp,
                strokeCap = StrokeCap.Butt,
                drawStopIndicator = {}
        )
    }
}

fun linearProgressBarBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWLinearProgressBar(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = data.props,
    )
}
