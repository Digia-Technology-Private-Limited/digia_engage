package com.digia.engage.internal

import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEndpoints
import com.digia.engage.DigiaEnvironment
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

internal class AnalyticsClient(
    private val diagnosticsReporter: DiagnosticsReporter,
) {
    @Volatile
    private var config: DigiaConfig? = null

    fun configure(config: DigiaConfig) {
        this.config = config
    }

    fun clear() {
        config = null
    }

    fun track(event: DigiaExperienceEvent, payload: InAppPayload) {
        val eventType = when (event) {
            DigiaExperienceEvent.Impressed -> "impressed"
            is DigiaExperienceEvent.Clicked -> "clicked"
            DigiaExperienceEvent.Dismissed -> "dismissed"
        }
        val elementId = (event as? DigiaExperienceEvent.Clicked)?.elementId
        record(eventType, payload, elementId = elementId, detail = emptyMap())
    }

    fun trackInternal(event: InternalEngageEvent, payload: InAppPayload) {
        when (event) {
            is InternalEngageEvent.SurveyAnswered -> record(
                eventType = "survey_answered",
                payload = payload,
                elementId = event.stepId,
                detail = mapOf(
                    "step_id" to event.stepId,
                    "answer" to event.answer,
                ),
            )
            is InternalEngageEvent.SurveyCompleted -> record(
                eventType = "survey_completed",
                payload = payload,
                elementId = null,
                detail = mapOf("response" to event.response),
            )
        }
    }

    private fun record(
        eventType: String,
        payload: InAppPayload,
        elementId: String?,
        detail: Map<String, Any?>,
    ) {
        val activeConfig = config ?: run {
            diagnosticsReporter.reportWarning("analytics skipped: SDK config unavailable")
            return
        }
        val campaignId = payload.content["campaign_id"] as? String
            ?: payload.content["campaignId"] as? String
            ?: run {
                diagnosticsReporter.reportWarning("analytics skipped: missing campaign_id for ${payload.id}")
                return
            }

        Thread {
            runCatching {
                val connection = (URL("${baseUrl(activeConfig)}/api/v1/engage/sdk/recordEvent")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("X-Digia-Project-Id", activeConfig.apiKey)
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                }
                val body = JSONObject()
                    .put("campaign_id", campaignId)
                    .put("event_type", eventType)
                    .put("user_id", userId(payload))
                    .put("element_id", elementId)
                    .put("detail", JSONObject(detail))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    diagnosticsReporter.reportWarning("analytics record failed: HTTP $code event=$eventType")
                }
                connection.disconnect()
            }.onFailure { error ->
                diagnosticsReporter.reportWarning("analytics record failed: ${error.message}")
            }
        }.start()
    }

    private fun baseUrl(config: DigiaConfig): String =
        (config.baseUrl
            ?: if (config.environment == DigiaEnvironment.SANDBOX) {
                DigiaEndpoints.SANDBOX
            } else {
                DigiaEndpoints.PRODUCTION
            }).trimEnd('/')

    private fun userId(payload: InAppPayload): String =
        (payload.cepContext["user_id"] as? String)
            ?: (payload.cepContext["userId"] as? String)
            ?: (payload.content["user_id"] as? String)
            ?: "anonymous"
}
