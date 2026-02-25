package com.digia.digiaui.core.functions

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext

/**
 * Mobile implementation of JSFunctions using QuickJS for JavaScript execution
 * @param context Android context required for file operations
 */
class MobileJSFunctions(context: Context) : JSFunctions() {
    
    private val gson = Gson()
    private var quickJSContext: QuickJSContext? = null
    private var jsFile: String = ""
    private val appContext: Context = context.applicationContext
    private val quickJSDispatcher = newSingleThreadContext("QuickJS")
    
    companion object {
        private const val TAG = "MobileJSFunctions"
    }

    override suspend fun initFunctions(strategy: FunctionInitStrategy): Boolean {
        // Initialize QuickJS loader
        withContext(quickJSDispatcher) {
            QuickJSLoader.init()
        }
        log("QuickJS Loader initialized")
        
        return try {
            when (strategy) {
                is PreferRemote -> {
                    val fileName = JSFunctions.getFunctionsFileName(strategy.version)
                    val file = File(appContext.filesDir, fileName)
                    val fileExists = strategy.version != null && file.exists()
                    
                    if (!fileExists) {
                        val downloaded = downloadFile(strategy.remotePath, file)
                        if (!downloaded) return false
                    }
                    
                    jsFile = file.readText()
                    withContext(quickJSDispatcher) {
                        initializeRuntime()
                    }
                    true
                }
                is PreferLocal -> {
                    jsFile = withContext(Dispatchers.IO) {
                        appContext.assets.open(strategy.localPath).bufferedReader().use { it.readText() }
                    }
                    withContext(quickJSDispatcher) {
                        initializeRuntime()
                    }
                    true
                }
            }
        } catch (e: Exception) {
            logError("File not found or initialization failed", e)
            false
        }
    }

    private fun initializeRuntime() {
        try {
            // Close existing context if any
            quickJSContext?.close()
            
            // Create new QuickJS context
            quickJSContext = QuickJSContext.create()
            
            // Evaluate the JavaScript file
            quickJSContext?.evaluate(jsFile)
            
            log("QuickJS runtime initialized successfully")
        } catch (e: Exception) {
            logError("Error initializing QuickJS runtime", e)
            throw e
        }
    }

    override fun callJs(fnName: String, data: Any?): Any? {
        return runBlocking(quickJSDispatcher) {
            try {
                val runtime = quickJSContext 
                    ?: throw IllegalStateException("QuickJS runtime not initialized. Call initFunctions() first.")
                
                // Encode input to JSON
                val input = gson.toJson(data)
                
                // Evaluate JavaScript function
                val jsCode = "JSON.stringify($fnName($input))"
                val result = runtime.evaluate(jsCode) as? String
                
                // Check if result is an error
                if (result != null && result.contains("Error")) {
                    handleJsError(fnName, input, result)
                    throw Exception("Error running function $fnName: $result")
                }
                
                // Decode result from JSON
                if (result == null) return@runBlocking null
                gson.fromJson(result, Any::class.java)
            } catch (e: Exception) {
                logError("Error calling JS function: $fnName", e)
                throw e
            }
        }
    }

    override suspend fun callAsyncJs(fnName: String, data: Any?): Any? {
        return withContext(quickJSDispatcher) {
            try {
                val runtime = quickJSContext 
                    ?: throw IllegalStateException("QuickJS runtime not initialized. Call initFunctions() first.")
                
                // Encode input to JSON
                val input = gson.toJson(data)
                
                // Evaluate JavaScript async function
                // Note: QuickJS handles promises differently than V8
                // This is a simplified version - you may need to adjust based on your JS code
                val jsCode = """
                    (async function() {
                        const result = await $fnName($input);
                        return JSON.stringify(result);
                    })();
                """.trimIndent()
                
                val result = runtime.evaluate(jsCode) as? String
                
                // Check if result is an error
                if (result != null && result.contains("Error")) {
                    handleJsError(fnName, input, result)
                    throw Exception("Error running async function $fnName: $result")
                }
                
                // Decode result from JSON
                if (result == null) return@withContext null
                gson.fromJson(result, Any::class.java)
            } catch (e: Exception) {
                logError("Error calling async JS function: $fnName", e)
                throw e
            }
        }
    }

    private suspend fun downloadFile(url: String, destinationFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connect()
                
                connection.getInputStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                log("Successfully downloaded file from $url")
                true
            } catch (e: Exception) {
                logError("Error downloading file from $url", e)
                false
            }
        }
    }

    private fun handleJsError(fnName: String, input: String, error: String) {
        logError("--------------ERROR Running Function-----------")
        log("functionName ----> $fnName")
        log("input ----------> $input")
        logError("error -------> $error")
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    private fun logError(message: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(TAG, message, error)
        } else {
            Log.e(TAG, message)
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        quickJSContext?.close()
        quickJSContext = null
        quickJSDispatcher.close()
        log("QuickJS runtime destroyed")
    }
}

/**
 * Create a new instance of JSFunctions
 * @param context Android context required for initialization
 * @return MobileJSFunctions instance
 */
fun getJSFunction(context: Context): JSFunctions = MobileJSFunctions(context)