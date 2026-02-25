package com.digia.digiaui.framework.base

import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props


abstract class VirtualCompositeNode<T>(
    props: T,
    commonProps: CommonProps?,
    parent: VirtualNode?,
    refName: String?,
    parentProps: Props? = null,
    private val _slots: ((VirtualCompositeNode<T>) -> Map<String, List<VirtualNode>>?)? = null
) : VirtualLeafNode<T>(
    props = props,
    commonProps = commonProps,
    parent = parent,
    refName = refName,
    parentProps = parentProps
) {

    val slots: Map<String, List<VirtualNode>>?
        get() = _slots?.invoke(this)

    val child: VirtualNode?
        get() = slot("child")

    val children: List<VirtualNode>
        get() = slotChildren("children")

    fun slot(key: String): VirtualNode? {
        return slots?.get(key)?.firstOrNull()
    }

    fun slotChildren(key: String): List<VirtualNode> {
        return slots?.get(key) ?: emptyList()
    }
}
