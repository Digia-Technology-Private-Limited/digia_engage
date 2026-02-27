package com.digia.digiaui.framework

import LocalUIResources
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.expression.evaluate
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.state.LocalStateContextProvider
import com.digia.digiaui.framework.state.StateContext
import com.digia.digiaui.framework.state.StateScopeContext
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.network.APIModel
import defaultTextStyle
import makeTextStyle
import resourceApiModel
import resourceColor
import resourceFontFactory

data class RenderPayload(
    val scopeContext: ScopeContext?,
    val widgetHierarchy: List<String> = emptyList(),
    val currentEntityId: String? = null
) {

    /* ---------------- Expression evaluation ---------------- */

    /**
     * Evaluate an expression (NON-REACTIVE).
     * Use this for:
     * - Static props that don't change
     * - Action expressions
     * - Initial values
     * 
     * For reactive state values in widgets, use observeState() instead.
     */
    inline fun <reified T : Any> evalExpr(
        expr: ExprOr<T>?,
        noinline decoder: ((Any) -> T?)? = null
    ): T? = expr?.evaluate(scopeContext, decoder)

    inline fun <reified T : Any> eval(
        expression: Any?,
        scopeContext: ScopeContext? = null,
        noinline decoder: ((Any?) -> T?)? = null
    ): T? = evaluate(expression, chainExprContext(scopeContext), decoder)


    @Composable
    inline fun <reified T : Any> evalObserve(
        expression: Any?,
        scopeContext: ScopeContext? = null,
        noinline decoder: ((Any?) -> T?)? = null
    ): T? {
        val stateContext = LocalStateContextProvider.current
        stateContext?.startTracking()
        val data = evaluate(expression, chainExprContext(scopeContext), decoder)
        val trackList = stateContext?.stopTracking()
        trackList?.forEach { e -> stateContext?.observe(e) }
        return data
    }

    @Composable
    inline fun <reified T : Any> evalObserve(
        expr: ExprOr<T>?,
        noinline decoder: ((Any) -> T?)? = null
    ): T? {
        val stateContext = LocalStateContextProvider.current ?: return null

        // 🔥 DerivedState anchors observation
        val value by remember(expr) {
            derivedStateOf {
                stateContext.startTracking()
                val result = expr?.evaluate(scopeContext, decoder)
                val deps = stateContext.stopTracking()

                // 🔥 READ ALL DEPENDENCY VERSIONS HERE
                deps.forEach { name ->
                    stateContext.observe(name)
                }

                result
            }
        }

        return value
    }


    /**
     * Executes an ActionFlow (sequence of actions).
     * This matches the Dart executeAction implementation.
     */
    fun executeAction(
        context: Context,
        actionFlow: ActionFlow?,
        actionExecutor: ActionExecutor,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        incomingScopeContext: ScopeContext? = null,
    ) {
        if (actionFlow == null) return

        // Chaining context ensures the action can see variables from the
        // current widget/row and the global state.
        val combinedContext = chainExprContext(incomingScopeContext)

        // Do NOT block the calling thread (often the UI thread).
        // Action execution is asynchronous and internally uses coroutines.
        actionExecutor.execute(
            context = context,
            actionFlow = actionFlow,
            scopeContext = combinedContext,
            stateContext = stateContext,
            resourcesProvider = resourcesProvider,
        )
    }


    /* ---------------- Hierarchy helpers ---------------- */

    fun withExtendedHierarchy(widgetName: String): RenderPayload =
        copy(widgetHierarchy = widgetHierarchy + widgetName)

    fun forComponent(componentId: String): RenderPayload =
        copy(
            widgetHierarchy = widgetHierarchy + componentId,
            currentEntityId = componentId
        )

    /* ---------------- Context chaining ---------------- */

    fun chainExprContext(incoming: ScopeContext?): ScopeContext? {
        if (incoming == null) return scopeContext
        if (scopeContext == null) return incoming

         incoming.addContextAtTail(scopeContext)
        return incoming
    }

    fun copyWithChainedContext(
        scopeContext: ScopeContext,
    ): RenderPayload =
        copy(
            scopeContext = chainExprContext(scopeContext)
        )
}





@Composable
fun RenderPayload.color(key: String): Color? =
    resourceColor(key)

@Composable
fun RenderPayload.apiModel(id: String): APIModel? =
    resourceApiModel(id)

@Composable
fun RenderPayload.fontFactory(): DUIFontFactory? =
    resourceFontFactory()

@Composable
fun RenderPayload.textStyle(
    token: JsonLike?,
    fallback: TextStyle? = defaultTextStyle
): TextStyle? =
    makeTextStyle(token, { expr -> eval<String>(expr) }, fallback)

@Composable
fun RenderPayload.evalColor(
    expression: Any?,
    scopeContext: ScopeContext? = null
): Color? =
    eval<String>(expression, scopeContext)?.let { resourceColor(it) }
