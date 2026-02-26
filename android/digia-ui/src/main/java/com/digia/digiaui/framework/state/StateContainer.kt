package com.digia.digiaui.framework.state

import LocalUIResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.datatype.DataTypeCreator
import com.digia.digiaui.framework.datatype.Variable
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.navigation.NavigationManager

/**
 * Virtual State Container Widget
 *
 * Provides scoped state management for child widgets. Similar to Flutter's StatefulScopeWidget.
 * Creates a StateContext with initial state and provides it to child widgets through the render
 * payload's scope context.
 */
class VWStateContainer(
        refName: String?,
        parent: VirtualNode?,
        parentProps: Props?,
        private val initStateDefs: Map<String, Variable>,
        private val childGroups: Map<String, List<VirtualNode>>?
) : VirtualNode(refName, parent, parentProps) {

    private val child: VirtualNode? = childGroups?.entries?.firstOrNull()?.value?.firstOrNull()

    @Composable
    override fun Render(payload: RenderPayload) {
        val child =
                child
                        ?: run {
                            Empty()
                            return
                        }

        // ✅ Evaluate initial state ONCE
        val resolvedState =
                initStateDefs.mapValues { DataTypeCreator.create(it.value, payload.scopeContext) }

        StateScope(namespace = refName, initialState = resolvedState) { stateContext ->

            val scopeContext = _createExprContext(stateContext)

            val containerPayload = payload.copyWithChainedContext(scopeContext)

            stateContext.Version()

            child.ToWidget(payload = containerPayload)
        }
    }

    fun _createExprContext(stateContext: StateContext): StateScopeContext {
        return StateScopeContext(
                state = stateContext,
        )
    }

    @Composable
    override fun Modifier.buildModifier(payload: RenderPayload): Modifier {
        return this // State containers don't modify the layout
    }
}
