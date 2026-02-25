package com.digia.digiaui.framework.page

import LocalUIResources
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.digia.digiaexpr.std.StdLibFunctions
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.showToast.DUISnackbarHost
import com.digia.digiaui.framework.appstate.AppStateScopeContext
import com.digia.digiaui.framework.appstate.DUIAppState
import com.digia.digiaui.framework.datatype.DataTypeCreator
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
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
fun DUIPage(
        pageId: String,
        pageArgs: Map<String, Any?>?,
        pageDef: PageDefinition,
        registry: VirtualWidgetRegistry
) {

    val appStateContext = remember {
        AppStateScopeContext(
                values = DUIAppState.instance.all(),
                variables =
                        mutableMapOf<String, Any?>().apply {
                            putAll(StdLibFunctions.functions)
                            putAll(DigiaUIManager.getInstance().jsVars)
                        }
        )
    }
    /* ----------------------------------------
     * Resolve arguments
     * ---------------------------------------- */
    val resolvedPageArgs =
            pageDef.pageArgDefs?.mapValues { (key, variable) ->
                pageArgs?.get(key) ?: variable.defaultValue
            }
                    ?: emptyMap()

    val resolvedState =
            pageDef.initStateDefs?.mapValues { (_, variable) ->
                DataTypeCreator.create(
                        variable,
                        _createExprContext(resolvedPageArgs, null, appStateContext)
                )
            }
                    ?: emptyMap()

    val rootNode = pageDef.layout?.root ?: return


    StateScope(namespace = pageId, initialState = resolvedState) { stateContext ->
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val actionContext = LocalActionExecutor.current
        val resources = LocalUIResources.current

        val scopeContext =
                remember(resolvedPageArgs, stateContext) {
                    _createExprContext(
                            params = resolvedPageArgs,
                            stateContext = stateContext,
                            scopeContext = appStateContext
                    )
                }

        val renderPayload = RenderPayload(scopeContext = scopeContext)

        /* ----------------------------------------
         * ON PAGE LOAD (runs once)
         * ---------------------------------------- */
        // Use rememberSavable to track if page has loaded - persists across navigation
        val didLoad = rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(pageId) {
            if (!didLoad.value) {
                didLoad.value = true
                pageDef.onPageLoad?.let { actionFlow ->
                    try {
                        actionContext.execute(
                                context = context,
                                actionFlow = actionFlow,
                                scopeContext = scopeContext,
                                stateContext = stateContext,
                                resourcesProvider = resources,
                                scope = this
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        /* ----------------------------------------
         * BACK PRESS HANDLING
         * ---------------------------------------- */
        BackHandler(enabled = pageDef.onBackPress != null) {
            pageDef.onBackPress?.let { actionFlow ->
                scope.launch {
                    try {
                        actionContext.execute(
                                context = context,
                                actionFlow = actionFlow,
                                scopeContext = scopeContext,
                                stateContext = stateContext,
                                resourcesProvider = resources,
                                scope = this
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        /* ----------------------------------------
         * Render page
         * ---------------------------------------- */
        registry.createWidget(rootNode, null).ToWidget(renderPayload)
    }
}

@Composable
fun RootStateTreeProvider(content: @Composable () -> Unit) {
    val tree = remember { StateTree() } // single tree for entire app/session

    CompositionLocalProvider(LocalStateTree provides tree) { content() }

    DUISnackbarHost()
}

internal fun _createExprContext(
        params: Map<String, Any?>,
        stateContext: StateContext?,
        scopeContext: ScopeContext
): ScopeContext {

    val pageVariables =
            mapOf(
                    // Backward compatibility key
                    "pageParams" to params,
                    // New convention: spread the params map into the new map
                    *params.toList().toTypedArray()
            )

    if (stateContext == null) {
        return DefaultScopeContext(name = "", variables = pageVariables, enclosing = scopeContext)
    }

    return StateScopeContext(
            state = stateContext,
            variables = pageVariables,
            enclosing = scopeContext,
    )
}
