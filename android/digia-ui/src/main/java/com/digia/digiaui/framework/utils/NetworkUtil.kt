package com.digia.digiaui.framework.utils

import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.expr.ScopeContext
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.network.APIModel
import com.digia.digiaui.network.ApiHandler
import com.digia.digiaui.network.ApiResponse
import kotlinx.coroutines.Job

typealias ResponseObject = JsonLike

/** Execute API action with success/error callbacks that return Jobs (awaitable) */
suspend fun executeApiAction(
        scopeContext: ScopeContext?,
        apiModel: APIModel,
        args: Map<String, ExprOr<Any>?>? = null,
        successCondition: ExprOr<Boolean>? = null,
        onSuccess: suspend (JsonLike) -> Job? = { null },
        onError: suspend (JsonLike) -> Job? = { null },
        progressCallback: ((com.digia.digiaui.network.UploadProgress) -> Unit)? = null
): ApiResponse<Any> {
    // Evaluate all arguments
    val evaluatedArgs =
            apiModel.variables?.mapValues { (key, variable) ->
                args?.get(key)?.evaluate(scopeContext) ?: variable.defaultValue
            }
                    ?: emptyMap()

    try {
        // Execute the API call using ApiHandler
        val response =
                ApiHandler.execute(
                        apiModel = apiModel,
                        args = evaluatedArgs,
                        progressCallback = progressCallback
                )

        // Build response object
        val respObj =
                when (response) {
                    is ApiResponse.Success -> {
                        mapOf(
                                "body" to response.data,
                                "statusCode" to response.statusCode,
                                "headers" to response.headers,
                                "error" to null
                        )
                    }
                    is ApiResponse.Error -> {
                        mapOf(
                                "body" to response.body,
                                "statusCode" to response.statusCode,
                                "headers" to emptyMap<String, Any>(),
                                "error" to response.message
                        )
                    }
                }

        // Check success condition
        val isSuccess =
                if (successCondition != null) {
                    successCondition.evaluate<Boolean>(
                            DefaultScopeContext(
                                    variables = mapOf("response" to respObj),
                                    enclosing = scopeContext,
                            )
                    ) == true
                } else {
                    response is ApiResponse.Success
                }

        // Execute appropriate callback and await completion (if any)
        val job =
                if (isSuccess) {
                    onSuccess(respObj)
                } else {
                    onError(respObj)
                }
        job?.join()

        return response
    } catch (error: Exception) {
        // Build error response object
        val respObj =
                mapOf(
                        "body" to null,
                        "statusCode" to null,
                        "headers" to emptyMap<String, Any>(),
                        "error" to (error.message ?: "Unknown error")
                )

        val job = onError(respObj)
        job?.join()

        throw error
    }
}

/** Check if a URL/path has one of the specified extensions */
fun hasExtension(src: String, exts: List<String>): Boolean {
    // Parse as URI if possible
    val path =
            try {
                java.net.URI(src).path ?: src
            } catch (e: Exception) {
                src
            }

    // Get the lower case path, remove query params and fragments
    val lowerPath = path.lowercase().split('?').first().split('#').first()

    // Normalize extensions to lowercase
    val normalizedExts = exts.map { it.lowercase() }

    // Check if any extension matches
    if (normalizedExts.any { lowerPath.endsWith(it) }) return true

    // Special handling for data URIs
    if (src.startsWith("data:")) {
        val lower = src.lowercase()
        if (normalizedExts.any { ext -> lower.startsWith("data:image/$ext") }) {
            return true
        }
    }

    return false
}
