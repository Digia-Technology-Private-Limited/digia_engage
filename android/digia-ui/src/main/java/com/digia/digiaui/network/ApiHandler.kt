package com.digia.digiaui.network

import android.webkit.MimeTypeMap
import com.digia.digiaui.framework.datatype.Variable
import com.digia.digiaui.framework.datatype.adaptedfile.AdaptedFile
import com.digia.digiaui.init.DigiaUIManager
import java.io.File

/**
 * Singleton API Handler for executing API requests with template variable hydration
 */
object ApiHandler {
    private val apiVariableRegex = Regex("\\{\\{([\\w.\\-]+)\\}\\}")
    
    private var networkClient: NetworkClient? = DigiaUIManager.getInstance().networkClient
    private var environmentVariables: Map<String, Variable>? = DigiaUIManager.getInstance().environmentVariables
    private var resourceProxyUrl: String? = null



    /**
     * Execute an API request with variable hydration
     */
    suspend fun execute(
        apiModel: APIModel,
        args: Map<String, Any?>? = null,
        progressCallback: ((UploadProgress) -> Unit)? = null
    ): ApiResponse<Any> {
        val client = networkClient 
            ?: throw IllegalStateException("NetworkClient not initialized. Call ApiHandler.initialize() first.")

        // Merge environment variables with args
        val envArgs = environmentVariables?.mapKeys { "env.${it.key}" }
            ?.mapValues { it.value.defaultValue }
            ?: emptyMap()
        
        val finalArgs = buildMap {
            putAll(envArgs)
            if (args != null) {
                putAll(args)
            }
        }

        // Hydrate URL
        var url = hydrateTemplate(apiModel.url, finalArgs)
        
        // Apply proxy if needed for http URLs
        if (resourceProxyUrl != null && url.startsWith("http:")) {
            url = "$resourceProxyUrl$url"
        }

        // Hydrate headers
        val headers = apiModel.headers?.mapKeys { (key, _) ->
            hydrateTemplate(key, finalArgs)
        }?.mapValues { (_, value) ->
            hydrateTemplate(value.toString(), finalArgs)
        }

        // Hydrate body (skip for GET requests)
        val body = if (apiModel.method != HttpMethod.GET) {
            hydrateTemplateInDynamic(apiModel.body, finalArgs)
        } else {
            null
        }

        val bodyType = apiModel.bodyType ?: BodyType.JSON

        return try {
            val preparedData = prepareRequestData(body, bodyType)
            
            if (bodyType == BodyType.MULTIPART) {
                client.multipartRequestProject(
                    bodyType = bodyType,
                    url = url,
                    method = apiModel.method,
                    additionalHeaders = headers,
                    data = preparedData,
                    apiName = apiModel.name,
                    uploadProgress = progressCallback?.let { callback ->
                        { uploaded, total ->
                            callback(UploadProgress(
                                count = uploaded,
                                total = total,
                                progress = if (total > 0) (uploaded.toDouble() / total * 100) else 0.0
                            ))
                        }
                    }
                )
            } else {
                client.requestProject(
                    bodyType = bodyType,
                    url = url,
                    method = apiModel.method,
                    additionalHeaders = headers,
                    data = preparedData,
                    apiName = apiModel.name
                )
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Prepare request data based on body type
     */
    private suspend fun prepareRequestData(body: Any?, bodyType: BodyType): Any? {
        return when (bodyType) {
            BodyType.MULTIPART -> createFormData(body)
            BodyType.FORM_URLENCODED -> createUrlEncodedData(body)
            BodyType.GRAPHQL -> createGraphQLData(body)
            else -> body
        }
    }

    /**
     * Create GraphQL request data
     * Expects body to be a Map with "query", "variables", and optionally "operationName"
     */
    private fun createGraphQLData(body: Any?): Map<String, Any?> {
        return when (body) {
            is Map<*, *> -> {
                val query = body["query"]?.toString() 
                    ?: throw IllegalArgumentException("GraphQL request must contain 'query' field")
                
                buildMap {
                    put("query", query)
                    body["variables"]?.let { put("variables", it) }
                    body["operationName"]?.let { put("operationName", it) }
                }
            }
            is String -> {
                // If body is just a string, treat it as the query
                mapOf("query" to body)
            }
            else -> throw IllegalArgumentException(
                "GraphQL body must be a Map with 'query' field or a String query"
            )
        }
    }

    /**
     * Create URL encoded data
     */
    private fun createUrlEncodedData(finalData: Any?): Map<String, String> {
        return when (finalData) {
            is Map<*, *> -> finalData.mapKeys { it.key.toString() }
                .mapValues { it.value.toString() }
            else -> emptyMap()
        }
    }

    /**
     * Create multipart form data
     */
    private suspend fun createFormData(finalData: Any?): FormDataBuilder {
        val formData = FormDataBuilder()
        
        if (finalData !is Map<*, *>) {
            return formData
        }

        for (entry in finalData.entries) {
            val key = entry.key.toString()
            val value = entry.value

            when (value) {
                is AdaptedFile -> {
                    formData.addFile(key, createMultipartFileFromAdaptedFile(value))
                }
                is File -> {
                    formData.addFile(key, createMultipartFile(value))
                }
                is ByteArray -> {
                    formData.addFile(key, createMultipartFileFromBytes(value, key))
                }
                is List<*> -> {
                    handleListValue(formData, key, value)
                }
                else -> {
                    formData.addField(key, value.toString())
                }
            }
        }

        return formData
    }

    /**
     * Handle list values in form data
     */
    private fun handleListValue(formData: FormDataBuilder, key: String, values: List<*>) {
        if (values.isEmpty()) {
            formData.addField(key, "[]")
            return
        }

        when (values.first()) {
            is AdaptedFile -> {
                values.forEach { value ->
                    if (value is AdaptedFile) {
                        formData.addFile(key, createMultipartFileFromAdaptedFile(value))
                    }
                }
            }
            is File -> {
                values.forEach { value ->
                    if (value is File) {
                        formData.addFile(key, createMultipartFile(value))
                    }
                }
            }
            is ByteArray -> {
                values.forEach { value ->
                    if (value is ByteArray) {
                        formData.addFile(key, createMultipartFileFromBytes(value, key))
                    }
                }
            }
            else -> {
                formData.addField(key, values.toString())
            }
        }
    }

    /**
     * Create multipart file from File
     */
    private fun createMultipartFile(file: File): FormFile.LocalFile {
        val fileName = file.name
        val mimeType = getMimeType(fileName)
        return FormFile.LocalFile(file, fileName, mimeType)
    }

    /**
     * Create multipart file from AdaptedFile
     */
    private fun createMultipartFileFromAdaptedFile(adaptedFile: AdaptedFile): FormFile {
        val fileName = adaptedFile.name ?: "file"
        val mimeType = adaptedFile.mimeType ?: getMimeType(fileName)
        
        // If AdaptedFile has a File object, use it
        adaptedFile.file?.let {
            return FormFile.LocalFile(it, fileName, mimeType)
        }
        
        // If AdaptedFile has bytes, use them
        adaptedFile.bytes?.let {
            return FormFile.BytesFile(it, fileName, mimeType)
        }
        
        // Try to read bytes from AdaptedFile (may require context)
        // This is a fallback - ideally bytes should be preloaded
        throw IllegalStateException("AdaptedFile must have either file or bytes available for upload")
    }

    /**
     * Create multipart file from bytes
     */
    private fun createMultipartFileFromBytes(bytes: ByteArray, key: String): FormFile.BytesFile {
        val mimeType = getMimeType(key)
        return FormFile.BytesFile(bytes, key, mimeType)
    }

    /**
     * Get MIME type from file name
     */
    private fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "")
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else {
            null
        }
    }

    /**
     * Hydrate template string with variables
     */
    private fun hydrateTemplate(template: String, values: Map<String, Any?>): String {
        return apiVariableRegex.replace(template) { matchResult ->
            val variableName = matchResult.groupValues[1]
            values[variableName]?.toString() ?: matchResult.value
        }
    }

    /**
     * Hydrate template in dynamic data structures
     */
    private fun hydrateTemplateInDynamic(json: Any?, values: Map<String, Any?>): Any? {
        return when (json) {
            null -> null
            is Number, is Boolean -> json
            is Map<*, *> -> {
                json.mapKeys { (key, _) ->
                    hydrateTemplateInDynamic(key, values)
                }.mapValues { (_, value) ->
                    hydrateTemplateInDynamic(value, values)
                }
            }
            is List<*> -> {
                json.map { hydrateTemplateInDynamic(it, values) }
            }
            is String -> {
                // Check if the entire string is a single variable reference
                val fullMatch = apiVariableRegex.matchEntire(json)
                if (fullMatch != null) {
                    val variableName = fullMatch.groupValues[1]
                    return values[variableName]
                }

                // Otherwise do string interpolation
                val hasVariables = apiVariableRegex.containsMatchIn(json)
                if (!hasVariables) {
                    return json
                }

                json.replace(apiVariableRegex) { matchResult ->
                    val variableName = matchResult.groupValues[1]
                    values[variableName]?.toString() ?: matchResult.value
                }
            }
            else -> json
        }
    }
}

/**
 * API Response sealed class
 */
sealed class ApiResponse<T> {
    data class Success<T>(
        val statusCode: Int,
        val data: T?,
        val headers: Map<String, List<String>>
    ) : ApiResponse<T>()

    data class Error<T>(
        val message: String,
        val exception: Throwable? = null,
        val statusCode: Int? = null,
        val body: String? = null
    ) : ApiResponse<T>()
}

/**
 * Upload progress data
 */
data class UploadProgress(
    val count: Long,
    val total: Long,
    val progress: Double
)

/**
 * Multipart form data builder
 */
class FormDataBuilder {
    val fields = mutableListOf<Pair<String, String>>()
    val files = mutableListOf<Pair<String, FormFile>>()

    fun addField(key: String, value: String) {
        fields.add(key to value)
    }

    fun addFile(key: String, file: FormFile) {
        files.add(key to file)
    }
}

/**
 * Represents a file for multipart upload
 */
sealed class FormFile {
    data class LocalFile(
        val file: File,
        val fileName: String = file.name,
        val mimeType: String? = null
    ) : FormFile()

    data class BytesFile(
        val bytes: ByteArray,
        val fileName: String,
        val mimeType: String? = null
    ) : FormFile() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BytesFile

            if (!bytes.contentEquals(other.bytes)) return false
            if (fileName != other.fileName) return false
            if (mimeType != other.mimeType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + fileName.hashCode()
            result = 31 * result + (mimeType?.hashCode() ?: 0)
            return result
        }
    }
}
