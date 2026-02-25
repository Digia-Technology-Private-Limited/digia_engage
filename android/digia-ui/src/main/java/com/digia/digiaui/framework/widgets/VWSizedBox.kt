package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil

class SizedBoxProps(val height: Double?, val width: Double?) {

    companion object {
        fun fromJson(json: JsonLike): SizedBoxProps {
            return SizedBoxProps(
                    height = NumUtil.toDouble(json["height"]),
                    width = NumUtil.toDouble(json["width"])
            )
        }
    }
}

class VWSizedBox(
        refName: String?,
        commonProps: CommonProps?,
        parent: VirtualNode?,
        parentProps: Props? = null,
        props: SizedBoxProps
) :
        VirtualLeafNode<SizedBoxProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {

        val widthDp = props.width?.toDp()
        val heightDp = props.height?.toDp()

        FixedSpace(width = widthDp, height = heightDp)
    }

    private fun Number.toDp(): Dp = this.toFloat().dp
}

fun sizedBoxBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWSizedBox(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = SizedBoxProps.fromJson(data.props.value)
    )
}

@Composable
fun FixedSpace(width: Dp? = null, height: Dp? = null) {
    Spacer(
        modifier = Modifier.layout { measurable, constraints ->
            val w = width?.roundToPx() ?: constraints.minWidth
            val h = height?.roundToPx() ?: constraints.minHeight
            
            layout(w, h) {
                // No placement needed for a spacer
            }
        }
    )
}
