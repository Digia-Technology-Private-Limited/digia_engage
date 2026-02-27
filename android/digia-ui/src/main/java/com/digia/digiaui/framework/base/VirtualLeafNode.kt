package com.digia.digiaui.framework.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.utils.applyCommonProps
import com.digia.digiaui.framework.utils.toComposeAlignment

abstract class VirtualLeafNode<T>(
        val props: T,
        val commonProps: CommonProps?,
        parent: VirtualNode?,
        refName: String?,
        parentProps: Props? = null
) : VirtualNode(refName, parent, parentProps) {

    private var parentModifier: Modifier = Modifier

    @Composable
    override fun ToWidget(payload: RenderPayload) {

        val extendedPayload = refName?.let { payload.withExtendedHierarchy(it) } ?: payload

        val isVisible = commonProps?.visibility?.let { extendedPayload.evalExpr(it) } ?: true

        if (!isVisible) return

        val alignment = commonProps?.align?.toComposeAlignment()

        if (alignment != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
                Render(extendedPayload)
            }
            return
        }

        Render(extendedPayload)
    }

    @Composable
    override fun ToWidgetWithModifier(payload: RenderPayload, modifier: Modifier) {
        parentModifier = modifier
        ToWidget(payload)
    }

    @Composable
    override fun Modifier.buildModifier(payload: RenderPayload): Modifier {
        return this.applyCommonProps(payload, commonProps).let {
            parentModifier.let { parentMod -> it.then(parentMod) }
        }
    }
}
