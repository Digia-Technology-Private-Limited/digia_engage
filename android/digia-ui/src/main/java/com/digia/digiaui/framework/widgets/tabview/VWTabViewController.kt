package com.digia.digiaui.framework.widgets.tabview

import LocalUIResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.JsonLike

/**
 * TabViewControllerProps - properties for the TabViewController widget. Matches Flutter's
 * TabViewControllerProps schema.
 */
data class TabViewControllerProps(
        val tabs: ExprOr<List<Any>>? = null,
        val initialIndex: ExprOr<Int>? = null,
        val onTabChange: ActionFlow? = null
) {
    companion object {
        fun fromJson(json: JsonLike): TabViewControllerProps {
            return TabViewControllerProps(
                    tabs = ExprOr.fromJson(json["dynamicList"]),
                    initialIndex = ExprOr.fromJson(json["initialIndex"]),
                    onTabChange = ActionFlow.fromJson(json["onTabChange"] as? JsonLike)
            )
        }
    }
}

/**
 * VWTabViewController - provides a TabViewController to its descendants. This widget wraps children
 * and provides tab state via CompositionLocal. It mirrors Flutter's TabViewControllerScopeWidget
 * pattern.
 */
class VWTabViewController(
        refName: String? = null,
        commonProps: CommonProps? = null,
        props: TabViewControllerProps,
        parent: VirtualNode? = null,
        slots:
                ((VirtualCompositeNode<TabViewControllerProps>) -> Map<
                                String, List<VirtualNode>>?)? =
                null,
        parentProps: Props? = null
) :
        VirtualCompositeNode<TabViewControllerProps>(
                props = props,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName,
                _slots = slots
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        if (child == null) {
            Empty()
            return
        }

        val context = LocalContext.current.applicationContext
        val resources = LocalUIResources.current
        val stateContext = LocalStateContextProvider.current
        val actionExecutor = LocalActionExecutor.current

        // Evaluate props
        val tabs = payload.evalExpr(props.tabs) ?: emptyList()
        val initialIndex = payload.evalExpr(props.initialIndex) ?: 0

        // State for current index
        var currentIndex by remember {
            mutableIntStateOf(initialIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)))
        }

        // Create controller
        val controller =
                remember(tabs, initialIndex) {
                    TabViewController(
                            tabs = tabs,
                            initialIndex = initialIndex,
                            currentIndex = currentIndex,
                            onTabChange = { newIndex -> currentIndex = newIndex }
                    )
                }

        // Update controller when currentIndex changes
        val updatedController = controller.copy(currentIndex = currentIndex)

        // Handle onTabChange callback
        fun handleTabChange(newIndex: Int, currentItem: Any?) {
            currentIndex = newIndex

            props.onTabChange?.let { actionFlow ->
                val scopeContext =
                        DefaultScopeContext(
                                variables =
                                        buildMap {
                                            put("index", newIndex)
                                            put("currentItem", currentItem)
                                            refName?.let {
                                                put(
                                                        it,
                                                        mapOf(
                                                                "index" to newIndex,
                                                                "currentItem" to currentItem
                                                        )
                                                )
                                            }
                                        }
                        )

                payload.executeAction(
                        context = context,
                        actionFlow = actionFlow,
                        actionExecutor = actionExecutor,
                        stateContext = stateContext,
                        resourcesProvider = resources,
                        incomingScopeContext = scopeContext
                )
            }
        }

        // Provide updated controller with onTabChange handler
        val controllerWithHandler =
                updatedController.copy(
                        onTabChange = { newIndex ->
                            val currentItem = if (newIndex in tabs.indices) tabs[newIndex] else null
                            handleTabChange(newIndex, currentItem)
                        }
                )

        // Provide the controller via CompositionLocal
        CompositionLocalProvider(LocalTabViewController provides controllerWithHandler) {
            child?.ToWidget(payload)
        }
    }
}

/** Builder function for TabViewController widget */
fun tabViewControllerBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWTabViewController(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = TabViewControllerProps.fromJson(data.props.value),
            slots = { self -> registerAllChildern(data.childGroups, self, registry) },
    )
}
