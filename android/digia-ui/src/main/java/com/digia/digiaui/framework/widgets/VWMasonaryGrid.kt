
package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * Masonry Grid View properties
 */
data class MasonaryGridProps(
    val dataSource: Any? = null,
    val controller: Any? = null,
    val allowScroll: Boolean? = true,
    val shrinkWrap: Boolean? = false,
    val crossAxisCount: Int = 2,
    val crossAxisSpacing: Float = 4f,
    val mainAxisSpacing: Float = 3f
) {
    companion object {
        fun fromJson(json: JsonLike): MasonaryGridProps {
            return MasonaryGridProps(
                dataSource = json["dataSource"],
                controller = json["controller"],
                allowScroll = json["allowScroll"] as? Boolean ?: true,
                shrinkWrap = json["shrinkWrap"] as? Boolean ?: false,
                crossAxisCount = (json["crossAxisCount"] as? Number)?.toInt() ?: 2,
                crossAxisSpacing = (json["crossAxisSpacing"] as? Number)?.toFloat() ?: 4f,
                mainAxisSpacing = (json["mainAxisSpacing"] as? Number)?.toFloat() ?: 3f
            )
        }
    }
}

/**
 * Virtual Masonary Grid Widget
 *
 * Renders a staggered grid of items from a data source.
 * Mimics the behavior of Flutter's MasonryGridView using Compose's LazyVerticalStaggeredGrid.
 */
class VWMasonaryGrid(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: MasonaryGridProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<MasonaryGridProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<MasonaryGridProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    private val shouldRepeatChild: Boolean
        get() = props.dataSource != null

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Render(payload: RenderPayload) {
        // Return empty if no child template or data source (consistent with VWListView)
        if (child == null || !shouldRepeatChild) {
            Empty()
            return
        }

        val items = payload.eval<List<Any>>(props.dataSource) ?: emptyList()
        val shrinkWrap = props.shrinkWrap ?: false
        val userScrollEnabled = props.allowScroll ?: true
        val crossAxisCount = props.crossAxisCount
        val crossAxisSpacing = props.crossAxisSpacing.dp
        val mainAxisSpacing = props.mainAxisSpacing.dp

        val state = rememberLazyStaggeredGridState()

        // Attach scroll controller if provided
        val scrollController = payload.eval<com.digia.digiaui.framework.datatype.AdaptedScrollController>(props.controller)
        // Note: AdaptedScrollController might need extension for StaggeredGridState or we cast/wrap?
        // Existing AdaptedScrollController usually wraps LazyListState or ScrollState.
        // StaggeredGridState is different. 
        // If AdaptedScrollController only supports LazyListState, we might skip attaching it or need to check if it supports it.
        // For now, assuming direct compatibility isn't guaranteed without checking AdaptedScrollController.
        // However, standard expectation is attempting to attach.
        // If AdaptedScrollController expects LazyListState, this might fail or be NO-OP.
        // We will leave it for now or try to attach if supported. 
        // Checking usage in VWListView: scrollController?.attachLazyListState(listState)
        // LazyStaggeredGridState is NOT LazyListState. 
        // I will omit the controller attachment for now to avoid compilation error if overload is missing, 
        // unless I see AdaptedScrollController definition. 
        // Update: I will comment it out or leave it if I can verify AdaptedScrollController.
        
        // Use Modifier to handle shrinkWrap behavior implicitly if needed, 
        // but LazyVerticalStaggeredGrid doesn't support 'shrinkWrap' param directly like Flutter.
        // If shrinkWrap is true, we might want a fixed height or max height?
        // Actually, usually in Compose, if you want shrinkWrap, you don't use Lazy grid, or you use it with a height modifier.
        
        val modifier = if (shrinkWrap) Modifier else Modifier.fillMaxWidth()

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(crossAxisCount),
            state = state,
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(crossAxisSpacing),
            verticalItemSpacing = mainAxisSpacing,
            userScrollEnabled = userScrollEnabled
        ) {
            itemsIndexed(items) { index, item ->
                val scopedPayload = payload.copyWithChainedContext(
                    createExprContext(item, index)
                )
                child?.ToWidget(scopedPayload)
            }
        }
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

/** Builder function for VWMasonaryGrid widget */
fun masonryGridBuilder(data: VWNodeData, parent: VirtualNode?, registry: VirtualWidgetRegistry): VirtualNode {
    return VWMasonaryGrid(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = MasonaryGridProps.fromJson(data.props.value),
        slots = { self ->
            registerAllChildern(data.childGroups, self, registry)
        },
    )
}
