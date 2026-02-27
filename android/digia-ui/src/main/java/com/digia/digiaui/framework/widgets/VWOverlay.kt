package com.digia.digiaui.framework.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.toComposeAlignment
import com.digia.digiaui.framework.widgets.overlay.Overlay

// Overlay Props
data class OverlayProps(
    val childAlignment: ExprOr<String>? = null,
    val popupAlignment: ExprOr<String>? = null,
    val offsetXAxis: ExprOr<Double>? = null,
    val offsetYAxis: ExprOr<Double>? = null,
    val dismissOnTapOutside: ExprOr<Boolean>? = null,
    val dismissOnTapInside: ExprOr<Boolean>? = null,
) {
    companion object {
        fun fromJson(json: JsonLike): OverlayProps {
            val offset = json["offset"] as? Map<*, *>
            return OverlayProps(
                childAlignment = ExprOr.fromValue(json["childAlignment"]),
                popupAlignment = ExprOr.fromValue(json["popupAlignment"]),
                offsetXAxis = ExprOr.fromValue(offset?.get("xAxis")),
                offsetYAxis = ExprOr.fromValue(offset?.get("yAxis")),
                dismissOnTapOutside = ExprOr.fromValue(json["dismissOnTapOutside"]),
                dismissOnTapInside = ExprOr.fromValue(json["dismissOnTapInside"]),
            )
        }
    }
}

// VWOverlay Class
class VWOverlay(
    props: OverlayProps,
    commonProps: CommonProps?,
    parentProps: Props?,
    parent: VirtualNode?,
    slots: ((VirtualCompositeNode<OverlayProps>) -> Map<String, List<VirtualNode>>?)? = null,
    refName: String? = null
) : VirtualCompositeNode<OverlayProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val childWidget = slot("childWidget")
        val popupWidget = slot("popupWidget")

        if (childWidget == null) {
            return
        }

        val childAlignment = payload.evalExpr(props.childAlignment)?.toComposeAlignment() ?: Alignment.TopStart
        val popupAlignment = payload.evalExpr(props.popupAlignment)?.toComposeAlignment() ?: Alignment.TopStart

        val offsetX = payload.evalExpr(props.offsetXAxis)?.toInt() ?: 0
        val offsetY = payload.evalExpr(props.offsetYAxis)?.toInt() ?: 0
        val offset = IntOffset(offsetX, offsetY)

        val dismissOnTapOutside = payload.evalExpr(props.dismissOnTapOutside) ?: true
        val dismissOnTapInside = payload.evalExpr(props.dismissOnTapInside) ?: false

        Overlay(
            showOnTap = true,
            dismissOnTapOutside = dismissOnTapOutside,
            dismissOnTapInside = dismissOnTapInside,
            offset = offset,
            childAlignment = childAlignment,
            popupAlignment = popupAlignment,
            popup = { controller ->
                popupWidget?.ToWidget(payload)
            },
            content = {
                childWidget.ToWidget(payload)
            }
        )
    }
}

// Builder function
fun overlayBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {
    return VWOverlay(
        props = OverlayProps.fromJson(data.props.value),
        commonProps = data.commonProps,
        parentProps = data.parentProps,
        parent = parent,
        refName = data.refName,
        slots = { self ->
            registerAllChildern(data.childGroups, self, registry)
        }
    )
}