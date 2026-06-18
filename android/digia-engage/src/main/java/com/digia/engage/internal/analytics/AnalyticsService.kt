package com.digia.engage.internal.analytics

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.digia.engage.AnalyticsConfig
import com.digia.engage.BuildConfig
import com.digia.engage.CEPTriggerPayload
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEndpoints
import com.digia.engage.internal.event.EngageAnalyticsEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal class AnalyticsService
@VisibleForTesting
constructor(
        private val config: AnalyticsConfig,
        private val apiKey: String,
        internal val identity: AnalyticsIdentityManager,
        internal val queue: AnalyticsQueue,
        private val sender: AnalyticsSender,
        private val staticContext: Map<String, Any?>,
        private val scope: CoroutineScope,
) {
    private var flushJob: Job? = null
    private var retryJob: Job? = null
    private var isDispatching = false
    private var _retryAttempt = 0

    @VisibleForTesting var retryScheduleMs: List<Long>? = null

    val retryAttempt: Int
        get() = _retryAttempt

    init {
        identity.initialize(config.sessionTimeoutMs)
        identity.onSessionRotated = { scope.launch { reportSession() } }
        if (queue.size() > 0) scheduleTimer()
        scope.launch { reportSession() }
    }

    fun capture(
            event: EngageAnalyticsEvent,
            payload: CEPTriggerPayload,
            campaignId: String?,
            campaignType: String?,
    ) {
        if (!config.enabled) {
            android.util.Log.w(
                    "DigiaAnalytics",
                    "[AnalyticsService] capture: DISABLED (config.enabled=false) — event '${event.eventName}' dropped"
            )
            return
        }
        android.util.Log.d(
                "DigiaAnalytics",
                "[AnalyticsService] capture: event='${event.eventName}' campaignKey=${payload.campaignKey} campaignId=$campaignId"
        )
        enqueue(
                eventName = event.eventName,
                campaignId = campaignId,
                campaignKey = payload.campaignKey,
                campaignType = campaignType,
                properties = event.properties,
        )
    }

    fun setUserId(userId: String) = identity.setUserId(userId)

    fun clearUserId() = identity.clearUserId()

    fun onLifecycleStop() {
        scope.launch {
            cancelTimers()
            dispatchPending()
        }
    }

    fun onLifecycleResume() = identity.maybeExpireSession()

    fun flush() {
        scope.launch {
            cancelTimers()
            dispatchPending()
        }
    }

    @VisibleForTesting
    fun resetForTest() {
        flushJob?.cancel()
        flushJob = null
        retryJob?.cancel()
        retryJob = null
        isDispatching = false
        _retryAttempt = 0
        // deliberately does NOT clear the queue — simulates process death where the store persists
    }

    fun clear() {
        flushJob?.cancel()
        flushJob = null
        retryJob?.cancel()
        retryJob = null
        isDispatching = false
        _retryAttempt = 0
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private suspend fun reportSession() {
        try {
            val props = JSONObject()
            staticContext.forEach { (k, v) -> if (v != null) props.put(k, v) }
            val body =
                    JSONObject()
                            .apply {
                                put("session_id", identity.sessionId)
                                put("anonymous_id", identity.anonymousId)
                                identity.userId?.let { put("user_id", it) }
                                put("occurred_at", isoNow())
                                put("properties", props)
                            }
                            .toString()
            val result =
                    sender.post(
                            url = DigiaEndpoints.session,
                            jsonBody = body,
                            headers =
                                    mapOf(
                                            "Content-Type" to "application/json",
                                            "X-Digia-Project-Id" to apiKey,
                                    ),
                    )
            android.util.Log.d(
                    "DigiaAnalytics",
                    "[AnalyticsService] session reported: HTTP ${result.statusCode} sessionId=${identity.sessionId} anonymousId=${identity.anonymousId}"
            )
        } catch (e: Exception) {
            android.util.Log.w(
                    "DigiaAnalytics",
                    "[AnalyticsService] session report failed: ${e.message}"
            )
        }
    }

    private fun enqueue(
            eventName: String,
            campaignId: String?,
            campaignKey: String?,
            campaignType: String?,
            properties: Map<String, Any?> = emptyMap(),
    ) {
        val eventId = UUID.randomUUID().toString()
        identity.captureEventTime()

        // Columns are ALSO included in properties (so every field is present in the
        // properties blob), and additionally hoisted to the top level below.
        val mergedProperties = (staticContext + properties).filterValues { it != null }

        val payloadMap =
                buildMap<String, Any?> {
                    put("event_id", eventId)
                    put("event_name", eventName)
                    put("occurred_at", isoNow())
                    if (campaignId != null) put("campaign_id", campaignId)
                    if (campaignKey != null) put("campaign_key", campaignKey)
                    if (campaignType != null) put("campaign_type", campaignType)
                    put("anonymous_id", identity.anonymousId)
                    put("session_id", identity.sessionId)
                    identity.userId?.let { put("user_id", it) }
                    put("properties", mergedProperties)
                }

        queue.append(
                QueueEntry(eventId, payloadMap, System.currentTimeMillis()),
                config.queueMaxEvents
        )
        android.util.Log.d(
                "DigiaAnalytics",
                "[AnalyticsService] enqueued '$eventName' eventId=$eventId queueSize=${queue.size()} flushBatchSize=${config.flushBatchSize}"
        )

        if (queue.size() >= config.flushBatchSize) {
            android.util.Log.d(
                    "DigiaAnalytics",
                    "[AnalyticsService] batch threshold reached — dispatching immediately"
            )
            cancelTimers()
            scope.launch { dispatchPending() }
        } else {
            android.util.Log.d(
                    "DigiaAnalytics",
                    "[AnalyticsService] scheduling flush timer (interval=${config.flushIntervalMs}ms)"
            )
            scheduleTimer()
        }
    }

    private suspend fun dispatchPending() {
        if (isDispatching) {
            android.util.Log.d(
                    "DigiaAnalytics",
                    "[AnalyticsService] dispatchPending: already dispatching — skipped"
            )
            return
        }
        flushJob?.cancel()
        flushJob = null
        isDispatching = true
        try {
            val batch = queue.peek(config.maxBatchSize)
            if (batch.isEmpty()) {
                android.util.Log.d(
                        "DigiaAnalytics",
                        "[AnalyticsService] dispatchPending: queue empty — nothing to send"
                )
                _retryAttempt = 0
                return
            }

            android.util.Log.d(
                    "DigiaAnalytics",
                    "[AnalyticsService] dispatchPending: sending batch of ${batch.size} event(s)"
            )
            queue.incrementAttempt(batch.map { it.eventId })
            val result =
                    sender.post(
                            url = DigiaEndpoints.track,
                            jsonBody = buildBatchJson(batch),
                            headers =
                                    mapOf(
                                            "Content-Type" to "application/json",
                                            "X-Digia-Project-Id" to apiKey,
                                            "X-Digia-Device-Id" to identity.anonymousId,
                                    ),
                    )

            android.util.Log.d(
                    "DigiaAnalytics",
                    "[AnalyticsService] dispatchPending: HTTP ${result.statusCode}"
            )
            when {
                result.statusCode == 200 || result.statusCode == 207 -> {
                    logRejected(result.body)
                    queue.remove(batch.map { it.eventId })
                    _retryAttempt = 0
                    android.util.Log.d(
                            "DigiaAnalytics",
                            "[AnalyticsService] dispatch success — removed ${batch.size} event(s), queueSize=${queue.size()}"
                    )
                    if (queue.size() > 0) scheduleTimer(minDelayMs = 15_000L)
                }
                result.statusCode >= 500 -> {
                    android.util.Log.w(
                            "DigiaAnalytics",
                            "[AnalyticsService] dispatch failed (5xx ${result.statusCode}) — scheduling retry #${_retryAttempt + 1}"
                    )
                    scheduleRetry()
                }
                else -> {
                    android.util.Log.w(
                            "DigiaAnalytics",
                            "[AnalyticsService] dispatch failed (${result.statusCode}) — dropping batch, body=${result.body?.take(200)}"
                    )
                    queue.remove(batch.map { it.eventId })
                    _retryAttempt = 0
                    if (queue.size() > 0) scheduleTimer(minDelayMs = 15_000L)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(
                    "DigiaAnalytics",
                    "[AnalyticsService] dispatchPending: exception — ${e.message}",
                    e
            )
            scheduleRetry()
        } finally {
            isDispatching = false
        }
    }

    private fun scheduleTimer(minDelayMs: Long = config.flushIntervalMs) {
        if (flushJob != null || isDispatching) return
        val delayMs = maxOf(config.flushIntervalMs, minDelayMs)
        flushJob =
                scope.launch {
                    delay(delayMs)
                    flushJob = null
                    dispatchPending()
                }
    }

    private suspend fun scheduleRetry() {
        retryJob?.cancel()
        _retryAttempt += 1
        val delayMs = retryDelayMs(_retryAttempt)
        retryJob =
                scope.launch {
                    delay(delayMs)
                    retryJob = null
                    dispatchPending()
                }
    }

    private fun cancelTimers() {
        flushJob?.cancel()
        flushJob = null
        retryJob?.cancel()
        retryJob = null
    }

    private fun retryDelayMs(attempt: Int): Long {
        retryScheduleMs?.let { schedule ->
            return schedule[(attempt - 1).coerceIn(0, schedule.lastIndex)]
        }
        return min(1_000L * (1L shl (attempt - 1)), 16_000L)
    }

    private fun buildBatchJson(batch: List<QueueEntry>): String {
        val events = JSONArray()
        for (entry in batch) events.put(AnalyticsQueue.mapToJson(entry.payload))
        return JSONObject().put("events", events).toString()
    }

    private fun logRejected(body: String?) {
        if (body.isNullOrBlank()) return
        try {
            val errors = JSONObject(body).optJSONArray("errors") ?: return
            for (i in 0 until errors.length()) {
                val err = errors.optJSONObject(i) ?: continue
                android.util.Log.w(
                        "DigiaAnalytics",
                        "Dropped event ${err.optString("event_id")}: ${err.optString("reason")}",
                )
            }
        } catch (_: Exception) {}
    }

    private fun isoNow(): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .also { it.timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date())

    companion object {
        fun create(
                context: Context,
                config: DigiaConfig,
                scope: CoroutineScope
        ): AnalyticsService? {
            val analyticsConfig = config.analyticsConfig
            if (!analyticsConfig.enabled) {
                android.util.Log.w(
                        "DigiaAnalytics",
                        "[AnalyticsService] create: analytics DISABLED in DigiaConfig — no events will be captured"
                )
                return null
            }
            android.util.Log.d(
                    "DigiaAnalytics",
                    "[AnalyticsService] create: analytics enabled, endpoint=${DigiaEndpoints.track} batchSize=${analyticsConfig.flushBatchSize} interval=${analyticsConfig.flushIntervalMs}ms"
            )
            val store =
                    SharedPrefsStore(
                            context.getSharedPreferences("digia_analytics", Context.MODE_PRIVATE),
                    )
            return AnalyticsService(
                    config = analyticsConfig,
                    apiKey = config.apiKey,
                    identity =
                            AnalyticsIdentityManager(
                                    store,
                                    deviceIdSeed = resolveDeviceId(context)
                            ),
                    queue = AnalyticsQueue(store),
                    sender = OkHttpAnalyticsSender(),
                    staticContext = buildStaticContext(context, config),
                    scope = scope
            )
        }

        private fun resolveDeviceId(context: Context): String {
            val androidId =
                    runCatching {
                                android.provider.Settings.Secure.getString(
                                        context.contentResolver,
                                        android.provider.Settings.Secure.ANDROID_ID,
                                )
                            }
                            .getOrNull()
            return androidId?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                    ?: UUID.randomUUID().toString()
        }

        private fun buildStaticContext(context: Context, config: DigiaConfig): Map<String, Any?> {
            val appVersion =
                    runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0)
                                        .versionName
                                        ?: "unknown"
                            }
                            .getOrDefault("unknown")
            val platform = AnalyticsDeviceInfo.platform()
            val binding = config.wrapperBinding ?: "native"
            return buildMap {
                put(
                        "sdk_version",
                        buildSdkVersion(
                                binding = binding,
                                platform = platform,
                                wrapperVersion = config.wrapperVersion,
                                core = BuildConfig.DIGIA_SDK_VERSION,
                        ),
                )
                put("sdk_platform", if (binding == "native") platform else binding)
                put("device_platform", platform)
                put("app_version", appVersion)
                put("app_locale", Locale.getDefault().toString())
                put("os_version", AnalyticsDeviceInfo.osVersion())
                val make = AnalyticsDeviceInfo.deviceMake()
                val model = AnalyticsDeviceInfo.deviceModel()
                if (make.isNotBlank()) put("device_make", make)
                if (model.isNotBlank()) put("device_model", model)
            }
        }

        /**
         * Builds the composite SDK descriptor (schema v1):
         *   `s=schema | b=binding | p=platform | [w=wrapper |] c=core`
         * The wrapper segment (`w`) is present only when a thin wrapper SDK
         * delegates to this engine (e.g. React Native).
         */
        private fun buildSdkVersion(
                binding: String,
                platform: String,
                wrapperVersion: String?,
                core: String,
        ): String =
                buildList {
                            add("s=1")
                            add("b=$binding")
                            add("p=$platform")
                            if (!wrapperVersion.isNullOrBlank()) add("w=$wrapperVersion")
                            add("c=$core")
                        }
                        .joinToString("|")
    }
}
