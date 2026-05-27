package com.digia.engage.internal

import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEndpoints
import com.digia.engage.DigiaEnvironment
import com.digia.engage.internal.model.CampaignModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

internal class CampaignFetcher(private val config: DigiaConfig) {

    fun fetch(): List<CampaignModel> {
        val baseUrl =
                (config.baseUrl
                                ?: if (config.environment == DigiaEnvironment.SANDBOX)
                                        DigiaEndpoints.SANDBOX
                                else DigiaEndpoints.PRODUCTION)
                        .trimEnd('/')

        val fullUrl = "$baseUrl/api/v1/engage/sdk/getCampaigns"
        android.util.Log.d(
                "Digia",
                "[CampaignFetcher] fetching: $fullUrl (env=${config.environment})"
        )
        val url = URL(fullUrl)
        val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("X-Digia-Project-Id", config.apiKey)
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    outputStream.write("{}".toByteArray(Charsets.UTF_8))
                }

        val code = connection.responseCode
        android.util.Log.d("Digia", "[CampaignFetcher] response: HTTP $code")
        if (code != 200) throw IOException("getCampaigns failed: HTTP $code")

        val body = connection.inputStream.bufferedReader().readText()
        return parseCampaigns(extractCampaignArray(body))
    }

    private fun extractCampaignArray(body: String): JSONArray {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)

        val envelope = JSONObject(trimmed)
        envelope.optJSONObject("data")?.optJSONArray("response")?.let {
            return it
        }
        envelope.optJSONArray("response")?.let {
            return it
        }

        throw IOException("getCampaigns response missing data.response")
    }

    private fun parseCampaigns(arr: JSONArray): List<CampaignModel> {
        val result = mutableListOf<CampaignModel>()
        for (i in 0 until arr.length()) {
            CampaignModel.fromJson(arr.getJSONObject(i))?.let(result::add)
        }
        return result
    }
}
