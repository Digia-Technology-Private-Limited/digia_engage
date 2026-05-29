package com.digia.engage.internal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.internal.model.CampaignModel
import java.util.UUID
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
        com.digia.engage.framework.DigiaFontConfig.fontFamily = config.fontFamily
        analyticsClient.configure(config)

        scope.launch(Dispatchers.IO) {
            try {
                val deviceId = loadOrCreateDeviceId(context.applicationContext)
                val fetcher = CampaignFetcher(config, deviceId)
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

    fun reportOverlayImpression(payload: InAppPayload) {
        displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Impressed, payload)
    }

    fun markOverlayDismissed(payloadId: String) {
        val current = controller.activePayload.value
        if (current?.id == payloadId) {
            displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Dismissed, current)
            controller.dismiss()
        }
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

    fun markSurveyCompleted(response: Map<String, Any?>) {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.trackInternal(
            InternalEngageEvent.SurveyCompleted(response),
            surveyPayload(state),
        )
        surveyOrchestrator.dismiss()
    }

    fun markSurveyDismissed() {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.notifyCEP(DigiaExperienceEvent.Dismissed, surveyPayload(state))
        surveyOrchestrator.dismiss()
    }

    private fun surveyPayload(state: ActiveSurveyState): InAppPayload =
        campaignPayload(state.campaign)

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
        analyticsClient.clear()
        campaignStore.populate(emptyList())
        controller.dismiss()
        controller.clearSlots()
        controller.clearSlotConfigs()
        controller.clearStorySlotConfigs()
        controller.dismissStoryOverlay()
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
        doRouteCampaign(payload)
    }

    private fun doRouteCampaign(payload: InAppPayload) {
        // content["campaign_key"] is set by native CEP plugins; RN bridge uses payload.id directly
        val campaignKey = ((payload.content["campaign_key"] as? String)
            ?: (payload.content["digiaKey"] as? String)
            ?: payload.id.takeIf { it.isNotBlank() })?.trim()
        if (campaignKey.isNullOrBlank()) {
            logWarning("payload dropped: missing campaign_key: ${payload.id}")
            return
        }
        val campaign = campaignStore.find(campaignKey)
        if (campaign == null) {
            logWarning("payload dropped: no campaign found for key '$campaignKey'")
            return
        }
        routeCampaign(campaign, payload)
    }

    private fun routeCampaign(campaign: CampaignModel, payload: InAppPayload = campaignPayload(campaign)) {
        val campaignKey = campaign.campaignKey
        val routedPayload = payloadForCampaign(campaign, payload)
        android.util.Log.d("Digia", "[routeCampaign] routing '$campaignKey' type=${campaign.campaignType}")
        when (campaign.campaignType) {
            "guide" -> guideOrchestrator.start(campaign)
            "nudge" -> displayCoordinator.routeNudge(routedPayload)
            "inline" -> when (val cfg = campaign.config) {
                is com.digia.engage.internal.model.CampaignConfigModel.Inline ->
                    displayCoordinator.routeInlineCarousel(cfg.inlineConfig, routedPayload)
                is com.digia.engage.internal.model.CampaignConfigModel.Story ->
                    displayCoordinator.routeInlineStory(cfg.storyConfig, routedPayload)
                else -> error("unexpected config for inline campaign '$campaignKey'")
            }
            "survey" -> {
                if (!surveyOrchestrator.start(campaign)) {
                    logWarning("survey campaign dropped: another survey is on screen: $campaignKey")
                }
            }
            else -> error("Unknown campaign_type: ${campaign.campaignType}")
        }
    }

    private fun campaignPayload(campaign: CampaignModel): InAppPayload =
        InAppPayload(
            id = campaign.campaignKey,
            content = mapOf(
                "campaign_id" to campaign.id,
                "campaign_key" to campaign.campaignKey,
            ),
            cepContext = emptyMap(),
        )

    private fun payloadForCampaign(campaign: CampaignModel, payload: InAppPayload): InAppPayload =
        payload.copy(
            content = payload.content + mapOf(
                "campaign_id" to campaign.id,
                "campaign_key" to campaign.campaignKey,
            ),
        )

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
    internal fun setCampaignsForTest(campaigns: List<CampaignModel>) {
        campaignStore.populate(campaigns)
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
        analyticsClient.clear()
        screenTracker.clear()
        campaignStore.populate(emptyList())
        controller.dismiss()
        controller.clearSlots()
        controller.clearSlotConfigs()
        controller.clearStorySlotConfigs()
        controller.dismissStoryOverlay()
        guideOrchestrator.dismiss()
        surveyOrchestrator.dismiss()
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }
}
