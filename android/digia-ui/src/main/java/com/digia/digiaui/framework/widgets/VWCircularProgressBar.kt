package com.digia.digiaui.framework.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

class VWCircularProgressBar(
        props: Props,
        commonProps: CommonProps?,
        parentProps: Props?,
        parent: VirtualNode?,
        refName: String?
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
        val type = props.getString("type") ?: "indeterminate"
        val size = props.getDouble("size")?.dp ?: 40.dp
        val thickness = props.getDouble("thickness")?.dp ?: 4.dp
        val indicatorColor = payload.evalColor(props.get("indicatorColor")) ?: Color.Blue
        val bgColor = payload.evalColor(props.get("bgColor")) ?: Color.Transparent

        when (type) {
            "indeterminate" -> {
                CircularProgressIndicator(
                        modifier = Modifier.size(size),
                        color = indicatorColor,
                        strokeWidth = thickness,
                        trackColor = bgColor
                )
            }
            else -> {
                DeterminateProgress(
                        size = size,
                        thickness = thickness,
                        indicatorColor = indicatorColor,
                        bgColor = bgColor,
                        payload = payload
                )
            }
        }
    }

    @Composable
    private fun DeterminateProgress(
            size: androidx.compose.ui.unit.Dp,
            thickness: androidx.compose.ui.unit.Dp,
            indicatorColor: Color,
            bgColor: Color,
            payload: RenderPayload
    ) {
        val progressValue = payload.eval<Double>(props.get("progressValue"))
        val animationEnabled = props.getBool("animation") ?: false
        val animateFromLastPercent = props.getBool("animateFromLastPercent") ?: false

        val targetProgress = ((progressValue ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)

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

        CircularProgressIndicator(
                progress = { progressAnimatable.value },
                modifier = Modifier.size(size),
                color = indicatorColor,
                strokeWidth = thickness,
                trackColor = bgColor,
                strokeCap = StrokeCap.Round
        )
    }
}

fun circularProgressBarBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWCircularProgressBar(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps ?: Props.empty(),
            props = data.props,
    )
}
