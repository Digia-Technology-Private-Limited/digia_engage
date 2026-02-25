package com.digia.digiaui.framework.widgets.story

import LocalUIResources
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.color
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.story.StoryController
import com.digia.digiaui.framework.story.StoryIndicatorConfig
import com.digia.digiaui.framework.story.StoryPresenter
import kotlin.time.Duration.Companion.milliseconds

/**
 * Virtual Story widget. Mirrors Flutter's VWStory from story.dart
 *
 * Renders a list of story items (typically videos) with:
 * - Progress indicator
 * - Tap navigation (left/right)
 * - Long press pause
 * - Optional external controller
 */
class VWStory(
        refName: String? = null,
        commonProps: CommonProps? = null,
        parent: VirtualNode? = null,
        parentProps: Props? = null,
        props: StoryProps,
        slots: ((VirtualCompositeNode<StoryProps>) -> Map<String, List<VirtualNode>>?)? = null
) :
        VirtualCompositeNode<StoryProps>(
                props = props,
                commonProps = commonProps,
                parent = parent,
                refName = refName,
                parentProps = parentProps,
                _slots = slots
        ) {

        private val items: List<VirtualNode>
                get() = slotChildren("items")

        private val header: VirtualNode?
                get() = slot("header")

        private val footer: VirtualNode?
                get() = slot("footer")

        @Composable
        override fun Render(payload: RenderPayload) {
                if (items.isEmpty()) {
                        Empty()
                        return
                }

                val context = LocalContext.current
                val actionExecutor = LocalActionExecutor.current
                val stateContext = LocalStateContextProvider.current
                val resources = LocalUIResources.current

                // Evaluate props
                val controller = payload.evalExpr(props.controller) ?: StoryController()
                val initialIndex = payload.evalExpr(props.initialIndex) ?: 0
                val restartOnCompleted = payload.evalExpr(props.restartOnCompleted) ?: false
                val duration = payload.evalExpr(props.duration) ?: 3000

                // Build indicator config
                val indicatorConfig = buildIndicatorConfig(payload, props.indicator)

                // Build modifier
                val modifier = Modifier.buildModifier(payload)

                // Convert items to composable content list
                val contents: List<@Composable () -> Unit> =
                        items.map { item -> { item.ToWidget(payload) } }

                // Render presenter with all callbacks wired
                StoryPresenter(
                        contents = contents,
                        controller = controller,
                        initialIndex = initialIndex.coerceIn(0, items.size - 1),
                        restartOnCompleted = restartOnCompleted,
                        defaultDuration = duration.milliseconds,
                        indicatorConfig = indicatorConfig,
                        header = header?.let { h -> { h.ToWidget(payload) } },
                        footer = footer?.let { f -> { f.ToWidget(payload) } },
                        onCompleted = {
                                props.onCompleted?.let { action ->
                                        payload.executeAction(
                                                context = context,
                                                actionFlow = action,
                                                actionExecutor = actionExecutor,
                                                stateContext = stateContext,
                                                resourcesProvider = resources
                                        )
                                }
                        },
                        onStoryChanged = { index ->
                                props.onStoryChanged?.let { action ->
                                        payload.executeAction(
                                                context = context,
                                                actionFlow = action,
                                                actionExecutor = actionExecutor,
                                                stateContext = stateContext,
                                                resourcesProvider = resources,
                                                incomingScopeContext =
                                                        DefaultScopeContext(
                                                                variables = mapOf("index" to index)
                                                        )
                                        )
                                }
                        },
                        onPreviousCompleted = {
                                props.onPreviousCompleted?.let { action ->
                                        payload.executeAction(
                                                context = context,
                                                actionFlow = action,
                                                actionExecutor = actionExecutor,
                                                stateContext = stateContext,
                                                resourcesProvider = resources
                                        )
                                }
                        },
                        onSlideDown = { offset ->
                                props.onSlideDown?.let { action ->
                                        payload.executeAction(
                                                context = context,
                                                actionFlow = action,
                                                actionExecutor = actionExecutor,
                                                stateContext = stateContext,
                                                resourcesProvider = resources,
                                                incomingScopeContext =
                                                        DefaultScopeContext(
                                                                variables =
                                                                        mapOf(
                                                                                "x" to
                                                                                        offset.x
                                                                                                .toDouble(),
                                                                                "y" to
                                                                                        offset.y
                                                                                                .toDouble()
                                                                        )
                                                        )
                                        )
                                }
                        },
                        onSlideStart = { offset ->
                                props.onSlideStart?.let { action ->
                                        payload.executeAction(
                                                context = context,
                                                actionFlow = action,
                                                actionExecutor = actionExecutor,
                                                stateContext = stateContext,
                                                resourcesProvider = resources,
                                                incomingScopeContext =
                                                        DefaultScopeContext(
                                                                variables =
                                                                        mapOf(
                                                                                "x" to
                                                                                        offset.x
                                                                                                .toDouble(),
                                                                                "y" to
                                                                                        offset.y
                                                                                                .toDouble()
                                                                        )
                                                        )
                                        )
                                }
                        },
                        onLeftTap = {
                                props.onLeftTap?.let { action ->
                                        payload.executeAction(
                                                context = context,
                                                actionFlow = action,
                                                actionExecutor = actionExecutor,
                                                stateContext = stateContext,
                                                resourcesProvider = resources
                                        )
                                }
                                true // Return true to continue with default navigation
                        },
                        onRightTap = {
                                props.onRightTap?.let { action ->
                                        payload.executeAction(
                                                context = context,
                                                actionFlow = action,
                                                actionExecutor = actionExecutor,
                                                stateContext = stateContext,
                                                resourcesProvider = resources
                                        )
                                }
                                true // Return true to continue with default navigation
                        },
                        modifier = modifier
                )
        }

        @Composable
        private fun buildIndicatorConfig(
                payload: RenderPayload,
                indicator: Map<String, Any?>?
        ): StoryIndicatorConfig {
                if (indicator == null) return StoryIndicatorConfig()

                // Resolve colors in composable context
                val activeColor = (indicator["activeColor"] as? String)?.let { payload.color(it) }
                val completedColor =
                        (indicator["backgroundCompletedColor"] as? String)?.let {
                                payload.color(it)
                        }
                val disabledColor =
                        (indicator["backgroundDisabledColor"] as? String)?.let { payload.color(it) }

                return StoryIndicatorConfig.fromJson(
                        json = indicator,
                        activeColor = activeColor,
                        completedColor = completedColor,
                        disabledColor = disabledColor
                )
        }
}

/** Builder function for Story widget registration. */
fun storyBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
        return VWStory(
                refName = data.refName,
                commonProps = data.commonProps,
                parent = parent,
                parentProps = data.parentProps,
                props = StoryProps.fromJson(data.props.value),
                slots = { self -> registerAllChildern(data.childGroups, self, registry) }
        )
}
