package com.digia.digiaui.framework.actions.ControlObject

import android.content.Context
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.state.StateContext

/**
 * Processor for ControlObjectAction.
 * 
 * Evaluates the data type instance, evaluates all arguments, and executes
 * the specified method using the MethodBindingRegistry.
 */
class ControlObjectProcessor(
    private val registry: MethodBindingRegistry
) : ActionProcessor<ControlObjectAction>() {

    override suspend fun execute(
        context: Context,
        action: ControlObjectAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ): Any? {
        // Evaluate the data type expression to get the object instance
        val obj = action.dataType?.evaluate<Any>(scopeContext)

        if (obj == null) {
            val error = "Object of type ${action.dataType} not found"
            throw IllegalStateException(error)
        }

        // Evaluate all arguments
        val evaluatedArgs = action.args?.mapValues { (_, v) ->
            v?.evaluate<Any>(scopeContext)
        } ?: emptyMap()

        // Execute the method through the registry
        try {
            registry.execute(obj, action.method, evaluatedArgs)
        } catch (e: Exception) {
            throw e
        }

        return null
    }
}