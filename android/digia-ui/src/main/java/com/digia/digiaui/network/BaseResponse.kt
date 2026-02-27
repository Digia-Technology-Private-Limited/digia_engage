package com.digia.digiaui.network

import com.google.gson.Gson

/**
 * Base response wrapper for API calls.
 *
 * [BaseResponse] provides a standardized way to handle API responses,
 * encapsulating success status, data payload, and error information.
 * This allows for consistent error handling and response parsing across
 * different API endpoints.
 *
 * Type parameter [T] represents the expected data type when the request succeeds.
 *
 * This class mirrors the Dart implementation for cross-platform consistency.
 */
data class BaseResponse<T>(
    /** Indicates whether the API request was successful */
    val isSuccess: Boolean? = null,

    /** The parsed response data, null if the request failed */
    val data: T? = null,

    /** Error information, null if the request succeeded */
    val error: Map<String, Any?>? = null
) {
    companion object {
        /**
         * Creates a [BaseResponse] from JSON data using a deserialization function.
         *
         * This method is used to parse successful API responses into typed objects.
         * The [fromJsonT] function should handle the conversion from raw JSON
         * to the expected type T.
         *
         * @param json Raw JSON response data as a Map
         * @param fromJsonT Function to deserialize JSON to type T
         * @return A [BaseResponse] with the parsed data
         */
        fun <T> fromJson(
            json: Map<String, Any?>,
            fromJsonT: (Any?) -> T
        ): BaseResponse<T> {
            val isSuccess = json["isSuccess"] as? Boolean
            val error = json["error"] as? Map<String, Any?>
            val data = json["data"]?.let { fromJsonT(it) }

            return BaseResponse(
                isSuccess = isSuccess,
                data = data,
                error = error
            )
        }
    }

    /**
     * Converts this response to a JSON string.
     * Similar to the Dart toString() implementation.
     */
    override fun toString(): String {
        return Gson().toJson(this)
    }

    /**
     * Converts this response to a Map for JSON serialization.
     *
     * @param toJsonT Function to serialize the data of type T
     * @return Map representation suitable for JSON serialization
     */
    fun toJson(toJsonT: (T) -> Any?): Map<String, Any?> {
        return mapOf(
            "isSuccess" to isSuccess,
            "data" to data?.let { toJsonT(it) },
            "error" to error
        )
    }
}
