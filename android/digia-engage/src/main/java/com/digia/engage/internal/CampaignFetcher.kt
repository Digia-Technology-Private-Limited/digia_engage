package com.digia.engage.internal

import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEndpoints
import com.digia.engage.internal.logging.Logger
import com.digia.engage.internal.model.CampaignModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

internal class CampaignFetcher(private val config: DigiaConfig, private val deviceId: String) {

    fun fetch(): List<CampaignModel> {
        val fullUrl = DigiaEndpoints.campaigns
        Logger.verbose("Fetching campaigns | url=$fullUrl env=${config.environment}")
        val url = URL(fullUrl)
        val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("X-Digia-Project-Id", config.apiKey)
                    setRequestProperty("X-Digia-Device-Id", deviceId)
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    outputStream.write("{}".toByteArray(Charsets.UTF_8))
                }

        val code = connection.responseCode
        Logger.verbose("Campaign fetch response: HTTP $code")
        if (code != 200) {
            Logger.error("Campaign fetch failed | HTTP $code — check your projectId ('${config.apiKey.take(8)}…') and network connectivity")
            throw IOException("getCampaigns failed: HTTP $code")
        }

        val body = connection.inputStream.bufferedReader().readText()
        val campaigns = parseCampaigns(extractCampaignArray(body))
        Logger.verbose("Campaigns fetched | count=${campaigns.size}")
        return campaigns
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
