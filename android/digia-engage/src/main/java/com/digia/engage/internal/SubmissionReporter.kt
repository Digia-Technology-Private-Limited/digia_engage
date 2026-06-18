package com.digia.engage.internal

import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEndpoints
import com.digia.engage.internal.model.CampaignModel
import com.digia.engage.internal.model.SurveyBlock
import com.digia.engage.internal.model.SurveyBlockType
import com.digia.engage.internal.model.SurveyConfigModel
import com.digia.engage.internal.model.SurveyNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class SubmissionReporter(
    private val config: DigiaConfig,
    private val deviceId: String,
    private val scope: CoroutineScope,
) {
    fun reportSurveyCompleted(
        campaign: CampaignModel,
        answers: Map<String, SurveyAnswer>,
        startedAtMs: Long,
    ) {
        val surveyConfig = campaign.surveyConfig ?: return
        val body = buildBody(campaign, surveyConfig, answers, startedAtMs)
        scope.launch(Dispatchers.IO) { post(body) }
    }

    private fun post(body: JSONObject) {
        val fullUrl = DigiaEndpoints.submission
        try {
            val connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-digia-project-id", config.apiKey)
                setRequestProperty("x-digia-device-id", deviceId)
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            android.util.Log.d("Digia", "[SubmissionReporter] recordSubmission HTTP $code")
            if (code !in 200..299) {
                val err = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull()
                android.util.Log.w("Digia", "[SubmissionReporter] error body: $err")
            }
        } catch (t: Throwable) {
            android.util.Log.w("Digia", "[SubmissionReporter] post failed: ${t.message}")
        }
    }

    private fun buildBody(
        campaign: CampaignModel,
        survey: SurveyConfigModel,
        answers: Map<String, SurveyAnswer>,
        startedAtMs: Long,
    ): JSONObject {
        val now = System.currentTimeMillis()
        val answeredNodes = survey.nodes.filter { node ->
            val block = survey.blockFor(node) ?: return@filter false
            !block.type.isContent && answers[node.id]?.isAnswered == true
        }
        val promptNodes = survey.nodes.filter { node ->
            survey.blockFor(node)?.type?.isContent == false
        }

        val responses = JSONArray()
        answeredNodes.forEach { node ->
            val block = survey.blockFor(node) ?: return@forEach
            val answer = answers[node.id] ?: return@forEach
            responses.put(buildResponse(node, block, answer))
        }

        val completion = JSONObject()
            .put("answeredCount", answeredNodes.size)
            .put("totalCount", promptNodes.size)

        val payload = JSONObject()
            .put("templateVersion", "v1")
            .put("completion", completion)
            .put("responses", responses)

        val computed = JSONObject().put("durationMs", now - startedAtMs)
        npsBucketOf(survey, answers)?.let { computed.put("npsBucket", it) }

        return JSONObject()
            .put("campaignId", campaign.id)
            .put("submissionKey", "attempt-$now")
            .put("submissionType", "survey")
            .put("payload", payload)
            .put("computed", computed)
            .put("occurredAt", isoTimestamp(now))
    }

    private fun buildResponse(
        node: SurveyNode,
        block: SurveyBlock,
        answer: SurveyAnswer,
    ): JSONObject {
        val obj = JSONObject()
            .put("blockId", block.id)
            .put("blockType", blockTypeWire(block.type))
            .put("title", block.title.text)

        when {
            block.type == SurveyBlockType.NPS || block.type == SurveyBlockType.RATING ||
                block.type == SurveyBlockType.NUMBER -> {
                val n = answer.asNumber()
                if (n != null && n == n.toLong().toDouble()) obj.put("value", n.toLong())
                else if (n != null) obj.put("value", n)
                else obj.put("value", answer.values.firstOrNull() ?: "")
            }
            block.type.isMultiSelect -> {
                val arr = JSONArray()
                val labels = JSONArray()
                answer.values.forEach { v ->
                    arr.put(v)
                    block.options.firstOrNull { it.id == v }?.let { labels.put(it.label) }
                }
                obj.put("value", arr)
                if (labels.length() > 0) obj.put("valueLabel", labels)
            }
            block.type.isChoice -> {
                val v = answer.values.firstOrNull() ?: ""
                obj.put("value", v)
                block.options.firstOrNull { it.id == v }?.let { obj.put("valueLabel", it.label) }
            }
            else -> obj.put("value", answer.values.firstOrNull() ?: "")
        }
        answer.comment?.takeIf { it.isNotBlank() }?.let { obj.put("comment", it) }
        return obj
    }

    private fun blockTypeWire(type: SurveyBlockType): String = type.name.lowercase()

    private fun npsBucketOf(
        survey: SurveyConfigModel,
        answers: Map<String, SurveyAnswer>,
    ): String? {
        val npsNode = survey.nodes.firstOrNull { survey.blockFor(it)?.type == SurveyBlockType.NPS }
            ?: return null
        val score = answers[npsNode.id]?.asNumber()?.toInt() ?: return null
        return when {
            score >= 9 -> "promoter"
            score >= 7 -> "passive"
            else -> "detractor"
        }
    }

    private fun isoTimestamp(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }
}
