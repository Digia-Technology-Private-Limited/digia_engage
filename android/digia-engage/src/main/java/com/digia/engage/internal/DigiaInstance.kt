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

    val guideState = guideOrchestrator.state

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
        controller.clearSlotConfigs()
        screenTracker.clear()
        guideOrchestrator.dismiss()
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

    private fun routeCampaign(payload: InAppPayload) {
        runCatching { doRouteCampaign(payload) }
            .onFailure { e -> logWarning("routeCampaign threw unexpectedly: ${e.message}") }
    }

    private fun doRouteCampaign(payload: InAppPayload) {
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
        android.util.Log.d("Digia", "[routeCampaign] type='${campaign.campaignType}' key='$campaignKey'")
        when (campaign.campaignType) {
            "guide" -> guideOrchestrator.start(campaign)
            "inline" -> {
                val inlineConfig = campaign.inlineConfig
                if (inlineConfig == null) {
                    logWarning("inline campaign '$campaignKey' has no valid carousel config — check template_config JSON")
                    return
                }
                displayCoordinator.routeInlineCarousel(inlineConfig, payload)
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
        controller.clearSlotConfigs()
        guideOrchestrator.dismiss()
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }
}
