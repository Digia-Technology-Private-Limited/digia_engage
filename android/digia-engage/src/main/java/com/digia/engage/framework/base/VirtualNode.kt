package com.digia.engage.framework.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.digia.engage.framework.RenderPayload
import com.digia.engage.framework.models.Props

/** Base class for all virtual widgets Mirrors Flutter VirtualWidget */
import java.lang.ref.WeakReference

abstract class VirtualNode(
    val refName: String?,
    parent: VirtualNode? = null,
    var parentProps: Props? = null
) {

    private val parentRef: WeakReference<VirtualNode>? =
        parent?.let { WeakReference(it) }

    var parent: VirtualNode? = null
        get() = parentRef?.get()

    /** Render widget */
    @Composable
    abstract fun Render(payload: RenderPayload)


    @Composable
    abstract fun Modifier.buildModifier(payload: RenderPayload): Modifier


    /** Empty widget (like SizedBox.shrink) */
    @Composable
    open fun Empty() {}

    /** Entry point */
    @Composable
    open fun ToWidget(payload: RenderPayload) {
        RenderNode(widget = this, payload = payload)
    }
    @Composable
   open fun  ToWidgetWithModifier(payload: RenderPayload, modifier: Modifier) {
        RenderNode(widget = this, payload = payload)
    }
}


sealed class RenderResult {
    data class Ok(val content: @Composable () -> Unit) : RenderResult()
    data class Error(val error: Throwable) : RenderResult()
}

fun VirtualNode.tryBuild(payload: RenderPayload): RenderResult =
    try {
        RenderResult.Ok { Render(payload) }
    } catch (t: Throwable) {
        RenderResult.Error(t)
    }


@Composable
fun RenderNode(widget: VirtualNode, payload: RenderPayload) {
    when (val result = widget.tryBuild(payload)) {
        is RenderResult.Ok -> result.content()
        is RenderResult.Error -> {
            throw result.error
        }
    }
}

