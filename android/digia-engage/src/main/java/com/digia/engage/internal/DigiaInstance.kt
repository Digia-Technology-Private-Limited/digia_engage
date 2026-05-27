package com.digia.engage.internal

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEndpoints
import com.digia.engage.DigiaEnvironment
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object DigiaInstance : DigiaCEPDelegate {

    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    val controller = DigiaOverlayController()

    private val _isUiReady = MutableStateFlow(false)
    val isUiReady: StateFlow<Boolean> = _isUiReady.asStateFlow()

    private val _sdkState = MutableStateFlow(SDKState.NOT_INITIALIZED)
    val sdkState: StateFlow<SDKState> = _sdkState.asStateFlow()

    private val initializationStarted = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private val lifecycleObserverAttached = AtomicBoolean(false)

    private var lifecycleObserver: LifecycleEventObserver? = null
    private var pendingPayload: InAppPayload? = null

    private val campaignStore = CampaignStore()
    private val anchorRegistry = AnchorRegistry()
    private val guideOrchestrator = GuideOrchestrator()
    private val surveyOrchestrator = SurveyOrchestrator()
    private var submissionReporter: SubmissionReporter? = null

    val guideState = guideOrchestrator.state
    val surveyState = surveyOrchestrator.state

    private val diagnosticsReporter = DiagnosticsReporter(::logWarning)
    private val analyticsClient = AnalyticsClient(diagnosticsReporter)
    private val pluginRegistry =
            PluginRegistry(delegate = this, diagnosticsReporter = diagnosticsReporter)
    private val screenTracker = ScreenTracker(onScreenChanged = pluginRegistry::forwardScreen)
    private val displayCoordinator =
            DisplayCoordinator(
                    overlayController = controller,
                    pluginRegistry = pluginRegistry,
                    analyticsClient = analyticsClient,
            )

    init {
        controller.onEvent = { event, payload -> displayCoordinator.onOverlayEvent(event, payload) }
    }

    fun initialize(context: Context, config: DigiaConfig) {
        if (!initializationStarted.compareAndSet(false, true)) return
        _sdkState.value = SDKState.INITIALIZING

        val deviceId = resolveDeviceId(context)
        submissionReporter = SubmissionReporter(config, deviceId, scope)

        scope.launch(Dispatchers.IO) {
            try {
                val fetcher = CampaignFetcher(config)
                val campaigns = fetcher.fetch()
                campaignStore.populate(campaigns)
                scope.launch(Dispatchers.Main.immediate) {
                    initialized.set(true)
                    _isUiReady.value = true
                    _sdkState.value = SDKState.READY
                    registerLifecycleObserver()
                    pluginRegistry.runHealthCheck()
                    if (campaignStore.isEmpty()) logWarning("No campaigns fetched — CampaignStore is empty")
                    flushPendingPayloadIfAny()
                }.join()
            } catch (t: Throwable) {
                initializationStarted.set(false)
                scope.launch(Dispatchers.Main.immediate) {
                    _isUiReady.value = false
                    _sdkState.value = SDKState.FAILED
                }
                logWarning("Digia.initialize failed: ${t.message}")
            }
        }
    }

    fun register(plugin: DigiaCEPPlugin) {
        pluginRegistry.register(plugin)
        screenTracker.currentScreen?.let(pluginRegistry::forwardScreen)
    }

    fun setCurrentScreen(name: String) {
        screenTracker.setScreen(name)
    }

    fun onHostMounted() { /* overlay is compose-native now, kept for API compat */ }
    fun onHostUnmounted() {}

    val _anchorVersion = MutableStateFlow(0)
    val anchorVersion: StateFlow<Int> = _anchorVersion.asStateFlow()

    fun registerAnchor(key: String, x: Int, y: Int, width: Int, height: Int) {
        anchorRegistry.register(key, ScreenRect(x, y, width, height))
        _anchorVersion.value++
    }

    fun registerAnchorView(key: String, view: android.view.View) {
        val rect = anchorRegistry.find(key) ?: ScreenRect(0, 0, 0, 0)
        anchorRegistry.registerWithView(key, rect, view)
        _anchorVersion.value++
    }

    fun unregisterAnchor(key: String) {
        anchorRegistry.unregister(key)
    }

    fun findAnchor(key: String): ScreenRect? = anchorRegistry.find(key)

    fun anchorRegistrySnapshot(): AnchorRegistry = anchorRegistry

    fun reportHealthEvent(eventType: String, params: Map<String, String>) {
        diagnosticsReporter.reportWarning("[HealthEvent] type=$eventType params=$params")
    }

    fun advanceGuide() { guideOrchestrator.advance() }

    fun dismissGuide() {
        val key = guideOrchestrator.state.value?.campaign?.campaignKey ?: return
        guideOrchestrator.dismiss()
        displayCoordinator.onOverlayEvent(
            DigiaExperienceEvent.Dismissed,
            InAppPayload(id = key, content = emptyMap(), cepContext = emptyMap()),
        )
    }

    // ── Survey lifecycle ────────────────────────────────────────────────────
    //
    // Event routing:
    //   • CEP plugin sees: Clicked (on start), Dismissed.
    //   • Internal analytics sees: SurveyAnswered, SurveyCompleted.
    //
    // The CEP intentionally does not see per-question Answered / Completed —
    // those are SDK-internal signals (handling TBD).

    /** Fired once when the survey first becomes visible (treated as a click). */
    fun reportSurveyStarted() {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.notifyCEP(
            DigiaExperienceEvent.Clicked(elementId = state.campaign.campaignKey),
            surveyPayload(state),
        )
    }

    fun reportSurveyAnswered(stepId: String, answer: Map<String, Any?>) {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.trackInternal(
            InternalEngageEvent.SurveyAnswered(stepId, answer),
            surveyPayload(state),
        )
    }

    fun markSurveyCompleted(
        response: Map<String, Any?>,
        answers: Map<String, SurveyAnswer> = emptyMap(),
    ) {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.trackInternal(
            InternalEngageEvent.SurveyCompleted(response),
            surveyPayload(state),
        )
        if (answers.isNotEmpty()) {
            submissionReporter?.reportSurveyCompleted(state.campaign, answers, state.startedAtMs)
        }
        surveyOrchestrator.dismiss()
    }

    fun markSurveyDismissed() {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.notifyCEP(DigiaExperienceEvent.Dismissed, surveyPayload(state))
        surveyOrchestrator.dismiss()
    }

    private fun surveyPayload(state: ActiveSurveyState): InAppPayload =
        InAppPayload(id = state.campaign.campaignKey, content = emptyMap(), cepContext = emptyMap())

    private fun resolveDeviceId(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences("digia_engage", Context.MODE_PRIVATE)
        prefs.getString("device_id", null)?.let { return it }
        val androidId = runCatching {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )
        }.getOrNull()
        val id = androidId?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: java.util.UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).apply()
        return id
    }

    fun reportSlotImpression(payload: InAppPayload) {
        displayCoordinator.onSlotEvent(DigiaExperienceEvent.Impressed, payload)
    }

    fun markSlotDismissed(payloadId: String, placementKey: String) {
        val current = controller.slotPayloads.value[placementKey]
        if (current?.id == payloadId) {
            displayCoordinator.onSlotEvent(DigiaExperienceEvent.Dismissed, current)
        }
    }

    fun emitExplicitCtaClick(elementId: String?) {
        val payload = controller.activePayload.value ?: return
        displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Clicked(elementId), payload)
    }

    fun teardown() {
        supervisorJob.cancel()
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)
        initializationStarted.set(false)
        initialized.set(false)
        pendingPayload = null
        pluginRegistry.teardown()
        controller.dismiss()
        controller.clearSlots()
        screenTracker.clear()
        guideOrchestrator.dismiss()
        surveyOrchestrator.dismiss()
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }

    override fun onCampaignTriggered(payload: InAppPayload) {
        scope.launch(Dispatchers.Main.immediate) {
            android.util.Log.d("Digia", "[onCampaignTriggered] id=${payload.id} state=${_sdkState.value}")
            when (_sdkState.value) {
                SDKState.NOT_INITIALIZED -> {
                    logWarning("campaign dropped — SDK not initialized: ${payload.id}")
                    return@launch
                }
                SDKState.INITIALIZING -> {
                    if (pendingPayload != null) {
                        logWarning("pending payload replaced by newer payload: ${payload.id}")
                    }
                    pendingPayload = payload
                    return@launch
                }
                SDKState.FAILED -> {
                    logWarning("campaign dropped — SDK initialization failed: ${payload.id}")
                    return@launch
                }
                SDKState.READY -> Unit
            }
            routeCampaign(payload)
        }
    }

    override fun onCampaignInvalidated(campaignId: String) {
        scope.launch(Dispatchers.Main.immediate) {
            displayCoordinator.dismissNudge(campaignId)
            displayCoordinator.dismissInline(campaignId)
        }
    }

    fun triggerCampaign(campaignId: String) {
        scope.launch(Dispatchers.Main.immediate) {
            when (_sdkState.value) {
                SDKState.NOT_INITIALIZED -> {
                    logWarning("test trigger dropped — SDK not initialized: $campaignId")
                    return@launch
                }
                SDKState.INITIALIZING -> {
                    logWarning("test trigger dropped — SDK still initializing: $campaignId")
                    return@launch
                }
                SDKState.FAILED -> {
                    logWarning("test trigger dropped — SDK initialization failed: $campaignId")
                    return@launch
                }
                SDKState.READY -> Unit
            }

            val trimmed = campaignId.trim()
            if (trimmed.isBlank()) {
                logWarning("test trigger dropped — campaign id is blank")
                return@launch
            }
            val campaign = campaignStore.findById(trimmed) ?: campaignStore.find(trimmed)
            if (campaign == null) {
                logWarning("test trigger dropped — no campaign found for id '$trimmed'")
                return@launch
            }
            routeCampaign(campaign)
        }
    }

    private fun routeCampaign(payload: InAppPayload) {
        val campaignKey = (payload.content["campaign_key"] as? String)?.trim()
        if (campaignKey.isNullOrBlank()) {
            logWarning("payload dropped: missing campaign_key: ${payload.id}")
            return
        }
        val campaign = campaignStore.find(campaignKey)
        if (campaign == null) {
            logWarning("payload dropped: no campaign found for key '$campaignKey'")
            return
        }
        routeCampaign(campaign)
    }

    private fun routeCampaign(campaign: com.digia.engage.internal.model.CampaignModel) {
        val campaignKey = campaign.campaignKey
        android.util.Log.d("Digia", "[routeCampaign] routing '$campaignKey' type=${campaign.campaignType}")
        when (campaign.campaignType) {
            "guide" -> guideOrchestrator.start(campaign)
            "survey" -> {
                if (campaign.surveyConfig == null) {
                    logWarning("survey campaign dropped: missing/invalid survey_config: $campaignKey")
                } else if (!surveyOrchestrator.start(campaign)) {
                    logWarning("survey campaign dropped: another survey is on screen: $campaignKey")
                }
            }
            else -> logWarning("campaign type '${campaign.campaignType}' not yet supported")
        }
    }

    private fun flushPendingPayloadIfAny() {
        val payload = pendingPayload ?: return
        pendingPayload = null
        routeCampaign(payload)
    }

    private fun registerLifecycleObserver() {
        if (!lifecycleObserverAttached.compareAndSet(false, true)) return
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> pluginRegistry.runHealthCheck()
                Lifecycle.Event.ON_DESTROY -> teardown()
                else -> Unit
            }
        }
        lifecycleObserver = observer
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        } catch (t: Throwable) {
            lifecycleObserver = null
            lifecycleObserverAttached.set(false)
            throw t
        }
    }

    private fun logWarning(message: String) {
        Log.w("DigiaInstance", message)
    }

    @Suppress("unused")
    internal fun initForTest() {
        initializationStarted.set(true)
        initialized.set(true)
        _isUiReady.value = true
        _sdkState.value = SDKState.READY
    }

    @Suppress("unused")
    internal fun setSdkStateForTest(state: SDKState) { _sdkState.value = state }

    @Suppress("unused")
    internal fun flushPendingPayloadForTest() { flushPendingPayloadIfAny() }

    @Suppress("unused")
    internal fun resetForTest() {
        supervisorJob.cancel()
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)
        initializationStarted.set(false)
        initialized.set(false)
        pendingPayload = null
        pluginRegistry.teardown()
        screenTracker.clear()
        controller.dismiss()
        controller.clearSlots()
        guideOrchestrator.dismiss()
        surveyOrchestrator.dismiss()
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }
}
