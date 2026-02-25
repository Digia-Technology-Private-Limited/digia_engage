package com.digia.digiaui.core.functions

import kotlinx.coroutines.Deferred

/**
 * Abstract interface for JavaScript function execution
 */
abstract class JSFunctions {
    /**
     * Call a JavaScript function synchronously
     * @param fnName Name of the function to call
     * @param data Data to pass to the function
     * @return Result from the JavaScript function
     */
    abstract fun callJs(fnName: String, data: Any?): Any?

    /**
     * Call a JavaScript function asynchronously
     * @param fnName Name of the function to call
     * @param data Data to pass to the function
     * @return Deferred result from the JavaScript function
     */
    abstract suspend fun callAsyncJs(fnName: String, data: Any?): Any?

    /**
     * Initialize JavaScript functions with the given strategy
     * @param strategy Initialization strategy (PreferRemote or PreferLocal)
     * @return True if initialization was successful
     */
    abstract suspend fun initFunctions(strategy: FunctionInitStrategy): Boolean

    companion object {
        /**
         * Get the JavaScript functions file name based on version
         * @param version Optional version number
         * @return File name for the functions file
         */
        fun getFunctionsFileName(version: Int?): String {
            return if (version == null) "functions.js" else "functions_v$version.js"
        }
    }
}

/**
 * Sealed class representing different function initialization strategies
 */
sealed class FunctionInitStrategy

/**
 * Strategy to prefer loading functions from a remote source
 * @param remotePath URL or path to the remote functions file
 * @param version Optional version number for the functions file
 */
data class PreferRemote(
    val remotePath: String,
    val version: Int?
) : FunctionInitStrategy()

/**
 * Strategy to prefer loading functions from a local source
 * @param localPath Local file path to the functions file
 */
data class PreferLocal(
    val localPath: String
) : FunctionInitStrategy()