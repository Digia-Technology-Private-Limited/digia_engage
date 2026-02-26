package com.digia.digiaui.framework.datatype.methodbinding

import com.digia.digiaui.framework.utils.JsonLike

/**
 * Abstract command class for executing methods on instances of type T.
 * 
 * Implementations define how to execute a specific method on an instance,
 * taking a map of named arguments.
 */
abstract class MethodCommand<T> {
    /**
     * Executes the command on the given instance with the provided arguments.
     * 
     * @param instance The instance to execute the method on
     * @param args Map of argument names to values
     */
    abstract fun run(instance: T, args: JsonLike)
}