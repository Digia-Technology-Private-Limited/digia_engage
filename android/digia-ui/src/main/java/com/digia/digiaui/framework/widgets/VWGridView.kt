package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.datatype.AdaptedScrollController
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike

/** GridView widget properties */
data class GridViewProps(
        val dataSource: Any? = null,
        val crossAxisCount: Int? = 2,
        val mainAxisSpacing: Float? = 0f,
        val crossAxisSpacing: Float? = 0f,
        val scrollDirection: String? = null,
        val shrinkWrap: Boolean? = null,
        val allowScroll: Boolean? = null,
        val controller: Any? = null
) {
    companion object {
        fun fromJson(json: JsonLike): GridViewProps {
            return GridViewProps(
                    dataSource = json["dataSource"],
                    crossAxisCount = (json["crossAxisCount"] as? Number)?.toInt(),
                    mainAxisSpacing = (json["mainAxisSpacing"] as? Number)?.toFloat(),
                    crossAxisSpacing = (json["crossAxisSpacing"] as? Number)?.toFloat(),
                    scrollDirection = json["scrollDirection"] as? String,
                    shrinkWrap = json["shrinkWrap"] as? Boolean,
                    allowScroll = json["allowScroll"] as? Boolean,
                    controller = json["controller"]
            )
        }
    }
}

/**
 * Virtual GridView widget
 *
 * Renders a scrollable grid of items from a data source.
 */
class VWGridView(
        refName: String? = null,
        commonProps: CommonProps? = null,
        props: GridViewProps,
        parent: VirtualNode? = null,
        slots: ((VirtualCompositeNode<GridViewProps>) -> Map<String, List<VirtualNode>>?)? = null,
        parentProps: Props? = null
) :
        VirtualCompositeNode<GridViewProps>(
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
        // Return empty if no child template or data source
        if (child == null || !shouldRepeatChild) {
            Empty()
            return
        }

        val items = payload.eval<List<Any>>(props.dataSource) ?: emptyList()
        val controller = payload.eval<AdaptedScrollController>(props.controller)
        val shrinkWrap = props.shrinkWrap ?: false
        val crossAxisCount = props.crossAxisCount ?: 2
        val mainAxisSpacing = props.mainAxisSpacing ?: 0f
        val crossAxisSpacing = props.crossAxisSpacing ?: 0f
        val scrollDirection = props.scrollDirection ?: "vertical"
        val allowScroll = props.allowScroll ?: true

        if (shrinkWrap) {
            // When shrinkWrap is true, render all items non-lazily to avoid unbounded constraints
            // This matches Flutter's behavior where shrinkWrap loads all items
            val baseModifier = Modifier.buildModifier(payload)
           FixedGrid(
               child = { item, index ->
                   val scopedPayload =
                       payload.copyWithChainedContext(
                           createExprContext(item, index)
                       )
                   // ✅ Safe composable call
                   child?.ToWidget(scopedPayload)
               },
               items = items,
               crossAxisCount = crossAxisCount,
               modifier = baseModifier,
               mainAxisSpacing = mainAxisSpacing.dp,
               crossAxisSpacing = crossAxisSpacing.dp
           )
        } else {



            // Use lazy components for better performance when shrinkWrap is false
            val gridState = rememberLazyGridState()
            val listModifier = Modifier.buildModifier(payload).then(
                when (scrollDirection.lowercase()) {
                    "horizontal" -> Modifier.fillMaxWidth()
                    else -> Modifier.fillMaxHeight()
                }
            )

            LaunchedEffect(controller, gridState) {
                controller?.attachLazyGridState(gridState)
            }

            when (scrollDirection.lowercase()) {
                "horizontal" -> {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(crossAxisCount),
                        state = gridState,
                        modifier = listModifier,
                        contentPadding = PaddingValues(0.dp),
                        horizontalArrangement = Arrangement.spacedBy(mainAxisSpacing.dp),
                        verticalArrangement = Arrangement.spacedBy(crossAxisSpacing.dp),
                        userScrollEnabled = allowScroll,

                    ) {
                        itemsIndexed(items) { index, item ->
                            val scopedPayload = payload.copyWithChainedContext(createExprContext(item, index))
                            child?.ToWidget(scopedPayload)
                        }
                    }
                }
                else -> {
                    // Default to vertical
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(crossAxisCount),
                        state = gridState,
                        modifier = listModifier,
                        contentPadding = PaddingValues(0.dp),
                        verticalArrangement = Arrangement.spacedBy(mainAxisSpacing.dp),
                        horizontalArrangement = Arrangement.spacedBy(crossAxisSpacing.dp),
                        userScrollEnabled = allowScroll,
                    ) {
                        itemsIndexed(items) { index, item ->
                            val scopedPayload = payload.copyWithChainedContext(createExprContext(item, index))
                            child?.ToWidget(scopedPayload)
                        }
                    }
                }
            }
        }
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val gridObj = mapOf("currentItem" to item, "index" to index)

        val variables =
                mutableMapOf<String, Any?>().apply {
                    putAll(gridObj)
                    refName?.let { name -> put(name, gridObj) }
                }

        return DefaultScopeContext(variables = variables)
    }
}

/** Builder function for GridView widget */
fun gridViewBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWGridView(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = GridViewProps.fromJson(data.props.value),
            slots = { self -> registerAllChildern(data.childGroups, self, registry) },
    )
}





@Composable
fun FixedGrid(
    items: List<Any>,
    crossAxisCount: Int,
    child: @Composable (item: Any?, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    mainAxisSpacing: Dp = 0.dp,
    crossAxisSpacing: Dp = 0.dp,
) {
    val rows = remember(items, crossAxisCount) {
        items.chunked(crossAxisCount)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(mainAxisSpacing)
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(crossAxisSpacing)
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val index = rowIndex * crossAxisCount + colIndex

                    Box(
                        modifier = Modifier
                            .weight(1f)      // 🔥 equal width
                    ) {
                        child(item, index)
                    }
                }

                // 🔥 Fill empty columns in last row
                repeat(crossAxisCount - rowItems.size) {
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
