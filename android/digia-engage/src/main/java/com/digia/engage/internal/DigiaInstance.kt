package com.digia.engage.internal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.digia.engage.CEPTriggerPayload
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEndpoints
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.internal.logging.Logger
import com.digia.engage.internal.analytics.AnalyticsService
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
    private var submissionReporter: SubmissionReporter? = null
    private var completedSurveyToken: Long? = null
    private var firstAnswerSurveyToken: Long? = null

    val guideState = guideOrchestrator.state
    val surveyState = surveyOrchestrator.state

    private val diagnosticsReporter = DiagnosticsReporter(::logWarning)
    private var analyticsService: AnalyticsService? = null
    private val pluginRegistry =
            PluginRegistry(delegate = this, diagnosticsReporter = diagnosticsReporter)
    private val screenTracker = ScreenTracker(onScreenChanged = pluginRegistry::forwardScreen)
    private val displayCoordinator =
            DisplayCoordinator(
                    overlayController = controller,
                    pluginRegistry = pluginRegistry,
                    getAnalyticsService = { analyticsService },
            )

    init {
        controller.onEvent = { event, payload -> displayCoordinator.onOverlayEvent(event, payload) }
    }

    fun initialize(context: Context, config: DigiaConfig) {
        if (!initializationStarted.compareAndSet(false, true)) return
        DigiaEndpoints.configure(config)
        Logger.configure(config.logLevel)
        Logger.verbose("Digia SDK initializing | projectId=${config.apiKey.take(8)}… env=${config.environment}")
        _sdkState.value = SDKState.INITIALIZING
        com.digia.engage.internal.DigiaFontConfig.fontFamily = config.fontFamily
        analyticsService = AnalyticsService.create(context.applicationContext, config, scope)

        val deviceId = resolveDeviceId(context)
        submissionReporter = SubmissionReporter(config, deviceId, scope)

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
                    Logger.verbose("Digia SDK ready | campaigns=${campaigns.size}")
                    registerLifecycleObserver()
                    pluginRegistry.runHealthCheck()
                    if (campaignStore.isEmpty()) Logger.warning("No campaigns fetched — check your projectId and network connection")
                    flushPendingPayloadIfAny()
                }.join()
            } catch (t: Throwable) {
                initializationStarted.set(false)
                scope.launch(Dispatchers.Main.immediate) {
                    _isUiReady.value = false
                    _sdkState.value = SDKState.FAILED
                }
                Logger.error("Digia SDK init failed: ${t.message}")
            }
        }
    }

    fun register(plugin: DigiaCEPPlugin) {
        Logger.verbose("Plugin registered: ${plugin.identifier}")
        pluginRegistry.register(plugin)
        screenTracker.currentScreen?.let(pluginRegistry::forwardScreen)
    }

    fun setCurrentScreen(name: String) {
        Logger.verbose("Screen: $name")
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

    fun setUserId(userId: String) { analyticsService?.setUserId(userId) }
    fun clearUserId() { analyticsService?.clearUserId() }

    fun captureAnalyticsEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        if (analyticsService == null) {
            Log.w("DigiaAnalytics", "[DigiaInstance] captureAnalyticsEvent: analyticsService is NULL — analytics disabled or SDK not initialized")
            return
        }
        Log.d("DigiaAnalytics", "[DigiaInstance] captureAnalyticsEvent → analyticsService.capture: event=${event::class.simpleName} campaignKey=${payload.content["campaign_key"]} campaignId=${payload.content["campaign_id"]}")
        analyticsService?.capture(event, payload)
    }

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
    //   • CEP plugin sees: Impressed (on start), Dismissed.
    //   • Internal analytics only (never the CEP) sees, on the first answered
    //     question: "Digia Experience Clicked" + "Digia Question Answered";
    //     on completion: "Digia Experience Completed".
    //
    // The CEP intentionally does not see the click / answer / completion signals —
    // those are SDK-internal and go only to the Digia analytics sink.

    /** Fired once when the survey first becomes visible (treated as a click). */
    fun reportSurveyStarted() {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.notifyCEP(
            DigiaExperienceEvent.Impressed,
            surveyPayload(state),
        )
    }

    /**
     * Fired when the user answers a survey question. Only the *first* answer of a
     * survey is recorded: it emits "Digia Experience Clicked" (engagement) and
     * "Digia Question Answered", both to the internal analytics sink only — never
     * to the CEP plugin. Subsequent answers are no-ops.
     */
    fun reportSurveyAnswered(stepId: String, answer: Map<String, Any?>) {
        val state = surveyOrchestrator.state.value ?: return
        if (firstAnswerSurveyToken == state.token) return
        firstAnswerSurveyToken = state.token
        val payload = surveyPayload(state)
        displayCoordinator.trackInternal(InternalEngageEvent.ExperienceClicked, payload)
        displayCoordinator.trackInternal(
            InternalEngageEvent.QuestionAnswered(stepId, answer),
            payload,
        )
    }

    fun markSurveyCompleted(
        response: Map<String, Any?>,
        answers: Map<String, SurveyAnswer> = emptyMap(),
    ) {
        reportSurveyCompleted(response, answers)
        markSurveyDismissed()
    }

    fun reportSurveyCompleted(
        response: Map<String, Any?>,
        answers: Map<String, SurveyAnswer> = emptyMap(),
    ) {
        val state = surveyOrchestrator.state.value ?: return
        if (completedSurveyToken == state.token) return
        completedSurveyToken = state.token
        displayCoordinator.trackInternal(
            InternalEngageEvent.ExperienceCompleted(response),
            surveyPayload(state),
        )
        if (answers.isNotEmpty()) {
            Log.d(
                "DigiaInstance",
                "survey submission started: campaignKey=${state.campaign.campaignKey}, campaignId=${state.campaign.id}, answers=${answers.size}",
            )
            submissionReporter?.reportSurveyCompleted(state.campaign, answers, state.startedAtMs)
        }
    }

    fun dismissCompletedSurvey() {
        markSurveyDismissed()
    }

    fun markSurveyDismissed() {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.notifyCEP(DigiaExperienceEvent.Dismissed, surveyPayload(state))
        surveyOrchestrator.dismiss()
    }

    private fun surveyPayload(state: ActiveSurveyState): InAppPayload =
        campaignPayload(state.campaign)

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

    // ── Nudge lifecycle ───────────────────────────────────────────────────────
    //
    // Bottom-sheet / dialog nudges route Viewed / Clicked / Dismissed through the
    // shared overlay path (both CEP plugin.track() and internal analytics). The
    // payload carries campaign_type='nudge' + display_style for the CEP mapping.

    fun reportNudgeImpression() {
        val payload = controller.nudgeOverlay.value?.payload ?: return
        displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Impressed, payload)
    }

    fun emitNudgeClick(elementId: String?) {
        val payload = controller.nudgeOverlay.value?.payload ?: return
        displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Clicked(elementId), payload)
    }

    fun markNudgeDismissed() {
        val payload = controller.nudgeOverlay.value?.payload ?: return
        Logger.verbose("Nudge dismissed: id=${payload.id}")
        displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Dismissed, payload)
        controller.dismissNudge()
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
        analyticsService?.clear()
        analyticsService = null
        campaignStore.populate(emptyList())
        controller.dismiss()
        controller.clearSlots()
        controller.clearSlotConfigs()
        controller.clearStorySlotConfigs()
        controller.dismissStoryOverlay()
        controller.dismissNudge()
        screenTracker.clear()
        guideOrchestrator.dismiss()
        surveyOrchestrator.dismiss()
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }

    override fun onCampaignTriggered(payload: CEPTriggerPayload) {
        val internalPayload = InAppPayload(
            id = payload.cepCampaignId,
            content = buildMap {
                put("campaign_key", payload.campaignKey)
                payload.variables?.let { put("variables", it) }
            },
            cepContext = payload.cepMetadata,
        )
        scope.launch(Dispatchers.Main.immediate) {
            Logger.verbose("Campaign received from CEP: id=${payload.cepCampaignId} sdkState=${_sdkState.value}")
            when (_sdkState.value) {
                SDKState.NOT_INITIALIZED -> {
                    Logger.error("Campaign dropped — SDK not initialized (call Digia.initialize() first): id=${payload.cepCampaignId}")
                    return@launch
                }
                SDKState.INITIALIZING -> {
                    if (pendingPayload != null) {
                        Logger.warning("Pending campaign replaced by newer trigger: id=${payload.cepCampaignId}")
                    } else {
                        Logger.verbose("SDK still initializing — campaign queued: id=${payload.cepCampaignId}")
                    }
                    pendingPayload = internalPayload
                    return@launch
                }
                SDKState.FAILED -> {
                    Logger.error("Campaign dropped — SDK init failed: id=${payload.cepCampaignId}")
                    return@launch
                }
                SDKState.READY -> Unit
            }
            routeCampaign(internalPayload)
        }
    }

    override fun onCampaignInvalidated(campaignId: String) {
        Logger.verbose("Campaign invalidated by CEP: id=$campaignId")
        scope.launch(Dispatchers.Main.immediate) {
            displayCoordinator.dismissNudge(campaignId)
            displayCoordinator.dismissInline(campaignId)
        }
    }

    private fun routeCampaign(payload: InAppPayload) {
        doRouteCampaign(payload)
    }

    private fun doRouteCampaign(payload: InAppPayload) {
        // content["campaign_key"] is set by native CEP plugins; RN bridge uses payload.id directly
        val campaignKey = ((payload.content["digia_campaign_key"] as? String)
            ?: (payload.content["campaign_key"] as? String)
            ?: (payload.content["digiaKey"] as? String)
            ?: payload.id.takeIf { it.isNotBlank() })?.trim()
        if (campaignKey.isNullOrBlank()) {
            Logger.error("Campaign dropped — missing campaign_key in payload: id=${payload.id}. Ensure the CEP plugin sets campaign_key in content.")
            return
        }
        val campaign = campaignStore.find(campaignKey)
        if (campaign == null) {
            Logger.error("Campaign dropped — no campaign found for key '$campaignKey'. Check the key matches a published campaign on the Digia dashboard.")
            return
        }
        Logger.verbose("Campaign resolved: key=$campaignKey type=${campaign.campaignType}")
        routeCampaign(campaign, payload)
    }

    private fun routeCampaign(campaign: CampaignModel, payload: InAppPayload = campaignPayload(campaign)) {
        val campaignKey = campaign.campaignKey
        val routedPayload = payloadForCampaign(campaign, payload)
        Logger.verbose("Routing campaign: key=$campaignKey type=${campaign.campaignType}")
        when (campaign.campaignType) {
            "guide" -> guideOrchestrator.start(campaign, extractVariables(payload.content))
            "nudge" -> {
                val nudgeConfig = campaign.nudgeConfig
                    ?: error("unexpected config for nudge campaign '$campaignKey'")
                // Merge dashboard defaults with the CEP trigger's variables (CEP
                // wins), matching Flutter's `{...defaultVariables, ...payload.variables}`.
                val variables = campaign.defaultVariables + (extractVariables(payload.content) ?: emptyMap())
                displayCoordinator.routeNudge(
                    nudgeConfig,
                    nudgePayload(campaign, routedPayload, nudgeConfig),
                    variables,
                )
            }
            "inline" -> when (val cfg = campaign.config) {
                is com.digia.engage.internal.model.CampaignConfigModel.Inline ->
                    displayCoordinator.routeInlineCarousel(cfg.inlineConfig, routedPayload)
                is com.digia.engage.internal.model.CampaignConfigModel.Story ->
                    displayCoordinator.routeInlineStory(cfg.storyConfig, routedPayload)
                else -> error("unexpected config for inline campaign '$campaignKey'")
            }
            "survey" -> {
                if (!surveyOrchestrator.start(campaign)) {
                    Logger.warning("Survey campaign skipped — another survey is already on screen: key=$campaignKey")
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
                "campaign_type" to campaign.campaignType,
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

    private fun nudgePayload(
        campaign: CampaignModel,
        payload: InAppPayload,
        config: com.digia.engage.internal.model.NudgeConfig,
    ): InAppPayload =
        payload.copy(
            content = payload.content + mapOf(
                "campaign_type" to "nudge",
                "display_style" to config.surface.displayType.displayStyle,
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
                Lifecycle.Event.ON_START -> {
                    pluginRegistry.runHealthCheck()
                    analyticsService?.onLifecycleResume()
                }
                Lifecycle.Event.ON_STOP -> analyticsService?.onLifecycleStop()
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

    private fun loadOrCreateDeviceId(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences("digia_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("digia_device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("digia_device_id", newId).apply()
        return newId
    }

    private fun logWarning(message: String) {
        Logger.warning(message)
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
        analyticsService?.clear()
        analyticsService = null
        screenTracker.clear()
        campaignStore.populate(emptyList())
        controller.dismiss()
        controller.clearSlots()
        controller.clearSlotConfigs()
        controller.clearStorySlotConfigs()
        controller.dismissStoryOverlay()
        controller.dismissNudge()
        guideOrchestrator.dismiss()
        surveyOrchestrator.dismiss()
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }
}
