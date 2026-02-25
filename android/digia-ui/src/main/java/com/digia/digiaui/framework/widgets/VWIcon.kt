package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.color
import com.digia.digiaui.framework.components.dui_icons.packs.MaterialIcons
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.NumUtil

class VWIcon(
        refName: String? = null,
        commonProps: CommonProps? = null,
        props: VWIconProps,
        parent: VirtualNode? = null,
        parentProps: Props? = null
) :
        VirtualLeafNode<VWIconProps>(
                props = props,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName
        ) {
    @Composable
    override fun Render(payload: RenderPayload) {
        val iconDataMap = props.iconData as? Map<*, *>
        val pack = iconDataMap?.get("pack") as? String

        var imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null

        if (pack == "material") {
            val key = iconDataMap["key"] as? String
            if (key != null) {
                imageVector = MaterialIcons.getMaterialIcon(key)
            }
        }

        if (imageVector == null) return

        val sizeVal = payload.evalExpr(props.size)
        // Ensure proper unit linkage. .dp extension works on Float.
        val size = NumUtil.toDouble(sizeVal)?.toFloat() ?: 24f

        val colorStr = payload.evalExpr(props.color)
        val tint =
                if (colorStr != null) {
                    payload.color(colorStr) ?: LocalContentColor.current
                } else {
                    LocalContentColor.current
                }

        Icon(
                imageVector = imageVector,
                contentDescription = null,
                modifier = Modifier.buildModifier(payload).size(size.dp),
                tint = tint
        )
    }
}

data class VWIconProps(
        val iconData: Any? = null,
        val size: ExprOr<Double>? = null,
        val color: ExprOr<String>? = null
) {
    companion object {
        fun fromJson(json: JsonLike): VWIconProps {
            return VWIconProps(
                    iconData = json["iconData"],
                    size = ExprOr.fromJson(json["iconSize"]),
                    color = ExprOr.fromJson(json["iconColor"])
            )
        }
    }
}

fun iconBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWIcon(
            refName = data.refName,
            commonProps = data.commonProps,
            props = VWIconProps.fromJson(data.props.value),
            parent = parent,
            parentProps = data.parentProps
    )
}
