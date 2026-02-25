package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.widgets.timer.TimerController
import com.digia.digiaui.framework.widgets.timer.TimerWidget


enum class TimerType {
	COUNT_UP,
	COUNT_DOWN,
}

data class TimerProps(
	val duration: ExprOr<Int>? = null,
	val initialValue: ExprOr<Int>? = null,
	val controller: ExprOr<TimerController>? = null,
	val updateInterval: ExprOr<Int>? = null,
	val timerType: TimerType = TimerType.COUNT_DOWN,
	val onTick: ActionFlow? = null,
	val onTimerEnd: ActionFlow? = null,
) {
	companion object {
		fun fromJson(json: JsonLike): TimerProps {
			return TimerProps(
				duration = ExprOr.fromValue(json["duration"]),
				initialValue = ExprOr.fromValue(json["initialValue"]),
				controller = ExprOr.fromValue(json["controller"]),
				updateInterval = ExprOr.fromValue(json["updateInterval"]),
				timerType = when (json["timerType"] as? String) {
					"countUp" -> TimerType.COUNT_UP
					"countDown" -> TimerType.COUNT_DOWN
					else -> TimerType.COUNT_DOWN
				},
				onTick = (json["onTick"] as? JsonLike)?.let { ActionFlow.fromJson(it) },
				onTimerEnd = (json["onTimerEnd"] as? JsonLike)?.let { ActionFlow.fromJson(it) },
			)
		}
	}
}

class VWTimer(
	refName: String? = null,
	commonProps: CommonProps? = null,
	props: TimerProps,
	parent: VirtualNode? = null,
	slots: ((VirtualCompositeNode<TimerProps>) -> Map<String, List<VirtualNode>>?)? = null,
	parentProps: Props? = null,
) : VirtualCompositeNode<TimerProps>(
	props = props,
	commonProps = commonProps,
	parentProps = parentProps,
	parent = parent,
	refName = refName,
	_slots = slots
) {

	@Composable
	override fun Render(payload: RenderPayload) {
		val context = LocalContext.current
		val actionExecutor = LocalActionExecutor.current
		val stateContext = LocalStateContextProvider.current
		val resources = LocalUIResources.current

		if (child == null) {
			Empty()
			return
		}

		val duration = payload.evalExpr(props.duration) ?: 0
		val isCountDown = props.timerType==TimerType.COUNT_DOWN
		val initialValue = payload.evalExpr(props.initialValue) ?: (if (isCountDown) duration else 0)
		val updateIntervalSeconds = payload.evalExpr(props.updateInterval) ?: 1
		val updateIntervalMs = updateIntervalSeconds.coerceAtLeast(0).toLong() * 1000L

		val externalController = payload.evalExpr<TimerController>(props.controller)
		val localController = remember(initialValue, updateIntervalMs, isCountDown, duration) {
			TimerController(
				initialValue = initialValue,
				updateInterval = updateIntervalMs,
				isCountDown = isCountDown,
				duration = duration,
			)
		}
		val controller = externalController ?: localController

		DisposableEffect(controller, externalController) {
			onDispose {
				if (externalController == null) {
					localController.dispose()
				}
			}
		}

		if (duration < 0) {
			val updatedPayload = payload.copyWithChainedContext(
				_createExprContext(initialValue)
			)
			child?.ToWidget(updatedPayload)
			return
		}

		val endValue = if (isCountDown) initialValue - duration else initialValue + duration

		TimerWidget(
			controller = controller,
			initialValue = initialValue,
			updateIntervalMs = updateIntervalMs,
			isCountDown = isCountDown,
			duration = duration,
		) { value ->
			val updatedPayload = payload.copyWithChainedContext(
				_createExprContext(value)
			)

			LaunchedEffect(value) {
				props.onTick?.let {
					updatedPayload.executeAction(
						context = context,
						actionFlow = it,
						actionExecutor = actionExecutor,
						stateContext = stateContext,
						resourcesProvider = resources,
						incomingScopeContext = null,
					)
				}

				if (value == endValue) {
					props.onTimerEnd?.let {
						updatedPayload.executeAction(
							context = context,
							actionFlow = it,
							actionExecutor = actionExecutor,
							stateContext = stateContext,
							resourcesProvider = resources,
							incomingScopeContext = null,
						)
					}
				}
			}

			child?.ToWidget(updatedPayload) ?: Empty()
		}
	}

	private fun _createExprContext(value: Int?): ScopeContext {
		val timerObj: Map<String, Any?> = mapOf(
			"tickValue" to value,
		)

		val variables = buildMap<String, Any?> {
			putAll(timerObj)
			refName?.let { put(it, timerObj) }
		}

		return DefaultScopeContext(
			variables = variables,
		)
	}
}

fun timerBuilder(
	data: VWNodeData,
	parent: VirtualNode?,
	registry: VirtualWidgetRegistry,
): VirtualNode {
	return VWTimer(
		refName = data.refName,
		commonProps = data.commonProps,
		parent = parent,
		parentProps = data.parentProps,
		props = TimerProps.fromJson(data.props.value),
		slots = { self ->
			registerAllChildern(data.childGroups, self, registry)
		},
	)
}