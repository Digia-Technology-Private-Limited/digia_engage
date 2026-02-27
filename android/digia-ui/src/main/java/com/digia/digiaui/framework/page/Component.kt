package com.digia.digiaui.framework.component


import LocalUIResources
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaexpr.std.StdLibFunctions
import com.digia.digiaui.framework.DefaultVirtualWidgetRegistry
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.appstate.AppStateScopeContext
import com.digia.digiaui.framework.appstate.DUIAppState
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ComponentDefinition
import com.digia.digiaui.framework.models.PageDefinition
import com.digia.digiaui.framework.state.LocalStateTree
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.state.StateScope
import com.digia.digiaui.framework.state.StateScopeContext
import com.digia.digiaui.framework.state.StateTree
import com.digia.digiaui.init.DigiaUIManager
import kotlinx.coroutines.launch

/** DUIPage - renders a page from its definition Mirrors Flutter DUIPage */
@Composable
fun DUIComponent(
    componentId: String,
    args: Map<String, Any?>?,
    componentDef: ComponentDefinition,
    registry: DefaultVirtualWidgetRegistry,
    resources: UIResources
) {
    /* ----------------------------------------
     * Resolve arguments
     * ---------------------------------------- */
    val resolvedPageArgs =
        componentDef.argDefs?.mapValues { (key, variable) ->
            args?.get(key) ?: variable.defaultValue
        } ?: emptyMap()

    val resolvedState =
        componentDef.initStateDefs?.mapValues { (_, variable) ->
            variable.defaultValue
        } ?: emptyMap()

    val rootNode = componentDef.layout?.root ?: return

    val virtualWidget = remember(rootNode) {
        registry.createWidget(rootNode, null)
    }

    val appStateContext = remember {
        AppStateScopeContext(
            values = DUIAppState.instance.all(),
            variables = mutableMapOf<String, Any?>().apply {
                putAll(StdLibFunctions.functions)
                putAll(DigiaUIManager.getInstance().jsVars)
            }
        )
    }

    val didLoad = remember { mutableStateOf(false) }


    RootStateTreeProvider {
        StateScope(
            namespace = componentDef.id,
            initialState = resolvedState
        ) { stateContext ->

            // Depend on version to recreate scopeContext when state changes
            val version = stateContext.version

            val scopeContext = remember(resolvedPageArgs, stateContext, version) {
                _createExprContext(
                    params = resolvedPageArgs,
                    stateContext = stateContext,
                    scopeContext = appStateContext
                )
            }

            val renderPayload=RenderPayload(
                scopeContext = scopeContext
            )


            /* ----------------------------------------
             * Render page
             * ---------------------------------------- */
            virtualWidget.ToWidget(
                renderPayload
            )

        }
    }
}



@Composable
fun RootStateTreeProvider(content: @Composable () -> Unit) {
    val tree = remember { StateTree() } // single tree for entire app/session

    CompositionLocalProvider(
        LocalStateTree provides tree
    ) {
        content()
    }
}


internal fun _createExprContext(params:Map<String, Any?>,stateContext: StateContext?
                                ,scopeContext: ScopeContext
): ScopeContext {

    val pageVariables = mapOf(
        // Backward compatibility key
        "pageParams" to params,
        // New convention: spread the params map into the new map
        *params.toList().toTypedArray()
    )

    if (stateContext == null) {
        return DefaultScopeContext(
            name = "",
            variables = pageVariables,
            enclosing =scopeContext
        );
    }

    return StateScopeContext(
        state= stateContext,
        variables = pageVariables,
        enclosing= scopeContext,
    );

}