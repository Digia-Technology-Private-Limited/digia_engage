package com.digia.digiaui.framework.utils


import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.base.VirtualNode
//import com.digia.digiaui.framework.base.VirtualSliver
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.widgets.*
import kotlin.reflect.KClass

object SliverUtil {
    val boxToSliverMap: Map<KClass<out Any>, (LazyGridScope, VirtualNode, RenderPayload, Int) -> Unit> = mapOf(
        VWListView::class to { scope, widget, payload, maxSpan ->
            val vw = widget as VWListView
            val items = payload.eval<List<Any>>(vw.props.dataSource) ?: emptyList()
            scope.items(items.size, span = {GridItemSpan(maxSpan)}) { index ->
                val item = items[index]
                val scopedPayload = payload.copyWithChainedContext(createExprContext(item, index, vw.refName))
                vw.child?.ToWidget(scopedPayload)
//                convertToSliver(vw.child!!, scopedPayload, maxSpan)(scope, scopedPayload)

            }
        },
        VWGridView::class to { scope, widget, payload, maxSpan ->
            val vw = widget as VWGridView
            val items = payload.eval<List<Any>>(vw.props.dataSource) ?: emptyList()
            val crossAxisCount = vw.props.crossAxisCount ?: 2
            val span = maxSpan / crossAxisCount
            scope.items(items.size, span = {GridItemSpan(span)}) { index ->
                val item = items[index]
                val scopedPayload = payload.copyWithChainedContext(createExprContext(item, index, vw.refName))
                vw.child?.ToWidget(scopedPayload)
//                convertToSliver(vw.child!!, scopedPayload, maxSpan)(scope, scopedPayload)
            }
        },
        VWPaginatedListView::class to { scope, widget, payload, maxSpan ->
            val vw = widget as VWPaginatedListView
            val items = payload.eval<List<Any>>(vw.props.dataSource) ?: emptyList()
            scope.items(items.size, span = {GridItemSpan(maxSpan)}) { index ->
                val item = items[index]
                val scopedPayload = payload.copyWithChainedContext(createExprContext(item, index, vw.refName))
                vw.child?.ToWidget(scopedPayload)
//                convertToSliver(vw.child!!, scopedPayload, maxSpan)(scope, scopedPayload)
            }
        },
        VWConditionalBuilder::class to { scope, widget, payload, maxSpan ->
            val evalChild = (widget as VWConditionalBuilder).getEvalChild(payload)
            if (evalChild != null) {
                val adder = convertToSliver(evalChild, payload, maxSpan)
                adder(scope, payload)
            }
        },
    )

    /// Converts a widget to a sliver by adding items to the LazyGridScope
    /// Returns a function that adds the items with appropriate spans
    @Suppress("UNUSED_PARAMETER")
    fun convertToSliver(widget: VirtualNode, payload: RenderPayload, maxSpan: Int): (LazyGridScope, RenderPayload) -> Unit {
//        if (widget is VirtualSliver<*>) {
//            // For VirtualSliver, render as item with max span
//            return { scope, p -> scope.item(span = { GridItemSpan(maxSpan) }) { widget.ToWidget(p) } }
//        }

        val converter = boxToSliverMap[widget::class]
        if (converter != null) {
            return { scope, p -> converter(scope, widget, p, maxSpan) }
        }

        // Default fallback: render as item with max span
        return { scope, p -> scope.item(span = { GridItemSpan(maxSpan) }) { widget.ToWidget(p) } }
    }

    private fun createExprContext(item: Any?, index: Int, refName: String?): DefaultScopeContext {
        val obj = mapOf("currentItem" to item, "index" to index)
        return DefaultScopeContext(variables = obj + (refName?.let { mapOf(it to obj) } ?: emptyMap()))
    }
}