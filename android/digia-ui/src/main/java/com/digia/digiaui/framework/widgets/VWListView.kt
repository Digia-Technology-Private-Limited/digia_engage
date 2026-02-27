package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike

/**
 * Scroll direction for ListView
 */
enum class ScrollDirection {
    VERTICAL, HORIZONTAL
}

/**
 * ListView widget properties
 */
data class ListViewProps(
    val dataSource: Any? = null,
    val scrollDirection: String? = null,
    val reverse: Boolean? = null,
    val shrinkWrap: Boolean? = null,
    val allowScroll: Boolean? = null,
    val initialScrollPosition: String? = null,
    val scrollController: Any? = null
) {
    companion object {
        fun fromJson(json: JsonLike): ListViewProps {
            return ListViewProps(
                dataSource = json["dataSource"],
                scrollDirection = json["scrollDirection"] as? String,
                reverse = json["reverse"] as? Boolean,
                shrinkWrap = json["shrinkWrap"] as? Boolean,
                allowScroll = json["allowScroll"] as? Boolean,
                initialScrollPosition = json["initialScrollPosition"] as? String,
                scrollController = json["controller"]
            )
        }
    }
}

/**
 * Virtual ListView widget
 *
 * Renders a scrollable list of items from a data source.
 * Each item is rendered using the child template widget.
 */
class VWListView(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: ListViewProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<ListViewProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<ListViewProps>(
    props = props,
    commonProps = commonProps,
    parentProps= parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    private val shouldRepeatChild: Boolean
        get() = props.dataSource != null

    @Composable
    override fun Render(payload: RenderPayload) {
        // Return empty if no child template or data source
        if (child == null || !shouldRepeatChild) {
            Empty()
            return
        }

        val items = payload.eval<List<Any>>(props.dataSource) ?: emptyList()
        val reverse = props.reverse ?: false
        val scrollDirection = toScrollDirection(props.scrollDirection)
        val shrinkWrap = props.shrinkWrap ?: false

        if (shrinkWrap) {
            // When shrinkWrap is true, render all items non-lazily to avoid unbounded constraints
            // This matches Flutter's behavior where shrinkWrap loads all items
            val baseModifier = Modifier.buildModifier(payload)
            val itemsList = if (reverse) items.reversed() else items
            
            when (scrollDirection) {
                ScrollDirection.VERTICAL -> {
                    Column(modifier = baseModifier) {
                        itemsList.forEachIndexed { index, item ->
                            val actualIndex = if (reverse) items.size - 1 - index else index
                            val scopedPayload = payload.copyWithChainedContext(
                                createExprContext(item, actualIndex)
                            )
                            child?.ToWidget(scopedPayload)
                        }
                    }
                }
                ScrollDirection.HORIZONTAL -> {
                    Row(modifier = baseModifier) {
                        itemsList.forEachIndexed { index, item ->
                            val actualIndex = if (reverse) items.size - 1 - index else index
                            val scopedPayload = payload.copyWithChainedContext(
                                createExprContext(item, actualIndex)
                            )
                            child?.ToWidget(scopedPayload)
                        }
                    }
                }
            }
        } else {
            // Use lazy components for better performance when shrinkWrap is false
            val listState = rememberLazyListState()
            
            // Attach scroll controller if provided
            val scrollController = payload.eval<com.digia.digiaui.framework.datatype.AdaptedScrollController>(props.scrollController)
            LaunchedEffect(scrollController, listState) {
                scrollController?.attachLazyListState(listState)
            }

            // Handle initial scroll position
            props.initialScrollPosition?.let { initial ->
                LaunchedEffect(initial) {
                    when (initial.lowercase()) {
                        "start" -> listState.scrollToItem(0)
                        "end" -> if (items.isNotEmpty()) listState.scrollToItem(items.size - 1)
                        else -> initial.toIntOrNull()?.takeIf { it in items.indices }?.let {
                            listState.scrollToItem(it)
                        }
                    }
                }
            }

            val listModifier = Modifier.buildModifier(payload).fillMaxWidth()

            when (scrollDirection) {
                ScrollDirection.VERTICAL -> {
                    LazyColumn(
                        state = listState,
                        reverseLayout = reverse,
                        modifier = listModifier
                    ) {
                        itemsIndexed(items) { index, item ->
                            val scopedPayload = payload.copyWithChainedContext(
                                createExprContext(item, index)
                            )
                            child?.ToWidget(scopedPayload)
                        }
                    }
                }

                ScrollDirection.HORIZONTAL -> {
                    LazyRow(
                        state = listState,
                        reverseLayout = reverse,
                        modifier = listModifier
                    ) {
                        itemsIndexed(items) { index, item ->
                            val scopedPayload = payload.copyWithChainedContext(
                                createExprContext(item, index)
                            )
                            child?.ToWidget(scopedPayload)
                        }
                    }
                }
            }
        }
    }

    private fun toScrollDirection(value: String?): ScrollDirection = when (value?.lowercase()) {
        "horizontal" -> ScrollDirection.HORIZONTAL
        "vertical" -> ScrollDirection.VERTICAL
        else -> ScrollDirection.VERTICAL
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val listObj = mapOf(
            "currentItem" to item,
            "index" to index
        )

        val variables = mutableMapOf<String, Any?>().apply {
            putAll(listObj)
            refName?.let { name -> put(name, listObj) }
        }

        return DefaultScopeContext(variables = variables)
    }
}


/** Builder function for ListView widget */
fun listViewBuilder(data: VWNodeData, parent: VirtualNode?,registry: VirtualWidgetRegistry): VirtualNode {

    return VWListView(
        refName = data.refName,
        commonProps = data.commonProps,
        parent= parent,
        parentProps = data.parentProps,
        props = ListViewProps.fromJson(data.props.value ),
        slots = {
                self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )

}
