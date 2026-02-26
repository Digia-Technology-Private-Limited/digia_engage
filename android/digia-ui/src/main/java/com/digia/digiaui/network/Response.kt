package com.digia.digiaui.network

/**
 * Response class that mimics dio's Response structure for HTTP operations.
 * Used for network responses including file downloads.
 */
data class Response<T>(
    /** The response data (file path for downloads, JSON for API calls, etc.) */
    val data: T? = null,

    /** HTTP status code */
    val statusCode: Int? = null,

    /** HTTP status message */
    val statusMessage: String? = null,

    /** Response headers */
    val headers: Map<String, List<String>>? = null,

    /** The original request options */
    val request: RequestOptions? = null,

    /** Extra data that can be attached to the response */
    val extra: Map<String, Any?>? = null
) {
    /** Whether the request was successful (status code 200-299) */
    val isSuccessful: Boolean
        get() = statusCode != null && statusCode in 200..299

    /** Whether the request failed */
    val isError: Boolean
        get() = !isSuccessful

    companion object {
        /** Creates a successful response */
        fun <T> success(
            data: T,
            statusCode: Int = 200,
            statusMessage: String? = null,
            headers: Map<String, List<String>>? = null,
            request: RequestOptions? = null,
            extra: Map<String, Any?>? = null
        ): Response<T> {
            return Response(
                data = data,
                statusCode = statusCode,
                statusMessage = statusMessage,
                headers = headers,
                request = request,
                extra = extra
            )
        }

        /** Creates an error response */
        fun <T> error(
            statusCode: Int,
            statusMessage: String? = null,
            headers: Map<String, List<String>>? = null,
            request: RequestOptions? = null,
            extra: Map<String, Any?>? = null
        ): Response<T> {
            return Response(
                data = null,
                statusCode = statusCode,
                statusMessage = statusMessage,
                headers = headers,
                request = request,
                extra = extra
            )
        }
    }
}

/**
 * Request options that mimic dio's RequestOptions
 */
data class RequestOptions(
    /** The request URL */
    val url: String,

    /** HTTP method */
    val method: String = "GET",

    /** Request headers */
    val headers: Map<String, String>? = null,

    /** Query parameters */
    val queryParameters: Map<String, Any?>? = null,

    /** Request data */
    val data: Any? = null,

    /** Extra options */
    val extra: Map<String, Any?>? = null
)
