package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualLeafNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.evalColor
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.JsonLike
import kotlinx.coroutines.launch

/**
 * RangeSlider widget properties Maps to the Flutter VWRangeSlider props structure from
 * range_slider.dart schema
 */
data class RangeSliderProps(
        val min: ExprOr<Number>?,
        val max: ExprOr<Number>?,
        val division: ExprOr<Number>?,
        val startValue: ExprOr<Number>?,
        val endValue: ExprOr<Number>?,
        val activeColor: ExprOr<String>?,
        val inactiveColor: ExprOr<String>?,
        val thumbColor: ExprOr<String>?,
        val thumbRadius: ExprOr<Number>?,
        val trackHeight: ExprOr<Number>?,
        val onChanged: ActionFlow?
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: JsonLike): RangeSliderProps {
            return RangeSliderProps(
                    min = ExprOr.fromValue(json["min"]),
                    max = ExprOr.fromValue(json["max"]),
                    division = ExprOr.fromValue(json["division"]),
                    startValue = ExprOr.fromValue(json["startValue"]),
                    endValue = ExprOr.fromValue(json["endValue"]),
                    activeColor = ExprOr.fromValue(json["activeColor"]),
                    inactiveColor = ExprOr.fromValue(json["inactiveColor"]),
                    thumbColor = ExprOr.fromValue(json["thumbColor"]),
                    thumbRadius = ExprOr.fromValue(json["thumbRadius"]),
                    trackHeight = ExprOr.fromValue(json["trackHeight"]),
                    onChanged = (json["onChanged"] as? JsonLike)?.let { ActionFlow.fromJson(it) }
            )
        }
    }
}

/**
 * VWRangeSlider - A range slider widget for selecting a range of values
 *
 * Allows users to select a range between two values by dragging two thumbs. Supports customization
 * of colors, divisions, and triggers onChanged actions with startValue and endValue in the scope
 * context.
 */
class VWRangeSlider(
        props: RangeSliderProps,
        commonProps: CommonProps?,
        parentProps: Props?,
        parent: VirtualNode?,
        refName: String? = null
) :
        VirtualLeafNode<RangeSliderProps>(
                props = props,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        val min = payload.evalExpr(props.min)?.toFloat() ?: 0f
        val max = payload.evalExpr(props.max)?.toFloat() ?: 100f
        val divisions = payload.evalExpr(props.division)?.toInt()
        val initialStartValue = payload.evalObserve(props.startValue)?.toFloat() ?: min
        val initialEndValue = payload.evalObserve(props.endValue)?.toFloat() ?: max

        // Clamp values to valid range
        val clampedStart = initialStartValue.coerceIn(min, max)
        val clampedEnd = initialEndValue.coerceIn(min, max)

        // Colors
        val activeColor =
                props.activeColor?.let { payload.evalColor(it.value) }
                        ?: SliderDefaults.colors().activeTrackColor
        val inactiveColor =
                props.inactiveColor?.let { payload.evalColor(it.value) }
                        ?: SliderDefaults.colors().inactiveTrackColor
        val thumbColor =
                props.thumbColor?.let { payload.evalColor(it.value) }
                        ?: SliderDefaults.colors().thumbColor

        // Contexts for action execution
        val context = LocalContext.current
        val actionExecutor = LocalActionExecutor.current
        val stateContext = LocalStateContextProvider.current
        val resources = LocalUIResources.current
        val coroutineScope = rememberCoroutineScope()

        // Local state for smooth sliding - using ClosedFloatingPointRange
        var sliderRange by remember { mutableStateOf(clampedStart..clampedEnd) }

        // Flag to ignore external updates when dragging
        var isDragging by remember { mutableStateOf(false) }

        // Sync with props if they change externally (only if not dragging)
        LaunchedEffect(clampedStart, clampedEnd) {
            if (!isDragging) {
                sliderRange = clampedStart..clampedEnd
            }
        }

        // Calculate steps: Compose steps = number of values *between* endpoints
        // Flutter divisions = number of intervals (segments)
        // steps = divisions - 1
        val steps = if (divisions != null && divisions > 1) divisions - 1 else 0

        RangeSlider(
                value = sliderRange,
                onValueChange = { newRange ->
                    isDragging = true
                    sliderRange = newRange

                    // Trigger action with new values
                    val action = props.onChanged
                    if (action != null) {
                        val scopeContext =
                                DefaultScopeContext(
                                        variables =
                                                mapOf(
                                                        "startValue" to newRange.start.toDouble(),
                                                        "endValue" to
                                                                newRange.endInclusive.toDouble()
                                                )
                                )

                        coroutineScope.launch {
                            payload.executeAction(
                                    context = context,
                                    actionFlow = action,
                                    actionExecutor = actionExecutor,
                                    stateContext = stateContext,
                                    resourcesProvider = resources,
                                    incomingScopeContext = scopeContext
                            )
                        }
                    }
                },
                onValueChangeFinished = { isDragging = false },
                modifier = Modifier.fillMaxWidth().then(Modifier.buildModifier(payload)),
                valueRange = min..max,
                steps = steps,
                colors =
                        SliderDefaults.colors(
                                thumbColor = thumbColor,
                                activeTrackColor = activeColor,
                                inactiveTrackColor = inactiveColor
                        )
        )
    }
}

/** Builder function for RangeSlider widget */
fun rangeSliderBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWRangeSlider(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = RangeSliderProps.fromJson(data.props.value),
    )
}
