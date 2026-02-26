
package com.digia.digiaui.framework.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import kotlinx.coroutines.flow.StateFlow

class VWAnimatedBuilder(
	refName: String? = null,
	commonProps: CommonProps? = null,
	props: Props,
	parent: VirtualNode? = null,
	slots: ((VirtualCompositeNode<Props>) -> Map<String, List<VirtualNode>>?)? = null,
	parentProps: Props? = null,
) : VirtualCompositeNode<Props>(
	props = props,
	commonProps = commonProps,
	parentProps = parentProps,
	parent = parent,
	refName = refName,
	_slots = slots
) {

	@Composable
	override fun Render(payload: RenderPayload) {
		val childNode = child ?: run { Empty(); return }

		val notifierAny = payload.eval<Any>(props.get("notifier"))
		val flow = notifierAny as? StateFlow<Any?> ?: run { Empty(); return }

		// Recompose whenever the flow emits.
		flow.collectAsState(initial = flow.value)

		childNode.ToWidget(payload)
	}
}

fun animatedBuilderBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {
	return VWAnimatedBuilder(
		refName = data.refName,
		commonProps = data.commonProps,
		parent = parent,
		parentProps = data.parentProps,
		props = data.props,
		slots = { self ->
			registerAllChildern(data.childGroups, self, registry)
		},
	)
}
