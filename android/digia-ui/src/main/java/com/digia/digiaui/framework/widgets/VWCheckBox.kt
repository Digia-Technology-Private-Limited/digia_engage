package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.utils.toDp
import resourceColor

private const val DEFAULT_CHECKBOX_SIZE_DP = 24.0

class VWCheckBox(
	refName: String? = null,
	commonProps: com.digia.digiaui.framework.models.CommonProps? = null,
	parent: VirtualNode? = null,
	parentProps: Props? = null,
	props: Props,
) : VirtualLeafNode<Props>(
	props = props,
	commonProps = commonProps,
	parent = parent,
	refName = refName,
	parentProps = parentProps,
) {
	@Composable
	override fun Render(payload: RenderPayload) {
		// Stateless display - just read and show the value (no actions)
		val value = payload.eval<Boolean>(props.get("value")) ?: false
		val enabled = payload.eval<Boolean>(props.get("enabled")) ?: true

		val widthDp = commonProps?.style?.width?.toDp() ?: DEFAULT_CHECKBOX_SIZE_DP.dp
		val heightDp = commonProps?.style?.height?.toDp() ?: widthDp
		val scaleX = widthDp.value / 20f
		val scaleY = heightDp.value / 20f

		val activeColor = payload.eval<String>(props.get("activeColor"))?.let { resourceColor(it) }
		val inactiveColor = payload.eval<String>(props.get("inactiveColor"))?.let { resourceColor(it) }

		val colors = CheckboxDefaults.colors(
			checkedColor = activeColor?: Color.Unspecified,
			uncheckedColor = inactiveColor ?: Color.Unspecified,
			checkmarkColor = Color.White
		)

		// Center wrapper to match Flutter's layout structure
		androidx.compose.foundation.layout.Box(
			modifier = Modifier.size(widthDp, heightDp),
			contentAlignment = Alignment.Center
		) {
			Checkbox(
				checked = value,
				onCheckedChange = null, // stateless
				enabled = enabled,
				colors = colors,
				modifier = Modifier.scale(scaleX, scaleY)
			)
		}
	}
}

fun checkBoxBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {
	return VWCheckBox(
		refName = data.refName,
		commonProps = data.commonProps,
		parent = parent,
		parentProps = data.parentProps,
		props = data.props,
	)
}