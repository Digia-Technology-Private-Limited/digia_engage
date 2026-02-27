package com.digia.digiaui.framework.datatype.methodbinding

import com.digia.digiaui.framework.datatype.methodbinding.adaptedFile.registerMethodCommandsForFile
import com.digia.digiaui.framework.datatype.methodbinding.apiCancelToken.registerMethodCommandsForApiCancelToken
import com.digia.digiaui.framework.datatype.methodbinding.asyncController.registerMethodCommandsForAsyncController
import com.digia.digiaui.framework.datatype.methodbinding.pageController.registerMethodCommandsForPageController
import com.digia.digiaui.framework.datatype.methodbinding.scrollController.registerMethodCommandsForScrollController
import com.digia.digiaui.framework.datatype.methodbinding.streamController.registerMethodCommandsForStreamController
import com.digia.digiaui.framework.datatype.methodbinding.textFieldController.registerMethodCommandsForTextFieldController
import com.digia.digiaui.framework.datatype.methodbinding.timerController.registerMethodCommandsForTimerController
import kotlin.reflect.KClass

/**
 * Registry for method command bindings.
 * 
 * This class maintains a mapping of types to their available method commands,
 * allowing dynamic method invocation through the command pattern.
 */
class MethodBindingRegistry {
    
    val _bindings = mutableMapOf<KClass<*>, Map<String, MethodCommand<*>>>()
    
    init {
        registerBindings(this)
    }
    
    /**
     * Registers a map of method commands for a given type.
     * 
     * @param T The type to register commands for
     * @param commands Map of method names to their command implementations
     */
    inline fun <reified T : Any> registerMethods(commands: Map<String, MethodCommand<T>>) {
        _bindings[T::class] = commands
    }
    
    /**
     * Executes a command by name on the given instance.
     * 
     * @param instance The instance to execute the command on
     * @param methodName The name of the method to execute
     * @param args Map of argument names to values
     * @throws IllegalArgumentException if the method is not found
     */
    fun <T : Any> execute(instance: T, methodName: String, args: Map<String, Any?> = emptyMap()) {
        val instanceClass = instance::class
        
        if (_bindings.containsKey(instanceClass)) {
            val methodCommands = _bindings[instanceClass]
            if (methodCommands?.containsKey(methodName) == true) {
                @Suppress("UNCHECKED_CAST")
                val command = methodCommands[methodName] as? MethodCommand<T>
                command?.run(instance, args)
                return
            }
        }
        
        throw IllegalArgumentException(
            "Method $methodName not found on instance of type: ${instanceClass.simpleName}"
        )
    }
    
    /**
     * Clears all registered bindings.
     */
    fun dispose() {
        _bindings.clear()
    }
}

/**
 * Override this function to register method bindings for different types.
 *
 * Example:
 * ```kotlin
 * fun registerBindings(registry: MethodBindingRegistry) {
 *     registry.registerMethods<TimerController>(mapOf(
 *         "start" to object : MethodCommand<TimerController>() {
 *             override fun run(instance: TimerController, args: Map<String, Any?>) {
 *                 instance.start()
 *             }
 *         },
 *         "pause" to object : MethodCommand<TimerController>() {
 *             override fun run(instance: TimerController, args: Map<String, Any?>) {
 *                 instance.pause()
 *             }
 *         }
 *     ))
 * }
 * ```
 */
fun registerBindings(registry: MethodBindingRegistry) {
    // Implement registration of method bindings for different types here.
    registerMethodCommandsForTimerController(registry)
    registerMethodCommandsForStreamController(registry)
    registerMethodCommandsForTextFieldController(registry)
    registerMethodCommandsForFile(registry)
    registerMethodCommandsForApiCancelToken(registry)
    registerMethodCommandsForAsyncController(registry)
    registerMethodCommandsForScrollController(registry)
    registerMethodCommandsForPageController(registry)
}
