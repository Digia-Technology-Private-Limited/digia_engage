package com.digia.digiaui.framework.datatype

import com.digia.digiaui.framework.datatype.adaptedfile.AdaptedFile
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.expression.evaluate
import com.digia.digiaui.framework.expression.evaluateNestedExpressions
import com.digia.digiaui.framework.utils.NumUtil
import com.digia.digiaui.framework.widgets.timer.TimerController
import com.digia.digiaui.framework.widgets.AsyncController
import com.digia.digiaui.framework.widgets.TextController
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Factory class for creating instances of different data types based on Variable definitions.
 * 
 * This class provides a centralized way to instantiate data type objects from Variable
 * definitions, handling default values and scope context evaluation.
 * 
 * Usage:
 * ```kotlin
 * val variable = Variable(
 *     type = DataType.STRING,
 *     name = "myString",
 *     defaultValue = "Hello"
 * )
 * val instance = DataTypeCreator.create(variable, scopeContext)
 * ```
 */
object DataTypeCreator {
    
    /**
     * Creates an instance of the specified data type.
     * 
     * @param def The Variable definition containing type and default value information
     * @param scopeContext Optional scope context for evaluating expressions in default values
     * @return An instance of the specified data type, or null if creation fails
     */
    fun create(def: Variable, scopeContext: ScopeContext? = null): Any? {
        return when (def.type) {
            // Primitive types - evaluate default value with scope context
            DataType.STRING -> evaluate<String>(def.defaultValue, scopeContext)
            DataType.NUMBER -> evaluate<Number>(def.defaultValue, scopeContext)
            DataType.BOOLEAN -> evaluate<Boolean>(def.defaultValue, scopeContext)
            
            // Complex JSON types - evaluate nested expressions
            DataType.JSON -> evaluateNestedExpressions(def.defaultValue, scopeContext)
            DataType.JSON_ARRAY -> evaluateNestedExpressions(def.defaultValue, scopeContext)
            
            // Scroll controller for programmatic scrolling
            DataType.SCROLL_CONTROLLER -> AdaptedScrollController()
            
            // File handling
            DataType.FILE -> AdaptedFile()
            
            // Stream controller using Kotlin Flow
            DataType.STREAM_CONTROLLER -> MutableStateFlow<Any?>(
                evaluate<Any>(def.defaultValue, scopeContext)
            )
            
            // Async controller for future/async operations
            DataType.ASYNC_CONTROLLER -> AsyncController<Any?>()
            
            // Text editing controller with initial value
            DataType.TEXT_EDITING_CONTROLLER -> {
                val initialText = evaluate<String>(def.defaultValue, scopeContext) ?: ""
                TextController(initialText)
            }
            
            // Timer controller for countdown/countup operations
            DataType.TIMER_CONTROLLER -> createTimerController(def, scopeContext)
            
            // API cancel token for request cancellation
            DataType.API_CANCEL_TOKEN -> CancelToken()
            
            // Page controller - needs implementation
            DataType.PAGE_CONTROLLER -> AdaptedPageController()
            
            // Action - not instantiable, return null
            DataType.ACTION -> null
            
            // Story controller - needs implementation
            DataType.STORY_CONTROLLER -> null
        }
    }
    
    /**
     * Creates a timer controller with configuration from default value.
     * 
     * Expected defaultValue format:
     * ```json
     * {
     *   "initialValue": 0,
     *   "updateInterval": 1000,
     *   "isCountDown": false,
     *   "duration": 60
     * }
     * ```
     */
    private fun createTimerController(def: Variable, scopeContext: ScopeContext?): TimerController {
        val config = evaluateNestedExpressions(def.defaultValue, scopeContext) as? Map<*, *>
        
        // Extract configuration with defaults
        val initialValue = NumUtil.toInt(config?.get("initialValue")) ?: 0
        val updateInterval = NumUtil.toDouble(config?.get("updateInterval"))?.toLong() ?: 1000L
        val isCountDown = config?.get("isCountDown") as? Boolean ?: false
        val duration = NumUtil.toInt(config?.get("duration")) ?: 60
        
        return TimerController(
            initialValue = initialValue,
            updateInterval = updateInterval,
            isCountDown = isCountDown,
            duration = duration
        )
    }
    
}

