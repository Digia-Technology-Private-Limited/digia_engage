package com.digia.engage.framework.widgets

import androidx.compose.runtime.Composable
import com.digia.engage.framework.RenderPayload
import com.digia.engage.framework.VirtualWidgetRegistry
import com.digia.engage.framework.base.VirtualCompositeNode
import com.digia.engage.framework.base.VirtualNode
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.models.CommonProps
import com.digia.engage.framework.models.ExprOr
import com.digia.engage.framework.models.Props
import com.digia.engage.framework.models.VWNodeData
import com.digia.engage.framework.registerAllChildern
import com.digia.engage.framework.utils.JsonLike


class ConditionalItemProps(
    val condition: ExprOr<Boolean>?
) {

    companion object {
        fun fromJson(json: JsonLike): ConditionalItemProps {
            return ConditionalItemProps(
                condition = ExprOr.fromJson<Boolean>(json["condition"])
            )
        }
    }
}


class VWConditionItem(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: ConditionalItemProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<ConditionalItemProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<ConditionalItemProps>(
props = props,
commonProps = commonProps,
parentProps= parentProps,
parent = parent,
refName = refName,
_slots = slots
) {

    fun evaluate(scopeContext: ScopeContext?): Boolean {
        return props.condition?.evaluate(scopeContext) ?: false
    }

    @Composable
    override fun Render(payload: RenderPayload) {
        child?.ToWidget(payload) ?: Empty()
    }
}


/** Builder function for ConditionalItem widget */
fun conditionalItemBuilder(data: VWNodeData, parent: VirtualNode?,registry: VirtualWidgetRegistry): VirtualNode {
    // Get the first child from childGroups as the template
    val childrenData = data.childGroups?.mapValues { (_, childrenData) ->
        childrenData.map { data ->
            registry.createWidget(data, parent)
        }
    }

    return VWConditionItem(
        refName = data.refName,
        commonProps = data.commonProps,
        parent= parent,
        parentProps = data.parentProps,
        props = ConditionalItemProps.fromJson(data.props.value),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )

}
