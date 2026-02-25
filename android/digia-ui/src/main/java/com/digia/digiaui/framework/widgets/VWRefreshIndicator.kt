package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.internals.PullToRefreshBox
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.launch

data class RefreshIndicatorProps(
	val color: Any? = null,
	val backgroundColor: Any? = null,
	val displacement: Any? = null,
	val edgeOffset: Any? = null,
	val strokeWidth: Any? = null,
	val triggerMode: Any? = null,
	val onRefresh: ActionFlow? = null,
) {
	companion object {
		fun fromJson(json: JsonLike): RefreshIndicatorProps {
			return RefreshIndicatorProps(
				color = json["color"],
				backgroundColor = json["backgroundColor"],
				displacement = json["displacement"],
				edgeOffset = json["edgeOffset"],
				strokeWidth = json["strokeWidth"],
				triggerMode = json["triggerMode"],
				onRefresh = ActionFlow.fromJson(json["onRefresh"] as? JsonLike),
			)
		}
	}
}

class VWRefreshIndicator(
	refName: String? = null,
	commonProps: CommonProps? = null,
	props: RefreshIndicatorProps,
	parent: VirtualNode? = null,
	slots: ((VirtualCompositeNode<RefreshIndicatorProps>) -> Map<String, List<VirtualNode>>?)? = null,
	parentProps: Props? = null,
) : VirtualCompositeNode<RefreshIndicatorProps>(
	props = props,
	commonProps = commonProps,
	parentProps = parentProps,
	parent = parent,
	refName = refName,
	_slots = slots
) {

//	@OptIn(ExperimentalMaterialApi::class)
	@Composable
	override fun Render(payload: RenderPayload) {
		val context = LocalContext.current.applicationContext
		val resources = LocalUIResources.current
		val stateContext = LocalStateContextProvider.current
		val actionExecutor = LocalActionExecutor.current
		val scope = rememberCoroutineScope()

		if (child == null) {
			Empty()
			return
		}

		val indicatorColor = payload.evalColor(props.color) ?: Color.Unspecified
		val bgColor = payload.evalColor(props.backgroundColor) ?: Color.Transparent
		val displacement = (payload.eval<Double>(props.displacement) ?: 40.0).toFloat()
		val edgeOffset = (payload.eval<Double>(props.edgeOffset) ?: 0.0).toFloat()
		val strokeWidth = (payload.eval<Double>(props.strokeWidth) ?: 2.0).toFloat()
		val triggerMode = payload.eval<String>(props.triggerMode)?.lowercase()

		val refreshingState = remember { mutableStateOf(false) }

		PullToRefreshBox(
			refreshing = refreshingState.value,
			onRefresh = {
				scope.launch {
					refreshingState.value = true
					payload.executeAction(
						context = context,
						actionFlow = props.onRefresh,
						actionExecutor = actionExecutor,
						stateContext = stateContext,
						resourcesProvider = resources,
						incomingScopeContext = null,
					)
					refreshingState.value = false
				}
			},
			modifier = Modifier.buildModifier(payload),
			indicatorColor = indicatorColor,
			indicatorBackgroundColor = bgColor,
			indicatorTopPadding = edgeOffset.dp,
			refreshingOffset = (edgeOffset + displacement).dp,
			strokeWidth = strokeWidth.dp,
			enabled = true,
			triggerMode = triggerMode,
		) {
			child?.ToWidget(payload)
		}
	}
}

fun refreshIndicatorBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {
	return VWRefreshIndicator(
		refName = data.refName,
		commonProps = data.commonProps,
		parent = parent,
		parentProps = data.parentProps,
		props = RefreshIndicatorProps.fromJson(data.props.value),
		slots = { self ->
			registerAllChildern(data.childGroups, self, registry)
		},
	)
}