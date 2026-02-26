
package com.digia.digiaui.framework.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

data class AnimatedSwitcherProps(
	val animationDuration: ExprOr<Int>? = null,
	val showFirstChild: ExprOr<Boolean>? = null,
	val switchInCurve: String? = null,
	val switchOutCurve: String? = null,
) {
	companion object {
		fun fromJson(json: JsonLike): AnimatedSwitcherProps {
			return AnimatedSwitcherProps(
				animationDuration = ExprOr.fromValue(json["animationDuration"]),
				showFirstChild = ExprOr.fromValue(json["showFirstChild"]),
				switchInCurve = json["switchInCurve"] as? String,
				switchOutCurve = json["switchOutCurve"] as? String,
			)
		}
	}
}

class VWAnimatedSwitcher(
	refName: String? = null,
	commonProps: CommonProps? = null,
	props: AnimatedSwitcherProps,
	parent: VirtualNode? = null,
	slots: ((VirtualCompositeNode<AnimatedSwitcherProps>) -> Map<String, List<VirtualNode>>?)? = null,
	parentProps: Props? = null,
) : VirtualCompositeNode<AnimatedSwitcherProps>(
	props = props,
	commonProps = commonProps,
	parentProps = parentProps,
	parent = parent,
	refName = refName,
	_slots = slots
) {

	@OptIn(ExperimentalAnimationApi::class)
	@Composable
	override fun Render(payload: RenderPayload) {
		val firstChild = slot("firstChild") ?: run { Empty(); return }
		val secondChild = slot("secondChild")

		val durationMs = payload.evalExpr(props.animationDuration) ?: 100
		val showFirst = payload.evalExpr(props.showFirstChild) ?: true

		AnimatedContent(
			targetState = showFirst,
			modifier = Modifier.buildModifier(payload),
			transitionSpec = {
				fadeIn(tween(durationMillis = durationMs, easing = easingFrom(props.switchInCurve)))
					.togetherWith(fadeOut(tween(durationMillis = durationMs, easing = easingFrom(props.switchOutCurve))))
			},
			label = "VWAnimatedSwitcher",
		) { isFirst ->
			if (isFirst) firstChild.ToWidget(payload) else secondChild?.ToWidget(payload) ?: Empty()
		}
	}

	private fun easingFrom(value: String?): Easing = when (value?.lowercase()) {
		"linear" -> LinearEasing
		"ease" -> FastOutSlowInEasing
		"easein" -> FastOutSlowInEasing
		"easeout" -> FastOutSlowInEasing
		else -> LinearEasing
	}
}

fun animatedSwitcherBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {
	return VWAnimatedSwitcher(
		refName = data.refName,
		commonProps = data.commonProps,
		parent = parent,
		parentProps = data.parentProps,
		props = AnimatedSwitcherProps.fromJson(data.props.value),
		slots = { self ->
			registerAllChildern(data.childGroups, self, registry)
		},
	)
}
