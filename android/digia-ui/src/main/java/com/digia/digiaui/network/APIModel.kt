package com.digia.digiaui.network

import com.digia.digiaui.utils.asSafe
import com.digia.digiaui.framework.datatype.Variable
import com.digia.digiaui.framework.datatype.VariableConverter
import com.digia.digiaui.framework.utils.JsonLike


/**
 * Represents an API model configuration for network requests.
 *
 * APIModel contains all the necessary information to make HTTP requests,
 * including endpoint details, authentication, headers, body content, and
 * dynamic variables that can be evaluated at runtime.
 *
 * Properties:
 * - **id**: Unique identifier for the API model
 * - **name**: Optional human-readable name for the API model
 * - **url**: The endpoint URL (can contain variable placeholders)
 * - **method**: HTTP method (GET, POST, PUT, DELETE, etc.)
 * - **headers**: Optional headers as a map of key-value pairs
 * - **body**: Optional request body content
 * - **bodyType**: Type of the request body (JSON, FormData, etc.)
 * - **variables**: Optional map of variables used in the request
 *
 * Example usage:
 * ```
 * val apiModel = APIModel(
 *     id = "getUserById",
 *     name = "Get User By ID",
 *     url = "https://api.example.com/users/{userId}",
 *     method = HttpMethod.GET,
 *     headers = mapOf("Authorization" to "Bearer {token}"),
 *     body = null,
 *     bodyType = null,
 *     variables = mapOf(
 *         "userId" to Variable(...),
 *         "token" to Variable(...)
 *     )
 * )
 * ```
 */
data class APIModel(
    val id: String,
    val name: String? = null,
    val url: String,
    val method: HttpMethod,
    val headers: Map<String, Any?>? = null,
    val body: Map<String, Any?>? = null,
    val bodyType: BodyType? = null,
    val variables: Map<String, Variable>? = null
) {
    companion object {
        /**
         * Creates an APIModel instance from a JSON map.
         *
         * @param json The JSON map containing API model data
         * @return A new APIModel instance
         * @throws IllegalArgumentException if required fields are missing
         */
        fun fromJson(json: Map<String, Any>): APIModel {
            val id = json["id"] as? String
                ?: throw IllegalArgumentException("APIModel 'id' is required")
            
            val url = json["url"] as? String
                ?: throw IllegalArgumentException("APIModel 'url' is required")
            
            val methodStr =asSafe<String>( json["method"] )
                ?: throw IllegalArgumentException("APIModel 'method' is required")
            
            val method = HttpMethod.valueOf(methodStr)
            
            val name = asSafe<String>(json["name"])
            
            val headers =asSafe<JsonLike>( json["headers"] )
            
            val body = asSafe<JsonLike>(json["body"] )
            
            val bodyTypeStr: String = asSafe<String>(json["bodyType"] )?:"JSON"
            val bodyType = BodyType.valueOf(bodyTypeStr)
            
            val variablesJson =asSafe<JsonLike>(json["variables"] )
            val variables = VariableConverter.fromJson(variablesJson)
            
            return APIModel(
                id = id,
                name = name,
                url = url,
                method = method,
                headers = headers,
                body = body,
                bodyType = bodyType,
                variables = variables
            )
        }
    }
    
    /**
     * Converts this APIModel instance to a JSON-compatible map.
     *
     * @return A map representation of this API model
     */
    fun toJson(): Map<String, Any?> {
        return buildMap {
            put("id", id)
            name?.let { put("name", it) }
            put("url", url)
            put("method", method.stringValue)
            headers?.let { put("headers", it) }
            body?.let { put("body", it) }
            bodyType?.let { put("bodyType", it.name) }
            VariableConverter.toJson(variables)
        }
    }
}
