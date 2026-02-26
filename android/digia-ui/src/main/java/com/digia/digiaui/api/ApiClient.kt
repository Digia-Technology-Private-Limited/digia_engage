//package com.digia.digiaui.api
//
//import com.digia.digiaui.config.model.DUIConfig
//import com.google.gson.Gson
//import java.util.concurrent.TimeUnit
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import okhttp3.MediaType.Companion.toMediaType
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.RequestBody.Companion.toRequestBody
//
///** REST API client for making HTTP requests based on DUIConfig */
//class ApiClient(
//        private val config: DUIConfig,
//        private val baseUrl: String,
//        private val timeout: Long = 30
//) {
//    private val gson = Gson()
//    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
//
//    private val client =
//            OkHttpClient.Builder()
//                    .connectTimeout(timeout, TimeUnit.SECONDS)
//                    .readTimeout(timeout, TimeUnit.SECONDS)
//                    .writeTimeout(timeout, TimeUnit.SECONDS)
//                    .build()
//
//    suspend fun get(
//            endpoint: String,
//            headers: Map<String, String> = emptyMap(),
//            queryParams: Map<String, String> = emptyMap()
//    ): ApiResponse<Any> {
//        return withContext(Dispatchers.IO) {
//            try {
//                val url = buildUrl(endpoint, queryParams)
//                val request =
//                        Request.Builder()
//                                .url(url)
//                                .apply {
//                                    addDefaultHeaders()
//                                    headers.forEach { (key, value) -> addHeader(key, value) }
//                                }
//                                .get()
//                                .build()
//
//                val response = client.newCall(request).execute()
//                parseResponse(response)
//            } catch (e: Exception) {
//                ApiResponse.Error(e.message ?: "Unknown error", e)
//            }
//        }
//    }
//
//    suspend fun post(
//            endpoint: String,
//            body: Any,
//            headers: Map<String, String> = emptyMap()
//    ): ApiResponse<Any> {
//        return withContext(Dispatchers.IO) {
//            try {
//                val url = buildUrl(endpoint)
//                val jsonBody = gson.toJson(body).toRequestBody(jsonMediaType)
//
//                val request =
//                        Request.Builder()
//                                .url(url)
//                                .apply {
//                                    addDefaultHeaders()
//                                    headers.forEach { (key, value) -> addHeader(key, value) }
//                                }
//                                .post(jsonBody)
//                                .build()
//
//                val response = client.newCall(request).execute()
//                parseResponse(response)
//            } catch (e: Exception) {
//                ApiResponse.Error(e.message ?: "Unknown error", e)
//            }
//        }
//    }
//
//    suspend fun put(
//            endpoint: String,
//            body: Any,
//            headers: Map<String, String> = emptyMap()
//    ): ApiResponse<Any> {
//        return withContext(Dispatchers.IO) {
//            try {
//                val url = buildUrl(endpoint)
//                val jsonBody = gson.toJson(body).toRequestBody(jsonMediaType)
//
//                val request =
//                        Request.Builder()
//                                .url(url)
//                                .apply {
//                                    addDefaultHeaders()
//                                    headers.forEach { (key, value) -> addHeader(key, value) }
//                                }
//                                .put(jsonBody)
//                                .build()
//
//                val response = client.newCall(request).execute()
//                parseResponse(response)
//            } catch (e: Exception) {
//                ApiResponse.Error(e.message ?: "Unknown error", e)
//            }
//        }
//    }
//
//    suspend fun delete(endpoint: String, headers: Map<String, String> = emptyMap()): ApiResponse<Any> {
//        return withContext(Dispatchers.IO) {
//            try {
//                val url = buildUrl(endpoint)
//
//                val request =
//                        Request.Builder()
//                                .url(url)
//                                .apply {
//                                    addDefaultHeaders()
//                                    headers.forEach { (key, value) -> addHeader(key, value) }
//                                }
//                                .delete()
//                                .build()
//
//                val response = client.newCall(request).execute()
//                parseResponse(response)
//            } catch (e: Exception) {
//                ApiResponse.Error(e.message ?: "Unknown error", e)
//            }
//        }
//    }
//
//    private fun buildUrl(endpoint: String, queryParams: Map<String, String> = emptyMap()): String {
//        var url =
//                if (endpoint.startsWith("http")) {
//                    endpoint
//                } else {
//                    "$baseUrl${if (!endpoint.startsWith("/")) "/" else ""}$endpoint"
//                }
//
//        if (queryParams.isNotEmpty()) {
//            val query = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
//            url = "$url?$query"
//        }
//
//        return url
//    }
//
//    private fun Request.Builder.addDefaultHeaders() {
//        config.getDefaultHeaders()?.forEach { (key, value) -> addHeader(key, value.toString()) }
//    }
//
//    private fun parseResponse(response: okhttp3.Response): ApiResponse<Any> {
//        val body = response.body?.string()
//
//        return if (response.isSuccessful) {
//            val data =
//                    if (body != null && body.isNotEmpty()) {
//                        try {
//                            gson.fromJson(body, Map::class.java) as? Map<String, Any>
//                        } catch (e: Exception) {
//                            mapOf("raw" to body)
//                        }
//                    } else {
//                        emptyMap()
//                    }
//
//            ApiResponse.Success(
//                    statusCode = response.code,
//                    data = data,
//                    headers = response.headers.toMultimap()
//            )
//        } else {
//            ApiResponse.Error(
//                    message = "HTTP ${response.code}: ${response.message}",
//                    statusCode = response.code,
//                    body = body
//            )
//        }
//    }
//}
//
//sealed class ApiResponse<T> {
//    data class Success<T>(
//            val statusCode: Int,
//            val data: T?,
//            val headers: Map<String, List<String>>
//    ) : ApiResponse<T>()
//
//    data class Error<T>(
//            val message: String,
//            val exception: Throwable? = null,
//            val statusCode: Int? = null,
//            val body: String? = null
//    ) : ApiResponse<T>()
//}
