package com.digia.digiaui.framework.actions.executeCallBack

import android.content.Context
import android.util.Log
import com.digia.digiaui.framework.UIResources
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.base.ActionFlow
import com.digia.digiaui.framework.actions.base.ActionProcessor
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.state.StateContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Processor for ExecuteCallBackAction
 * 
 * Executes a callback ActionFlow with updated arguments.
 * The actionName is evaluated to get an ActionFlow (as Map or JSON string),
 * and argUpdates provide parameter overrides passed in as 'args' context.
 */
class ExecuteCallBackProcessor : ActionProcessor<ExecuteCallBackAction>() {
    private val actionExecutor = ActionExecutor()

    override suspend fun execute(
        context: Context,
        action: ExecuteCallBackAction,
        scopeContext: ScopeContext?,
        stateContext: StateContext?,
        resourcesProvider: UIResources?,
        id: String
    ): Any? {
        try {
            // Evaluate argUpdates to create resolved arguments map
            val resolvedArgs = convertArgUpdatesToMap(action.argUpdates, scopeContext)

            // Evaluate actionName to get the ActionFlow
            val evaluatedActionName = action.actionName?.evaluate<Any>(scopeContext)
            
            val actionFlow = parseActionFlow(evaluatedActionName)

            if (actionFlow == null) {
                Log.e("ExecuteCallback", "Failed to parse ActionFlow from actionName")
                return null
            }

            // Create a new scope context with 'args' containing resolved arguments
            // This mimics Flutter's DefaultScopeContext with variables: {'args': resolvedArgs}
            val callbackContext = DefaultScopeContext(
                variables = mapOf("args" to resolvedArgs),
                enclosing = scopeContext
            )

            // Execute the action flow with the new context
            actionExecutor.execute(
                context = context,
                actionFlow = actionFlow,
                scopeContext = callbackContext,
                stateContext = stateContext,
                resourcesProvider = resourcesProvider,
            )

            return null
        } catch (e: Exception) {
            Log.e("ExecuteCallback", "Error executing callback: ${e.message}", e)
            throw e
        }
    }

    /**
     * Parse ActionFlow from evaluated actionName.
     * Handles both Map (JSON structure) and String (JSON string) formats.
     */
    private fun parseActionFlow(evaluatedActionName: Any?): ActionFlow? {
        return when (evaluatedActionName) {
            null -> null
            
            // If it's already a Map, treat it as ActionFlow JSON
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                ActionFlow.fromJson(evaluatedActionName as? Map<String, Any>)
            }
            
            // If it's a String, try parsing as JSON
            is String -> {
                try {
                    val jsonObject = JSONObject(evaluatedActionName)
                    val jsonMap = jsonObject.toMap()
                    ActionFlow.fromJson(jsonMap)
                } catch (e: Exception) {
                    Log.e("ExecuteCallback", "Failed to parse JSON string: ${e.message}")
                    null
                }
            }
            
            else -> {
                Log.e("ExecuteCallback", "Unsupported actionName type: ${evaluatedActionName::class.java}")
                null
            }
        }
    }

    /**
     * Convert ArgUpdate list to a Map for scope context
     */
    private fun convertArgUpdatesToMap(
        updates: List<ArgUpdate>,
        scopeContext: ScopeContext?
    ): Map<String, Any?> {
        return updates.associate { update ->
            update.argName to update.argValue?.evaluate(scopeContext)
        }
    }
}

/**
 * Extension function to convert JSONObject to Map
 */
private fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = this.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        var value: Any? = this.get(key)

        // Recursively convert nested JSONObjects and JSONArrays
        value = when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }

        map[key] = value
    }
    return map
}

/**
 * Extension function to convert JSONArray to List
 */
private fun JSONArray.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until this.length()) {
        var value: Any? = this.get(i)
        
        value = when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            JSONObject.NULL -> null
            else -> value
        }
        
        list.add(value)
    }
    return list
}
