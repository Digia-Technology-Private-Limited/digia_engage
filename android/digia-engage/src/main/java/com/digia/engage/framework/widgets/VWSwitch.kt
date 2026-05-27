package com.digia.engage.framework.widgets

import LocalUIResources
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.digia.engage.framework.RenderPayload
import com.digia.engage.framework.VirtualWidgetRegistry
import com.digia.engage.framework.actions.LocalActionExecutor
import com.digia.engage.framework.actions.base.ActionFlow
import com.digia.engage.framework.base.VirtualLeafNode
import com.digia.engage.framework.base.VirtualNode
import com.digia.engage.framework.expr.DefaultScopeContext
import com.digia.engage.framework.expr.ScopeContext
import com.digia.engage.framework.models.Props
import com.digia.engage.framework.models.VWNodeData
import com.digia.engage.framework.state.LocalStateContextProvider
import com.digia.engage.framework.utils.toDp
import resourceColor

class VWSwitch(
        refName: String? = null,
        commonProps: com.digia.engage.framework.models.CommonProps? = null,
        parent: VirtualNode? = null,
        parentProps: Props? = null,
        props: Props,
) :
        VirtualLeafNode<Props>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps,
        ) {
    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current

        val enabled = payload.evalObserve<Boolean>(props.get("enabled")) ?: false
        val valueFromProps = payload.evalObserve<Boolean>(props.get("value")) ?: false

        val activeThumbColor =
                payload.eval<String>(props.get("activeColor"))?.let { resourceColor(it) }
        val inactiveThumbColor =
                payload.eval<String>(props.get("inactiveThumbColor"))?.let { resourceColor(it) }
        val activeTrackColor =
                payload.eval<String>(props.get("activeTrackColor"))?.let { resourceColor(it) }
        val inactiveTrackColor =
                payload.eval<String>(props.get("inactiveTrackColor"))?.let { resourceColor(it) }

        val onChangedFlow = ActionFlow.fromJson(props.getMap("onChanged"))

        var checked by rememberSaveable { mutableStateOf(valueFromProps) }
        LaunchedEffect(valueFromProps) { checked = valueFromProps }

        val widthDp = commonProps?.style?.width?.toDp() ?: 52.dp
        val heightDp = commonProps?.style?.height?.toDp() ?: 32.dp
        val scaleX = widthDp.value / 52f
        val scaleY = heightDp.value / 32f

        // Match Flutter's WidgetStatePropertyAll behavior for thumb color
        val thumbColor = if (checked) activeThumbColor else inactiveThumbColor
        val colors =
                SwitchDefaults.colors(
                        checkedThumbColor = thumbColor ?: Color.Unspecified,
                        uncheckedThumbColor = thumbColor ?: Color.Unspecified,
                        checkedTrackColor = activeTrackColor ?: Color.Unspecified,
                        uncheckedTrackColor = inactiveTrackColor ?: Color.Unspecified,
                )

        Switch(
                checked = checked,
                onCheckedChange =
                        if (enabled) {
                            { newValue: Boolean ->
                                checked = newValue
                                payload.executeAction(
                                        context = context,
                                        actionFlow = onChangedFlow,
                                        actionExecutor = actionExecutor,
                                        stateContext = stateContext,
                                        resourcesProvider = resources,
                                        incomingScopeContext = _createExprContext(newValue),
                                )
                            }
                        } else {
                            null
                        },
                enabled = enabled,
                colors = colors,
                modifier = Modifier.buildModifier(payload).scale(scaleX, scaleY),
        )
    }

    private fun _createExprContext(value: Boolean): ScopeContext {
        val switchObj: Map<String, Any?> =
                mapOf(
                        "value" to value,
                )

        val variables =
                buildMap<String, Any?> {
                    putAll(switchObj)
                    refName?.let { put(it, switchObj) }
                }

        return DefaultScopeContext(variables = variables)
    }
}

fun switchBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWSwitch(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = data.props,
    )
}
