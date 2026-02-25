package com.digia.digiaui.framework.actions

import LocalApiModels
import LocalUIResources
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.Action
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.actions.base.ActionId
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.network.APIModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


var LocalActionExecutor= compositionLocalOf { ActionExecutor() }


@Composable
fun ActionProvider(
    actionExecutor: ActionExecutor,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalActionExecutor provides actionExecutor,
    ) { content() }
}

/** Action executor - executes action flows */
class ActionExecutor(
        private val processorFactory: ActionProcessorFactory = ActionProcessorFactory()
) {
    /** Execute an action flow */
    fun execute(
            context: Context,
            actionFlow: ActionFlow,
            scopeContext: ScopeContext?,
            stateContext: com.digia.digiaui.framework.state.StateContext?,
        resourcesProvider: UIResources? = null,
            scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    ): Job {
    return scope.launch {

            
            // Execute each action sequentially
            for (action in actionFlow.actions) {
                // Generate action ID
                action.actionId = ActionId(java.util.UUID.randomUUID().toString())

                // Check if action is disabled
                val disabled = action.disableActionIf?.evaluate(scopeContext) ?: false
                if (disabled) {
                    continue
                }

                // Get processor and execute
                try {
                    @Suppress("UNCHECKED_CAST")
                    val processor = processorFactory.getProcessor(action, registry = MethodBindingRegistry()) as ActionProcessor<Action>
                    processor.execute(
                            context = context,
                            action = action,
                            scopeContext = scopeContext,
                            stateContext = stateContext,
                            resourcesProvider = resourcesProvider,
                            id = action.actionId!!.id
                    )
                } catch (e: Exception) {
                    // Log error (in production, use proper logging)
                    println("Error executing action: ${e.message}")
                }
            }
        }
    }
}
