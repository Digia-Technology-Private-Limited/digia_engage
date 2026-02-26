package com.digia.digiaui.network

import android.content.Context
import android.os.Build
import com.digia.digiaui.utils.DeveloperConfig
import com.digia.digiaui.utils.HostApp
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkHttpResponse
import java.io.IOException
import java.lang.reflect.Type
import java.net.InetSocketAddress
import java.net.Proxy
import java.math.BigDecimal
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NetworkClient(
    val baseUrl: String,
    digiaHeaders: Map<String, String>,
    projectNetworkConfiguration: NetworkConfiguration,
    developerConfig: DeveloperConfig? = null,
) {
    val digiaClient: OkHttpClient
    val projectClient: OkHttpClient
    private val mutableDigiaHeaders = digiaHeaders.toMutableMap()
    private val mutableProjectHeaders = projectNetworkConfiguration.defaultHeaders.mapValues { it.value.toString() }.toMutableMap()

    /// Checks if the app is in debug mode
    private val isDebugMode: Boolean
        get() = try {
            // Try BuildConfig.DEBUG first (most common)
//            val buildConfigClass = Class.forName("com.digia.digiaui.BuildConfig")
//            val debugField = buildConfigClass.getField("DEBUG")
//            debugField.getBoolean(null)
            false
        } catch (_: Exception) {
            // Fallback: check build type
            Build.TYPE == "debug" || Build.TYPE == "eng"
        }

    private val gson: Gson = GsonBuilder()
//        .registerTypeAdapter(Any::class.java, AnyDeserializer())
        .registerTypeAdapter(JsonElement::class.java, AnyDeserializer())
        .registerTypeAdapter(Map::class.java, AnyDeserializer())
        .create()

    init {
        if (baseUrl.isEmpty()) {
            throw IllegalArgumentException("Invalid BaseUrl")
        }

        digiaClient = createDigiaClient(mutableDigiaHeaders, developerConfig)
        projectClient = createProjectClient(mutableProjectHeaders, developerConfig)
    }

    private fun createDigiaClient(
            headers: Map<String, String>,
            developerConfig: DeveloperConfig?
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
                .connectTimeout(1000, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                            .newBuilder()
                            .addHeader("Content-Type", "application/json")
                            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                            .build()
                    chain.proceed(request)
                }

        configureDeveloperOptions(builder, developerConfig)
        return builder.build()
    }

    private fun createProjectClient(
            headers: Map<String, String>,
            developerConfig: DeveloperConfig?
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
                .connectTimeout(30000, TimeUnit.MILLISECONDS) // Default 30 seconds
                .addInterceptor { chain ->
                    val request = chain.request()
                            .newBuilder()
                            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                            .build()
                    chain.proceed(request)
                }

        // Add interceptor if provided
        developerConfig?.inspector?.networkObserver?.interceptor?.let { interceptor ->
            builder.addInterceptor(interceptor)
        }

        configureDeveloperOptions(builder, developerConfig)
        return builder.build()
    }

    private fun configureDeveloperOptions(
            builder: OkHttpClient.Builder,
            developerConfig: DeveloperConfig?
    ) {
        developerConfig?.proxyUrl?.let { proxyUrl ->
            if (isDebugMode) {
                val parts = proxyUrl.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 8080
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
                builder.proxy(proxy)

                val trustAllCerts =
                        arrayOf<TrustManager>(
                                object : X509TrustManager {
                                    override fun checkClientTrusted(
                                            chain: Array<out X509Certificate>?,
                                            authType: String?
                                    ) {}
                                    override fun checkServerTrusted(
                                            chain: Array<out X509Certificate>?,
                                            authType: String?
                                    ) {}
                                    override fun getAcceptedIssuers(): Array<X509Certificate> =
                                            arrayOf()
                                }
                        )

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                val sslSocketFactory = sslContext.socketFactory

                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
            }
        }
    }

    suspend fun requestProject(
            bodyType: BodyType,
            url: String,
            method: HttpMethod,
            additionalHeaders: Map<String, String>? = null,
            data: Any? = null,
            apiName: String? = null
    ): ApiResponse<Any> {
        return try {
            val response = requestProjectRaw(bodyType, url, method, additionalHeaders, data, apiName)
            convertToApiResponse(response)
        } catch (e: Exception) {
            ApiResponse.Error(
                message = e.message ?: "Unknown error",
                exception = e
            )
        }
    }

    private suspend fun requestProjectRaw(
            bodyType: BodyType,
            url: String,
            method: HttpMethod,
            additionalHeaders: Map<String, String>? = null,
            data: Any? = null,
            apiName: String? = null
    ): OkHttpResponse {
        return withContext(Dispatchers.IO) {
            val requestBody =
                    when (bodyType) {
                        BodyType.JSON, BodyType.GRAPHQL -> {
                            if (data == null) {
                                null
                            } else {
                                val json = when (data) {
                                    is String -> data
                                    else -> Gson().toJson(data)
                                }
                                json.toRequestBody("application/json".toMediaTypeOrNull())
                            }
                        }
                        BodyType.MULTIPART -> {
                            if (data is FormDataBuilder) {
                                buildMultipartBody(data)
                            } else {
                                data as? RequestBody
                            }
                        }
                        BodyType.FORM_URLENCODED -> {
                            if (data is Map<*, *>) {
                                val formBody = FormBody.Builder()
                                @Suppress("UNCHECKED_CAST")
                                (data as Map<String, String>).forEach { (key, value) ->
                                    formBody.add(key, value)
                                }
                                formBody.build()
                            } else {
                                data?.toString()?.toRequestBody(
                                    "application/x-www-form-urlencoded".toMediaTypeOrNull()
                                )
                            }
                        }
                    }

            // Remove headers already in base headers to avoid conflicts
            val commonKeys = mutableProjectHeaders.keys.intersect(additionalHeaders?.keys ?: emptySet())
            val filteredAdditionalHeaders = additionalHeaders?.filterKeys { it !in commonKeys }

            val requestBuilder = Request.Builder()
                    .url(url)
                    .method(method.stringValue, requestBody)
                    .addHeader("Content-Type", bodyType.contentTypeHeader ?: "application/json")

            filteredAdditionalHeaders?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            projectClient.newCall(requestBuilder.build()).execute()
        }
    }

    private fun buildMultipartBody(formData: FormDataBuilder): RequestBody {
        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        
        // Add fields
        formData.fields.forEach { (key, value) ->
            multipartBuilder.addFormDataPart(key, value)
        }
        
        // Add files
        formData.files.forEach { (key, file) ->
            when (file) {
                is FormFile.LocalFile -> {
                    val mediaType = file.mimeType?.toMediaTypeOrNull()
                    val requestBody = RequestBody.create(mediaType, file.file)
                    multipartBuilder.addFormDataPart(key, file.fileName, requestBody)
                }
                is FormFile.BytesFile -> {
                    val mediaType = file.mimeType?.toMediaTypeOrNull()
                    val requestBody = RequestBody.create(mediaType, file.bytes)
                    multipartBuilder.addFormDataPart(key, file.fileName, requestBody)
                }
            }
        }
        
        return multipartBuilder.build()
    }

    private fun convertToApiResponse(
        response: OkHttpResponse
    ): ApiResponse<Any> {
        val body = response.body?.string()
        val parsedData = if (body != null && body.isNotEmpty()) {
            try {
                Gson().fromJson(body, Map::class.java) as? Map<String, Any>
            } catch (e: Exception) {
                mapOf("raw" to body)
            }
        } else {
            null
        }

        return if (response.isSuccessful) {
            ApiResponse.Success(
                statusCode = response.code,
                data = parsedData,
                headers = response.headers.toMultimap()
            )
        } else {
            ApiResponse.Error(
                message = "HTTP ${response.code}: ${response.message}",
                statusCode = response.code,
                body = body
            )
        }
    }

    suspend fun <T> requestInternal(
            method: HttpMethod,
            path: String,
            fromJsonT: (Any?) -> T,
            data: Any? = null,
            headers: Map<String, String> = emptyMap()
    ): BaseResponse<T> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = when {
                    data == null -> null
                    data is Map<*, *> -> Gson().toJson(data).toRequestBody("application/json".toMediaTypeOrNull())
                    else -> data.toString().toRequestBody("application/json".toMediaTypeOrNull())
                }

                val fullUrl = "$baseUrl$path".replace("/$path", path)

                val request = Request.Builder()
                        .url(fullUrl)
                        .method(method.stringValue, requestBody)
                        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                        .build()

                val response = digiaClient.newCall(request).execute()
                println(response.request)
                if (response.isSuccessful) {
                    val json = response.body?.string()

                    val type = object : TypeToken<Map<String, Any?>>() {}.type

                    // 🔥 THIS is where AnyDeserializer kicks in
                    val map: Map<String, Any?> =
                        gson.fromJson(json, type)
                    BaseResponse.fromJson(map, fromJsonT)

                } else {
                    BaseResponse(isSuccess = false, data = null, error = mapOf("code" to response.code))
                }
            } catch (e: IOException) {
                throw RuntimeException("Error making HTTP request: $e")
            }
        }
    }

    fun replaceProjectHeaders(headers: Map<String, String>) {
        mutableProjectHeaders.clear()
        mutableProjectHeaders.putAll(headers)
    }

    fun addVersionHeader(version: Int) {
        mutableDigiaHeaders["x-digia-project-version"] = version.toString()
    }

    companion object {
        fun getDefaultDigiaHeaders(
                packageVersion: String,
                accessKey: String,
                platform: String,
                uuid: String?,
                packageName: String,
                appVersion: String,
                appBuildNumber: String,
                environment: String,
                buildSignature: String,
                host: HostApp?
        ): Map<String, String> {
            val headers =
                    mutableMapOf(
                            "x-digia-version" to packageVersion,
                            "x-digia-project-id" to accessKey,
                            "x-digia-platform" to platform,
                            "x-app-package-name" to packageName,
                            "x-app-version" to appVersion,
                            "x-app-build-number" to appBuildNumber,
                            "x-digia-environment" to environment
                    )
            uuid?.let { headers["x-digia-device-id"] = it }
            if (buildSignature.isNotEmpty()) {
                headers["x-app-signature"] = buildSignature
            }
            if(host!=null) {
                headers["x-digia-host"] = host.name
            }
            return headers
        }
    }

    suspend fun multipartRequestProject(
            bodyType: BodyType,
            url: String,
            method: HttpMethod,
            additionalHeaders: Map<String, String>? = null,
            data: Any? = null,
            uploadProgress: ((Long, Long) -> Unit)? = null,
            apiName: String? = null
    ): ApiResponse<Any> {
        return withContext(Dispatchers.IO) {
            try {
                // Remove connection timeout for large file uploads
                val client = projectClient.newBuilder().connectTimeout(0, TimeUnit.MILLISECONDS).build()

                val requestBody = if (data is FormDataBuilder) {
                    buildMultipartBody(data)
                } else {
                    data as? RequestBody
                }

                val requestBuilder = Request.Builder()
                        .url(url)
                        .method(method.stringValue, requestBody)
                        .addHeader(
                                "Content-Type",
                                bodyType.contentTypeHeader ?: "multipart/form-data"
                        )

                // Remove headers already in base headers to avoid conflicts
                val commonKeys = mutableProjectHeaders.keys.intersect(additionalHeaders?.keys ?: emptySet())
                val filteredAdditionalHeaders = additionalHeaders?.filterKeys { it !in commonKeys }

                filteredAdditionalHeaders?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

                val response = client.newCall(requestBuilder.build()).execute()
                convertToApiResponse(response)
            } catch (e: Exception) {
                ApiResponse.Error(
                    message = e.message ?: "Unknown error",
                    exception = e
                )
            }
        }
    }
}

class AnyDeserializer : JsonDeserializer<Any> {
    override fun deserialize(
        json: com.google.gson.JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Any? {

        if (json == null || json.isJsonNull) return null

        return when {
            json.isJsonPrimitive -> {
                val prim = json.asJsonPrimitive
                when {
                    prim.isBoolean -> prim.asBoolean
                    prim.isString -> prim.asString
                    prim.isNumber -> {
                        val numStr = prim.asString

                        when {
                            // Decimal / scientific notation: keep as BigDecimal (Number) to avoid forced Double
                            numStr.contains('.') || numStr.contains('e') || numStr.contains('E') ->
                                runCatching { BigDecimal(numStr) }.getOrNull()

                            // Integer: prefer Int/Long, fall back to BigInteger if it doesn't fit
                            else -> {
                                val longValue = numStr.toLongOrNull()
                                if (longValue != null) {
                                    if (longValue in Int.MIN_VALUE..Int.MAX_VALUE) longValue.toInt() else longValue
                                } else {
                                    runCatching { BigInteger(numStr) }.getOrNull()
                                }
                            }
                        }
                    }
                    else -> null
                }
            }

            json.isJsonArray ->
                json.asJsonArray.map {
                    deserialize(it, Any::class.java, context)
                }

            json.isJsonObject ->
                json.asJsonObject.entrySet().associate {
                    it.key to deserialize(it.value, Any::class.java, context)
                }

            else -> null
        }
    }
}
