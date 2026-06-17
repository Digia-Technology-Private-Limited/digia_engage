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
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.internal.logging.Logger
import com.digia.engage.internal.analytics.AnalyticsService
import com.digia.engage.internal.analytics.EngageMatrix
import com.digia.engage.internal.model.CampaignModel
import com.digia.engage.internal.model.NudgeAction
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

    /**
     * Wall-clock when the active nudge was presented (its `Digia Experience
     * Viewed`). Only one nudge shows at a time, so a single timestamp suffices —
     * drives `time_to_action_ms` (CTA tap) and `time_to_dismiss_ms` (dismiss).
     * Mirrors the Flutter `DigiaInstance` nudge view-clock.
     */
    private var nudgeViewedAtMs: Long? = null

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

    // ── Guide lifecycle ───────────────────────────────────────────────────────
    //
    // Tooltip / spotlight guides emit the rich `Digia Experience Viewed` on first
    // display (coarse Impressed → CEP) and Digia-only `Digia Step Viewed` /
    // `Step Clicked` per step. Ending the guide fires the coarse Dismissed → CEP
    // plus, to Digia, either `Digia Experience Completed` (advanced through the
    // last step) or `Digia Experience Dismissed` (closed early). display_style and
    // item_total come from the guide config. Mirrors the inline/story split.

    private var guideViewedAtMs: Long? = null

    /** Fired once when the guide first becomes visible (anchor resolved). */
    fun reportGuideViewed() {
        val campaign = guideOrchestrator.state.value?.campaign ?: return
        guideViewedAtMs = System.currentTimeMillis()
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Impressed,
            eventName = "Digia Experience Viewed",
            payload = guidePayload(campaign),
            properties = EngageMatrix.containerViewed(
                displayStyle = guideDisplayStyle(campaign),
                itemTotal = guideStepTotal(campaign),
                screenName = screenTracker.currentScreen,
            ),
        )
    }

    /** A guide step became visible — Digia-only `Digia Step Viewed`. */
    fun reportGuideStepViewed(stepIndex: Int) {
        val campaign = guideOrchestrator.state.value?.campaign ?: return
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Step Viewed",
            payload = guidePayload(campaign),
            properties = EngageMatrix.step(
                displayStyle = guideDisplayStyle(campaign),
                itemIndex = stepIndex,
                itemTotal = guideStepTotal(campaign),
                itemId = campaign.guideConfig?.steps?.getOrNull(stepIndex)?.id,
            ),
        )
    }

    /** A guide step CTA was tapped — Digia-only `Digia Step Clicked`. */
    fun emitGuideStepClick(stepIndex: Int, label: String? = null) {
        val campaign = guideOrchestrator.state.value?.campaign ?: return
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Step Clicked",
            payload = guidePayload(campaign),
            properties = EngageMatrix.step(
                displayStyle = guideDisplayStyle(campaign),
                itemIndex = stepIndex,
                itemTotal = guideStepTotal(campaign),
                itemId = campaign.guideConfig?.steps?.getOrNull(stepIndex)?.id,
            ) + (label?.let { mapOf("cta_label" to it) } ?: emptyMap()),
        )
    }

    /** Advanced through the final step — coarse Dismissed → CEP, `Experience Completed` → Digia. */
    fun completeGuide() {
        val campaign = guideOrchestrator.state.value?.campaign ?: return
        guideOrchestrator.dismiss()
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Dismissed,
            eventName = "Digia Experience Completed",
            payload = guidePayload(campaign),
            properties = EngageMatrix.completed(
                displayStyle = guideDisplayStyle(campaign),
                itemTotal = guideStepTotal(campaign),
                timeToCompleteMs = guideViewedAtMs?.let { System.currentTimeMillis() - it },
            ),
            flush = true,
        )
        guideViewedAtMs = null
    }

    fun dismissGuide() {
        val state = guideOrchestrator.state.value ?: return
        val campaign = state.campaign
        guideOrchestrator.dismiss()
        val payload = guidePayload(campaign)
        // The step the user abandoned on …
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Step Dismissed",
            payload = payload,
            properties = EngageMatrix.step(
                displayStyle = guideDisplayStyle(campaign),
                itemIndex = state.stepIndex,
                itemTotal = guideStepTotal(campaign),
            ),
        )
        // … and the container-level dismissal (coarse Dismissed → CEP).
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Dismissed,
            eventName = "Digia Experience Dismissed",
            payload = payload,
            properties = EngageMatrix.nudgeDismissed(
                timeToDismissMs = guideViewedAtMs?.let { System.currentTimeMillis() - it },
            ),
            flush = true,
        )
        guideViewedAtMs = null
    }

    private fun guidePayload(campaign: CampaignModel): InAppPayload =
        InAppPayload(
            id = campaign.campaignKey,
            content = mapOf(
                "campaign_id" to campaign.id,
                "campaign_key" to campaign.campaignKey,
                "campaign_type" to "guide",
                "display_style" to guideDisplayStyle(campaign),
            ),
            cepContext = emptyMap(),
        )

    private fun guideDisplayStyle(campaign: CampaignModel): String =
        campaign.guideConfig?.steps?.firstOrNull()?.displayStyle ?: "tooltip"

    private fun guideStepTotal(campaign: CampaignModel): Int =
        campaign.guideConfig?.steps?.size ?: 1

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

    /**
     * Fired once when the survey first becomes visible. Emits the coarse
     * [DigiaExperienceEvent.Impressed] to the CEP plugin and the rich
     * `Digia Experience Viewed` (display_style=`standard`, item_total=question
     * count) to Digia analytics — the survey's container-level view.
     */
    fun reportSurveyStarted() {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Impressed,
            eventName = "Digia Experience Viewed",
            payload = surveyPayload(state),
            properties = EngageMatrix.containerViewed(
                displayStyle = "standard",
                itemTotal = surveyQuestionTotal(state),
                screenName = screenTracker.currentScreen,
            ),
        )
    }

    /**
     * Fired when a survey question first becomes visible. Rich `Digia Question
     * Viewed` to Digia only — the CEP plugin never sees per-question signals.
     */
    fun reportSurveyQuestionViewed(nodeId: String) {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Question Viewed",
            payload = surveyPayload(state),
            properties = surveyQuestionProps(state, nodeId),
        )
    }

    /**
     * Fired when the user skips an optional survey question (advances without an
     * answer). Rich `Digia Question Skipped` to Digia only.
     */
    fun reportSurveyQuestionSkipped(nodeId: String) {
        val state = surveyOrchestrator.state.value ?: return
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Question Skipped",
            payload = surveyPayload(state),
            properties = surveyQuestionProps(state, nodeId),
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
        val payload = surveyPayload(state)
        // First answer of the survey is the engagement signal: a one-shot
        // `Digia Experience Clicked` (Digia-only, never the CEP plugin).
        if (firstAnswerSurveyToken != state.token) {
            firstAnswerSurveyToken = state.token
            displayCoordinator.trackInternal(InternalEngageEvent.ExperienceClicked, payload)
        }
        // Per-question rich `Digia Question Answered` (Digia-only) carrying the
        // question position/type plus the answer payload.
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Question Answered",
            payload = payload,
            properties = surveyQuestionProps(state, stepId) + mapOf("answer" to answer),
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
        // Coarse Dismissed → CEP; rich `Digia Experience Dismissed` → Digia,
        // flushed immediately as a terminal event.
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Dismissed,
            eventName = "Digia Experience Dismissed",
            payload = surveyPayload(state),
            properties = EngageMatrix.nudgeDismissed(),
            flush = true,
        )
        surveyOrchestrator.dismiss()
    }

    private fun surveyPayload(state: ActiveSurveyState): InAppPayload =
        campaignPayload(state.campaign)

    /** Count of answerable (non-content) question nodes in the survey. */
    private fun surveyQuestionTotal(state: ActiveSurveyState): Int =
        surveyQuestionNodes(state).size

    /**
     * Assembles the matrix `question` properties for [nodeId]: its 0-based index
     * among answerable questions, the question total, the node id, and the block
     * type token (e.g. `single_select`).
     */
    private fun surveyQuestionProps(state: ActiveSurveyState, nodeId: String): Map<String, Any?> {
        val questions = surveyQuestionNodes(state)
        val index = questions.indexOfFirst { it.id == nodeId }.takeIf { it >= 0 } ?: 0
        val type = state.config.nodeById(nodeId)
            ?.let { state.config.blockFor(it) }?.type?.name?.lowercase()
        return EngageMatrix.question(
            questionIndex = index,
            questionTotal = questions.size,
            questionId = nodeId,
            questionType = type,
        )
    }

    private fun surveyQuestionNodes(state: ActiveSurveyState) =
        state.config.nodes.filter { node ->
            state.config.blockFor(node)?.type?.isContent == false
        }

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
        nudgeViewedAtMs = System.currentTimeMillis()
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Impressed,
            eventName = "Digia Experience Viewed",
            payload = payload,
            properties = EngageMatrix.nudgeViewed(
                displayStyle = payload.content["display_style"] as? String ?: "dialog",
                screenName = screenTracker.currentScreen,
                triggerType = payload.cepContext["trigger_type"] as? String,
                triggerEvent = payload.cepContext["trigger_event"] as? String,
            ),
        )
    }

    /**
     * Fired when an actionable nudge button (a primary CTA, or any button carrying
     * actions) is tapped. Coarse Clicked → CEP; rich `Digia Experience Clicked`
     * (with the synthesised `cta_*` element_id + action/timing context) → Digia.
     */
    fun emitNudgeClick(label: String, isPrimary: Boolean, actions: List<NudgeAction>) {
        val payload = controller.nudgeOverlay.value?.payload ?: return
        val position = if (isPrimary) "primary" else "secondary"
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Clicked("cta_$position"),
            eventName = "Digia Experience Clicked",
            payload = payload,
            properties = EngageMatrix.nudgeClicked(
                label = label,
                isPrimary = isPrimary,
                actions = actions,
                timeToActionMs = nudgeElapsedMs(),
            ),
        )
    }

    fun markNudgeDismissed(dismissReason: String? = null) {
        val payload = controller.nudgeOverlay.value?.payload ?: return
        Logger.verbose("Nudge dismissed: id=${payload.id}")
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Dismissed,
            eventName = "Digia Experience Dismissed",
            payload = payload,
            properties = EngageMatrix.nudgeDismissed(
                dismissReason = dismissReason,
                timeToDismissMs = nudgeElapsedMs(),
            ),
            flush = true,
        )
        nudgeViewedAtMs = null
        controller.dismissNudge()
    }

    /** Milliseconds since the active nudge was viewed, or null if unknown. */
    private fun nudgeElapsedMs(): Long? =
        nudgeViewedAtMs?.let { System.currentTimeMillis() - it }

    // ── Inline lifecycle ──────────────────────────────────────────────────────
    //
    // Inline rails (carousel / story) fire the rich `Digia Experience Viewed` on
    // first render (coarse Impressed → CEP, rich → Digia) and Digia-only `Digia
    // Step Viewed` / `Step Clicked` per slide. Mirrors the Flutter DigiaSlot.

    fun reportInlineViewed(payload: InAppPayload, displayStyle: String, itemTotal: Int) {
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Impressed,
            eventName = "Digia Experience Viewed",
            payload = payload,
            properties = EngageMatrix.containerViewed(
                displayStyle = displayStyle,
                itemTotal = itemTotal,
                screenName = screenTracker.currentScreen,
                triggerType = payload.cepContext["trigger_type"] as? String,
                triggerEvent = payload.cepContext["trigger_event"] as? String,
            ),
        )
    }

    fun reportInlineStep(payload: InAppPayload, displayStyle: String, itemIndex: Int, itemTotal: Int) {
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Step Viewed",
            payload = payload,
            properties = EngageMatrix.step(displayStyle, itemIndex, itemTotal),
        )
    }

    fun emitInlineStepClick(
        payload: InAppPayload,
        displayStyle: String,
        itemIndex: Int,
        itemTotal: Int,
        deepLink: String? = null,
    ) {
        val hasLink = !deepLink.isNullOrEmpty()
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Step Clicked",
            payload = payload,
            properties = EngageMatrix.step(
                displayStyle = displayStyle,
                itemIndex = itemIndex,
                itemTotal = itemTotal,
                actionType = if (hasLink) "deeplink" else null,
                actionUrl = if (hasLink) deepLink else null,
            ),
        )
    }

    /** A story rail card was tapped open — the matrix `Digia Experience Clicked`. */
    fun emitStoryOpened(payload: InAppPayload, itemIndex: Int, itemTotal: Int) {
        displayCoordinator.emitMatrix(
            coarse = DigiaExperienceEvent.Clicked("story_card"),
            eventName = "Digia Experience Clicked",
            payload = payload,
            properties = EngageMatrix.step("story", itemIndex, itemTotal),
        )
    }

    /** A story frame became visible — Digia-only `Digia Step Viewed`. */
    fun reportStoryFrameViewed(payload: InAppPayload, itemIndex: Int, itemTotal: Int) =
        reportInlineStep(payload, "story", itemIndex, itemTotal)

    /** A story played through every frame — the matrix `Digia Experience Completed`. */
    fun markStoryCompleted(payload: InAppPayload, itemTotal: Int, timeToCompleteMs: Long? = null) {
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Experience Completed",
            payload = payload,
            properties = EngageMatrix.completed("story", itemTotal, timeToCompleteMs),
            flush = true,
        )
    }

    /** A story was closed before completing — Digia-only `Digia Step Dismissed`. */
    fun markStoryDismissed(payload: InAppPayload, itemIndex: Int, itemTotal: Int) {
        displayCoordinator.emitMatrix(
            coarse = null,
            eventName = "Digia Step Dismissed",
            payload = payload,
            properties = EngageMatrix.step("story", itemIndex, itemTotal),
            flush = true,
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
