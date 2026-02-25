package com.digia.digiaui.framework.widgets

import androidx.compose.runtime.Composable
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern

class VWConditionalBuilder(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: Props,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<Props>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<Props>(
props = props,
commonProps = commonProps,
parentProps= parentProps,
parent = parent,
refName = refName,
_slots = slots
) {

    fun getEvalChild(payload: RenderPayload): VirtualNode? {
        val conditionalItemChildren =
            children.filterIsInstance<VWConditionItem>().orEmpty()

        if (conditionalItemChildren.isEmpty()) {
            return null
        }

        return conditionalItemChildren
            .firstOrNull { it.evaluate(payload.scopeContext) }
            ?.child
    }

    @Composable
    override fun Render(payload: RenderPayload) {
        val widget = getEvalChild(payload)
        widget?.ToWidget(payload) ?: Empty()
    }
}




/** Builder function for ConditionalBUilder widget */
fun conditionalBuilder(data: VWNodeData, parent: VirtualNode?,registry: VirtualWidgetRegistry): VirtualNode {
    // Get the first child from childGroups as the template
    val childrenData = data.childGroups?.mapValues { (_, childrenData) ->
        childrenData.map { data ->
            registry.createWidget(data, parent)
        }
    }

    return VWConditionalBuilder(
        refName = data.refName,
        commonProps = data.commonProps,
        parent= parent,
        parentProps = data.parentProps,
        props = data.props,
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )

}
