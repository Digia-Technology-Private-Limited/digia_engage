package com.digia.engage.internal.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal data class SenderResult(val statusCode: Int, val body: String?)

internal fun interface AnalyticsSender {
    suspend fun post(url: String, jsonBody: String, headers: Map<String, String>): SenderResult
}

internal class OkHttpAnalyticsSender : AnalyticsSender {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun post(
        url: String,
        jsonBody: String,
        headers: Map<String, String>,
    ): SenderResult = withContext(Dispatchers.IO) {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        response.close()
        SenderResult(response.code, responseBody)
    }
}
