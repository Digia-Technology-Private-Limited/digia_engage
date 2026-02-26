package com.digia.digiaui.framework.widgets

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.SliverUtil
import com.digia.digiaui.utils.asSafe

/**
 * SmartScrollView widget properties
 */
data class SmartScrollViewProps(
    val dataSource: Any? = null,
    val controller: ExprOr<Any>? = null,
    val isReverse: ExprOr<Boolean>? = null,
    val scrollDirection: String? = null,
    val allowScroll: Boolean? = null
) {
    companion object {
        fun fromJson(json: JsonLike): SmartScrollViewProps {
            return SmartScrollViewProps(
                dataSource = json["dataSource"],
                controller = ExprOr.fromJson( json["controller"]),
                isReverse = ExprOr.fromJson(json["isReverse"]),
                scrollDirection = asSafe<String>( json["scrollDirection"]) ,
                allowScroll = asSafe<Boolean>(json["allowScroll"])
            )
        }
    }
}

/**
 * Virtual SmartScrollView widget
 *
 * Renders a scrollable grid of items from a data source or children.
 */
class VWSmartScrollView(
    refName: String? = null,
    commonProps: CommonProps? = null,
    props: SmartScrollViewProps,
    parent: VirtualNode? = null,
    slots: ((VirtualCompositeNode<SmartScrollViewProps>) -> Map<String, List<VirtualNode>>?)? = null,
    parentProps: Props? = null
) : VirtualCompositeNode<SmartScrollViewProps>(
    props = props,
    commonProps = commonProps,
    parentProps = parentProps,
    parent = parent,
    refName = refName,
    _slots = slots
) {

    val shouldRepeatChild: Boolean get() = props.dataSource != null

    @Composable
    override fun Render(payload: RenderPayload) {
        val controller = payload.evalExpr(props.controller)
        val isReverse = payload.evalExpr(props.isReverse) ?: false
        val allowScroll = props.allowScroll ?: true
        val maxSpan = computeMaxSpan(children, child, shouldRepeatChild)

        LazyVerticalGrid(
            columns = GridCells.Fixed(maxSpan),
            state = controller as? androidx.compose.foundation.lazy.grid.LazyGridState ?: rememberLazyGridState(),
            reverseLayout = isReverse,
            userScrollEnabled = allowScroll
        ) {
            if (shouldRepeatChild && child != null) {
                val items = payload.eval<List<Any>>(props.dataSource) ?: emptyList()

//                itemsIndexed(items, span = { index, item -> GridItemSpan(maxSpan) }) { index, item ->
//                    // In here, 'this' is LazyGridItemScope
//                    val scopedPayload = payload.copyWithChainedContext(_createExprContext(item, index))
//                    val adder = SliverUtil.convertToSliver(child!!, scopedPayload, maxSpan)
//
//                    // Fix: Use the outer scope (LazyGridScope)
//                    adder(this@LazyVerticalGrid, scopedPayload)
//                }

                items.forEachIndexed { index, item ->
                    // Create the scoped payload for this specific item
                    val scopedPayload = payload.copyWithChainedContext(_createExprContext(item, index))

                    // Get the "adder" lambda from SliverUtil
                    val adder = SliverUtil.convertToSliver(child!!, scopedPayload, maxSpan)

                    // Call the adder. 'this' here is LazyGridScope, which is what SliverUtil expects.
                    adder(this, scopedPayload)
                }
            } else {
                children.forEach { child ->
                    val adder = SliverUtil.convertToSliver(child, payload, maxSpan)
                    adder(this, payload)
                }
            }
        }
    }

    private fun computeMaxSpan(children: List<VirtualNode>, child: VirtualNode?, shouldRepeatChild: Boolean): Int {
        val counts = mutableListOf<Int>()
        if (shouldRepeatChild && child is VWGridView) {
            counts.add(child.props.crossAxisCount ?: 2)
        } else {
            children.forEach { c ->
                if (c is VWGridView) {
                    counts.add(c.props.crossAxisCount ?: 2)
                }
            }
        }
        return if (counts.isEmpty()) 12 else lcm(counts)
    }

    private fun lcm(numbers: List<Int>): Int {
        if (numbers.isEmpty()) return 1
        return numbers.reduce { a, b -> a * b / gcd(a, b) }
    }

    private fun gcd(a: Int, b: Int): Int {
        return if (b == 0) a else gcd(b, a % b)
    }

    private fun _createExprContext(item: Any?, index: Int): ScopeContext {
        val smartScrollViewObj = mapOf(
            "currentItem" to item,
            "index" to index
        )
        return DefaultScopeContext(variables = smartScrollViewObj + (refName?.let { mapOf(it to smartScrollViewObj) } ?: emptyMap()))
    }
}


fun smartScrollViewBuilder(
    data: VWNodeData,
    parent: VirtualNode?,
    registry: VirtualWidgetRegistry
): VirtualNode {
    return VWSmartScrollView(
        refName = data.refName,
        commonProps = data.commonProps,
        parent = parent,
        parentProps = data.parentProps,
        props = SmartScrollViewProps.fromJson(data.props.value),
        slots = { self -> registerAllChildern(data.childGroups, self, registry) },
    )
}
