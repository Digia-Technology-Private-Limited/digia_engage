package com.digia.digiaui.framework.widgets

import LocalUIResources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.datatype.AdaptedPageController
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.internals.Axis
import com.digia.digiaui.framework.internals.InternalPageView
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.utils.JsonLike

data class PageViewProps(
    val dataSource: Any? = null,
    val preloadPages: Any? = null,
    val reverse: Any? = null,
    val initialPage: Any? = null,
    val viewportFraction: Any? = null,
    val keepPage: Any? = null,
    val pageSnapping: Any? = null,
    val controller: Any? = null,
    val scrollDirection: Any? = null,
    val allowScroll: Any? = null,
    val padEnds: Any? = null,
    val onPageChanged: ActionFlow? = null,
) {
    companion object {
        fun fromJson(json: JsonLike): PageViewProps {
            return PageViewProps(
                dataSource = json["dataSource"],
                preloadPages = json["preloadPages"],
                reverse = json["reverse"],
                initialPage = json["initialPage"],
                viewportFraction = json["viewportFraction"],
                keepPage = json["keepPage"],
                pageSnapping = json["pageSnapping"],
                controller = json["controller"],
                scrollDirection = json["scrollDirection"],
                allowScroll = json["allowScroll"],
                padEnds = json["padEnds"],
                onPageChanged = ActionFlow.fromJson(json["onPageChanged"] as? JsonLike),
            )
        }
    }
}

class VWPageView(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: PageViewProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<PageViewProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<PageViewProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    private val shouldRepeatChild: Boolean
        get() = props.dataSource != null

    @Composable
    override fun Render(payload: RenderPayload) {
        val context = LocalContext.current.applicationContext
        val resources = LocalUIResources.current
        val stateContext = LocalStateContextProvider.current
        val actionExecutor = LocalActionExecutor.current

        if (child == null) {
            Empty()
            return
        }

        val isReversed = payload.eval<Boolean>(props.reverse) ?: false
        val initialPage = payload.eval<Int>(props.initialPage) ?: 0
        val viewportFraction = (payload.eval<Double>(props.viewportFraction) ?: 1.0).toFloat()
        val keepPage = payload.eval<Boolean>(props.keepPage) ?: true
        val pageSnapping = payload.eval<Boolean>(props.pageSnapping) ?: true
        val controller = payload.eval<AdaptedPageController>(props.controller)
        val scrollDirection = toAxis(payload.eval<String>(props.scrollDirection))
        val allowScroll = payload.eval<Boolean>(props.allowScroll) ?: true
        val padEnds = payload.eval<Boolean>(props.padEnds) ?: true
        val onPageChanged = props.onPageChanged

        if (shouldRepeatChild) {
            val items = payload.eval<List<Any>>(props.dataSource) ?: emptyList()
            val preloadPages = payload.eval<Boolean>(props.preloadPages) ?: false

            val children = if (preloadPages) {
                items.mapIndexed { index, item ->
                    @Composable {
                        val scopedPayload = payload.copyWithChainedContext(
                            createExprContext(item, index)
                        )
                        child?.ToWidget(scopedPayload) ?: Empty()
                    }
                }
            } else {
                emptyList()
            }

            InternalPageView(
                controller = controller,
                reverse = isReversed,
                initialPage = initialPage,
                viewportFraction = viewportFraction,
                keepPage = keepPage,
                pageSnapping = pageSnapping,
                scrollDirection = scrollDirection,
                padEnds = padEnds,
                allowScroll = allowScroll,
                itemCount = if (preloadPages) null else items.size,
                itemBuilder = if (preloadPages) null else { index ->
                    val scopedPayload = payload.copyWithChainedContext(
                        createExprContext(items.getOrNull(index), index)
                    )
                    child?.ToWidget(scopedPayload) ?: Empty()
                },
                children = children,
                onChanged = { index ->
                    payload.executeAction(
                        context = context,
                        actionFlow = onPageChanged,
                        actionExecutor = actionExecutor,
                        stateContext = stateContext,
                        resourcesProvider = resources,
                        incomingScopeContext = createExprContext(null, index),
                    )
                }
            )
            return
        }

        InternalPageView(
            controller = controller,
            reverse = isReversed,
            initialPage = initialPage,
            viewportFraction = viewportFraction,
            keepPage = keepPage,
            pageSnapping = pageSnapping,
            scrollDirection = scrollDirection,
            padEnds = padEnds,
            allowScroll = allowScroll,
            children = listOf(
                @Composable { child?.ToWidget(payload) ?: Empty() }
            ),
            onChanged = { index ->
                payload.executeAction(
                    context = context,
                    actionFlow = onPageChanged,
                    actionExecutor = actionExecutor,
                    stateContext = stateContext,
                    resourcesProvider = resources,
                    incomingScopeContext = createExprContext(null, index),
                )
            }
        )
    }

    private fun toAxis(value: String?): Axis = when (value?.lowercase()) {
        "vertical" -> Axis.VERTICAL
        "horizontal" -> Axis.HORIZONTAL
        else -> Axis.HORIZONTAL
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val pageViewObj = mapOf(
            "currentItem" to item,
            "index" to index
        )

        val variables = mutableMapOf<String, Any?>().apply {
            putAll(pageViewObj)
            refName?.let { name -> put(name, pageViewObj) }
        }

        return DefaultScopeContext(variables = variables)
    }
}

fun pageViewBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {
    return VWPageView(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = PageViewProps.fromJson(data.props.value),
        slots = { self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )
}
