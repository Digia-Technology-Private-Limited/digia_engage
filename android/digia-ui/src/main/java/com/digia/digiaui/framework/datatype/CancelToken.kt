package com.digia.digiaui.framework.datatype

import com.digia.digiaexpr.callable.ExprInstance
import okhttp3.Call
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cancel token for API request cancellation.
 * 
 * This class provides a mechanism to cancel ongoing API requests. It can be passed to
 * API handlers and used to cancel requests at any time. Multiple OkHttp calls can be
 * registered with the same token, and calling cancel() will cancel all of them.
 * 
 * Usage:
 * ```kotlin
 * val cancelToken = CancelToken()
 * 
 * // Pass to API request
 * apiHandler.execute(request, cancelToken = cancelToken)
 * 
 * // Cancel the request at any time
 * cancelToken.cancel()
 * 
 * // Check if cancelled
 * if (cancelToken.isCancelled) {
 *     // Handle cancellation
 * }
 * ```
 * 
 * In JSON expressions:
 * ```json
 * {
 *   "type": "digia/text",
 *   "props": {
 *     "text": "@{cancelToken.isCancelled ? 'Cancelled' : 'Active'}"
 *   }
 * }
 * ```
 */
class CancelToken : ExprInstance {
    
    private val _calls = CopyOnWriteArrayList<Call>()
    
    /**
     * Whether this token has been cancelled.
     */
    @Volatile
    var isCancelled: Boolean = false
        private set
    
    /**
     * Message provided when the token was cancelled, if any.
     */
    var cancelReason: String? = null
        private set
    
    /**
     * Cancels all registered OkHttp calls and marks this token as cancelled.
     * 
     * @param reason Optional reason for cancellation
     */
    fun cancel(reason: String? = null) {
        if (isCancelled) return
        
        isCancelled = true
        cancelReason = reason
        
        // Cancel all registered calls
        _calls.forEach { call ->
            if (!call.isCanceled()) {
                call.cancel()
            }
        }
        
        // Clear the list after cancelling
        _calls.clear()
    }
    
    /**
     * Registers an OkHttp Call with this token for cancellation management.
     * If the token is already cancelled, the call will be cancelled immediately.
     * 
     * @param call The OkHttp Call to register
     */
    fun register(call: Call) {
        if (isCancelled) {
            // If already cancelled, cancel the new call immediately
            call.cancel()
        } else {
            _calls.add(call)
        }
    }
    
    /**
     * Unregisters an OkHttp Call from this token.
     * Called automatically when a call completes or is cancelled.
     * 
     * @param call The OkHttp Call to unregister
     */
    fun unregister(call: Call) {
        _calls.remove(call)
    }
    
    /**
     * Throws a CancellationException if this token has been cancelled.
     * Useful for checking cancellation status at various points in async operations.
     * 
     * @throws CancellationException if the token is cancelled
     */
    fun throwIfCancelled() {
        if (isCancelled) {
            throw CancellationException(cancelReason ?: "Request was cancelled")
        }
    }
    
    /**
     * Resets the token to an uncancelled state.
     * This allows reusing the token for new requests after previous ones were cancelled.
     * Note: This does not affect already-cancelled calls.
     */
    fun reset() {
        isCancelled = false
        cancelReason = null
        _calls.clear()
    }
    
    /**
     * Gets a field value for expression evaluation.
     * Supports accessing cancellation state in JSON expressions.
     * 
     * Available fields:
     * - `isCancelled` - Whether the token has been cancelled
     * - `cancelReason` - The reason for cancellation, if provided
     */
    override fun getField(name: String): Any? {
        return when (name) {
            "isCancelled" -> isCancelled
            "cancelReason" -> cancelReason
            else -> null
        }
    }
}

/**
 * Exception thrown when an operation is cancelled via a CancelToken.
 */
class CancellationException(message: String) : Exception(message)
