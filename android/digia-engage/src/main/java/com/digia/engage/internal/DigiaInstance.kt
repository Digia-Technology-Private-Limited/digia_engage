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
import com.digia.engage.internal.analytics.AnalyticsService
import com.digia.engage.internal.event.CarouselEvent
import com.digia.engage.internal.event.CepPluginSink
import com.digia.engage.internal.event.DigiaAnalyticsSink
import com.digia.engage.internal.event.DwellTracker
import com.digia.engage.internal.event.EngageAnalyticsEvent
import com.digia.engage.internal.event.EngageEventEmitter
import com.digia.engage.internal.event.GuideEvent
import com.digia.engage.internal.event.NudgeEvent
import com.digia.engage.internal.event.StoriesEvent
import com.digia.engage.internal.event.SurveyEvent
import com.digia.engage.internal.logging.Logger
import com.digia.engage.internal.model.BranchingType
import com.digia.engage.internal.model.CampaignModel
import com.digia.engage.internal.model.SurveyBlock
import com.digia.engage.internal.model.SurveyBlockType
import com.digia.engage.internal.model.SurveyConfigModel
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
    private var pendingPayload: CEPTriggerPayload? = null

    private val campaignStore = CampaignStore()
    private val anchorRegistry = AnchorRegistry()
    private val guideOrchestrator = GuideOrchestrator()
    private val surveyOrchestrator = SurveyOrchestrator()
    private var submissionReporter: SubmissionReporter? = null
    private var completedSurveyToken: Long? = null
    private var welcomeStartToken: Long? = null
    /**
     * Per-question viewed-at timestamps, keyed by "<surveyToken>:<nodeId>". Used to
     * compute `time_to_answer_ms` on QuestionAnswered.
     */
    private val questionViewedAt = mutableMapOf<String, Long>()

    val guideState = guideOrchestrator.state
    val surveyState = surveyOrchestrator.state

    private val diagnosticsReporter = DiagnosticsReporter(::logWarning)
    private var analyticsService: AnalyticsService? = null
    private val pluginRegistry =
            PluginRegistry(delegate = this, diagnosticsReporter = diagnosticsReporter)
    private val screenTracker = ScreenTracker(onScreenChanged = pluginRegistry::forwardScreen)

    // Event system (mirrors Flutter): a fan-out router over two sinks, fronted by
    // an intent-revealing emitter (toCep / toDigia / toAll). Campaign id/type are
    // resolved from the store inside the Digia sink at event time.
    private val analyticsSink =
            DigiaAnalyticsSink(
                    getAnalyticsService = { analyticsService },
                    getCampaign = { campaignStore.find(it) },
            )
    private val events =
            EngageEventEmitter(
                    cep = CepPluginSink(pluginRegistry),
                    digia = analyticsSink,
            )

    // Tracks viewed→dismissed duration (dwell_ms) per campaign instance.
    private val dwellTracker = DwellTracker()

    private val displayCoordinator = DisplayCoordinator(overlayController = controller)

    init {
        // Forward overlay CTA actions to the active CEP plugin (native open is the
        // renderer's fallback when no plugin handles it).
        controller.onAction = { actionType, url, payload ->
            pluginRegistry.notifyAction(actionType, url, payload)
        }
    }

    fun initialize(context: Context, config: DigiaConfig) {
        if (!initializationStarted.compareAndSet(false, true)) return
        DigiaEndpoints.configure(config)
        Logger.configure(config.logLevel)
        Logger.verbose(
                "Digia SDK initializing | projectId=${config.apiKey.take(8)}… env=${config.environment}"
        )
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
                scope
                        .launch(Dispatchers.Main.immediate) {
                            initialized.set(true)
                            _isUiReady.value = true
                            _sdkState.value = SDKState.READY
                            Logger.verbose("Digia SDK ready | campaigns=${campaigns.size}")
                            registerLifecycleObserver()
                            pluginRegistry.runHealthCheck()
                            if (campaignStore.isEmpty())
                                    Logger.warning(
                                            "No campaigns fetched — check your projectId and network connection"
                                    )
                            flushPendingPayloadIfAny()
                        }
                        .join()
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

    fun onHostMounted() {
        /* overlay is compose-native now, kept for API compat */
    }
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

    fun setUserId(userId: String) {
        analyticsService?.setUserId(userId)
    }
    fun clearUserId() {
        analyticsService?.clearUserId()
    }

    /**
     * Public analytics entry point for JS-rendered RN campaigns (guides). The JS
     * layer fires each lifecycle event by its Engage matrix [eventName] with
     * wire-keyed [props]; this maps it to the typed analytics event and records it
     * to Digia. CEP forwarding for JS-rendered campaigns is handled JS-side by the
     * registered plugins.
     */
    fun captureAnalyticsEvent(campaignKey: String, eventName: String, props: Map<String, Any?>) {
        val event = guideEventFor(eventName, props) ?: run {
            Logger.warning("captureAnalyticsEvent: unsupported event '$eventName' for key '$campaignKey' — skipped")
            return
        }
        val campaign = campaignStore.find(campaignKey)
        val payload = CEPTriggerPayload(
            cepCampaignId = campaign?.id ?: campaignKey,
            campaignKey = campaignKey,
        )
        events.toDigia(event, payload)
    }

    fun reportHealthEvent(eventType: String, params: Map<String, String>) {
        diagnosticsReporter.reportWarning("[HealthEvent] type=$eventType params=$params")
    }

    fun advanceGuide() {
        guideOrchestrator.advance()
    }

    fun dismissGuide() {
        val state = guideOrchestrator.state.value ?: return
        val payload = state.payload
        guideOrchestrator.dismiss()
        events.toBoth(
                DigiaExperienceEvent.Dismissed,
                GuideEvent.Dismissed(
                        abandonedAtItem = state.stepIndex + 1,
                        itemTotal = state.campaign.guideConfig?.steps?.size,
                        dwellMs = dwellTracker.consumeDwellMs(payload.cepCampaignId),
                ),
                payload,
        )
    }

    // ── RN guide events ──────────────────────────────────────────────────────
    //
    // Guides are rendered in JS on React Native. The JS layer fires each guide
    // lifecycle event with wire-keyed props (via captureAnalyticsEvent); this maps
    // it to the typed [GuideEvent] (per the Engage matrix). CEP forwarding for RN
    // guides is handled JS-side by the registered plugins.

    private fun guideEventFor(eventName: String, props: Map<String, Any?>): EngageAnalyticsEvent? {
        fun str(k: String) = props[k] as? String
        fun int(k: String) = (props[k] as? Number)?.toInt()
        fun long(k: String) = (props[k] as? Number)?.toLong()
        return when (eventName) {
            "Digia Experience Viewed" -> GuideEvent.Viewed(
                displayStyle = str("display_style").orEmpty(),
                itemTotal = int("step_total") ?: 0,
                screenName = screenTracker.currentScreen,
            )
            "Digia Step Viewed" -> GuideEvent.StepViewed(
                itemIndex = int("step_index") ?: 0,
                itemTotal = int("step_total") ?: 0,
                anchorKey = str("anchor_key"),
                displayStyle = str("display_style"),
            )
            // Guides only have Step Clicked in the matrix (no Experience Clicked).
            "Digia Step Clicked" -> GuideEvent.StepClicked(
                itemIndex = int("step_index") ?: 0,
                elementId = str("element_id"),
                ctaLabel = str("cta_label"),
                actionType = str("action_type"),
                actionUrl = str("action_url"),
            )
            "Digia Step Dismissed" -> GuideEvent.StepDismissed(itemIndex = int("step_index") ?: 0)
            "Digia Experience Dismissed" -> GuideEvent.Dismissed(
                abandonedAtItem = int("abandoned_at_step") ?: int("step_index"),
                itemTotal = int("step_total"),
                dwellMs = long("dwell_ms"),
            )
            "Digia Experience Completed" -> GuideEvent.Completed(itemTotal = int("step_total"))
            else -> null
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
        val payload = surveyPayload(state)
        val config = state.config
        dwellTracker.markViewed(payload.cepCampaignId)
        events.toBoth(
                DigiaExperienceEvent.Impressed,
                SurveyEvent.Viewed(
                        itemTotal = config.questionCount(),
                        hasWelcome = config.hasWelcome(),
                        hasThanks = config.hasThanks(),
                        hasBranching = config.hasBranching(),
                        screenName = screenTracker.currentScreen,
                ),
                payload,
        )
    }

    /**
     * The survey's start engagement — fired once per showing. When a welcome screen
     * is present this is its "Start" CTA tap; when there's no welcome screen it is
     * raised on the first continue (see [reportSurveyAnswered]/[reportSurveyQuestionSkipped]).
     */
    fun reportSurveyWelcomeStart() {
        val state = surveyOrchestrator.state.value ?: return
        if (welcomeStartToken == state.token) return
        welcomeStartToken = state.token
        events.toDigia(SurveyEvent.Clicked(elementId = "welcome_start"), surveyPayload(state))
    }

    /** When no welcome screen exists, the first continue is the start engagement. */
    private fun ensureWelcomeStartIfNoWelcome(state: ActiveSurveyState) {
        if (!state.config.hasWelcome()) reportSurveyWelcomeStart()
    }

    /** A survey question became visible. [itemIndex] is its 1-based shown position. */
    fun reportSurveyQuestionViewed(nodeId: String, itemIndex: Int) {
        val state = surveyOrchestrator.state.value ?: return
        val block = state.config.blockForNode(nodeId) ?: return
        if (block.type.isContent) return
        questionViewedAt[questionKey(state.token, nodeId)] = System.currentTimeMillis()
        val typeWire = block.type.wireName()
        events.toDigia(
                SurveyEvent.QuestionViewed(
                        questionId = nodeId,
                        questionType = typeWire,
                        itemIndex = itemIndex,
                        itemTotal = state.config.questionCount(),
                        blockType = typeWire,
                        blockId = block.id,
                        isRequired = block.required,
                        questionTitle = questionTitle(block),
                ),
                surveyPayload(state),
        )
    }

    /** An eligible optional question was skipped (advanced without an answer). */
    fun reportSurveyQuestionSkipped(nodeId: String, itemIndex: Int) {
        val state = surveyOrchestrator.state.value ?: return
        ensureWelcomeStartIfNoWelcome(state)
        val block = state.config.blockForNode(nodeId) ?: return
        questionViewedAt.remove(questionKey(state.token, nodeId))
        events.toDigia(
                SurveyEvent.QuestionSkipped(
                        questionId = nodeId,
                        itemIndex = itemIndex,
                        blockType = block.type.wireName(),
                        blockId = block.id,
                        questionTitle = questionTitle(block),
                ),
                surveyPayload(state),
        )
    }

    /** Fired each time the user answers a question (one event per answered question). */
    fun reportSurveyAnswered(stepId: String, answer: Map<String, Any?>) {
        val state = surveyOrchestrator.state.value ?: return
        ensureWelcomeStartIfNoWelcome(state)
        val block = state.config.blockForNode(stepId)
        @Suppress("UNCHECKED_CAST") val values = (answer["values"] as? List<String>).orEmpty()
        val comment = answer["comment"] as? String
        val viewedKey = questionKey(state.token, stepId)
        val timeToAnswerMs = questionViewedAt[viewedKey]?.let { System.currentTimeMillis() - it }
        questionViewedAt.remove(viewedKey)
        val scale = block?.let(::scaleBounds)
        events.toDigia(
                SurveyEvent.QuestionAnswered(
                        questionId = stepId,
                        questionType = block?.type?.wireName(),
                        questionTitle = block?.let(::questionTitle),
                        answerValue = values.firstOrNull(),
                        answerText = comment
                                        ?: values.joinToString(", ").takeIf { it.isNotBlank() },
                        blockType = block?.type?.wireName(),
                        blockId = block?.id,
                        answerLabel = block?.let { answerLabel(it, values) },
                        answerOptions = values.takeIf { it.size > 1 },
                        scaleMin = scale?.first,
                        scaleMax = scale?.second,
                        timeToAnswerMs = timeToAnswerMs,
                        answer = answer,
                ),
                surveyPayload(state),
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
        val answeredCount = if (answers.isNotEmpty()) answers.size else response.size
        events.toDigia(
                SurveyEvent.Completed(
                        itemTotal = state.config.questionCount(),
                        answeredCount = answeredCount,
                        timeToCompleteMs = System.currentTimeMillis() - state.startedAtMs,
                        response = response,
                ),
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

    fun markSurveyDismissed(abandonedAtItem: Int? = null, answeredCount: Int? = null) {
        val state = surveyOrchestrator.state.value ?: return
        val payload = surveyPayload(state)
        events.toBoth(
                DigiaExperienceEvent.Dismissed,
                SurveyEvent.Dismissed(
                        abandonedAtItem = abandonedAtItem,
                        itemTotal = state.config.questionCount(),
                        answeredCount = answeredCount,
                        dwellMs = dwellTracker.consumeDwellMs(payload.cepCampaignId),
                ),
                payload,
        )
        clearQuestionViewedAt(state.token)
        surveyOrchestrator.dismiss()
    }

    private fun clearQuestionViewedAt(token: Long) {
        val prefix = "$token:"
        questionViewedAt.entries.removeAll { it.key.startsWith(prefix) }
    }

    /** Stable per-question key. Scoped by survey token so a re-show doesn't reuse a stale viewed-at. */
    private fun questionKey(token: Long, nodeId: String): String = "$token:$nodeId"

    /** Block title, trimmed; null when empty (blank titles aren't worth shipping). */
    private fun questionTitle(block: SurveyBlock): String? =
            block.title.text.trim().takeIf { it.isNotEmpty() }

    /**
     * Comma-joined labels for the selected option ids on a choice block. Null when the
     * block has no options or no selection matches (e.g. rating/nps/text inputs whose
     * answer values aren't option ids).
     */
    private fun answerLabel(block: SurveyBlock, values: List<String>): String? {
        if (values.isEmpty() || block.options.isEmpty()) return null
        val labels = values.mapNotNull { id -> block.options.firstOrNull { it.id == id }?.label }
        return labels.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** Numeric scale bounds for scored blocks (Rating 1–5, NPS 0–10). */
    private fun scaleBounds(block: SurveyBlock): Pair<Int, Int>? = when (block.type) {
        SurveyBlockType.RATING -> 1 to 5
        SurveyBlockType.NPS, SurveyBlockType.NPS_EMOJI, SurveyBlockType.NPS_SMILEY -> 0 to 10
        else -> null
    }

    private fun surveyPayload(state: ActiveSurveyState): CEPTriggerPayload = state.payload

    // ── Survey config metrics (Engage matrix props) ─────────────────────────

    /** Configured questions = graph nodes whose block is an actual prompt (not content chrome). */
    private fun SurveyConfigModel.questionCount(): Int =
            nodes.count { node -> blockFor(node)?.type?.isContent == false }

    private fun SurveyConfigModel.hasWelcome(): Boolean = welcomeBlock() != null

    private fun SurveyConfigModel.hasThanks(): Boolean =
            blocks.any { it.type == SurveyBlockType.RESULT_PAGE }

    private fun SurveyConfigModel.hasBranching(): Boolean =
            nodes.any { it.branching.type != BranchingType.LINEAR }

    private fun SurveyConfigModel.blockForNode(
            nodeId: String
    ): com.digia.engage.internal.model.SurveyBlock? = nodeById(nodeId)?.let { blockFor(it) }

    /** Block type as the matrix's input-primitive string, e.g. SINGLE_SELECT → "single_select". */
    private fun SurveyBlockType.wireName(): String = name.lowercase()

    private fun resolveDeviceId(context: Context): String {
        val prefs =
                context.applicationContext.getSharedPreferences(
                        "digia_engage",
                        Context.MODE_PRIVATE
                )
        prefs.getString("device_id", null)?.let {
            return it
        }
        val androidId =
                runCatching {
                            android.provider.Settings.Secure.getString(
                                    context.contentResolver,
                                    android.provider.Settings.Secure.ANDROID_ID,
                            )
                        }
                        .getOrNull()
        val id =
                androidId?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                        ?: java.util.UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).apply()
        return id
    }

    // ── Nudge lifecycle ───────────────────────────────────────────────────────
    //
    // Impression and Dismissed go to both CEP and Digia analytics (toAll); a
    // primary-button Click is a Digia-only engagement signal (toDigia), matching
    // Flutter's NudgeNodeRenderer.

    fun reportNudgeImpression() {
        val state = controller.nudgeOverlay.value ?: return
        dwellTracker.markViewed(state.payload.cepCampaignId)
        events.toBoth(
                DigiaExperienceEvent.Impressed,
                NudgeEvent.Viewed(
                        displayStyle = state.config.surface.displayType.displayStyle,
                        screenName = screenTracker.currentScreen,
                ),
                state.payload,
        )
    }

    fun emitNudgeClick(
            elementId: String? = null,
            ctaLabel: String? = null,
            actionType: String? = null,
            actionUrl: String? = null,
            ctaRole: String? = null,
    ) {
        val payload = controller.nudgeOverlay.value?.payload ?: return
        events.toDigia(
                NudgeEvent.Clicked(
                        elementId = elementId,
                        ctaLabel = ctaLabel,
                        actionType = actionType,
                        actionUrl = actionUrl,
                        ctaRole = ctaRole,
                        // ms since the nudge was viewed (peek — the nudge is still open).
                        timeToActionMs = dwellTracker.elapsedMs(payload.cepCampaignId),
                ),
                payload,
        )
    }

    fun markNudgeDismissed() {
        val state = controller.nudgeOverlay.value ?: return
        Logger.verbose("Nudge dismissed: id=${state.payload.cepCampaignId}")
        events.toBoth(
                DigiaExperienceEvent.Dismissed,
                NudgeEvent.Dismissed(
                        dwellMs = dwellTracker.consumeDwellMs(state.payload.cepCampaignId)
                ),
                state.payload,
        )
        controller.dismissNudge()
    }

    // ── Inline slot lifecycle ───────────────────────────────────────────────
    //
    // CEP is Impressed + Dismissed instantly at route time (syncTemplate
    // semantics — see routeCampaign). Digia's impression fires once, when the
    // slot first actually renders, deduped per campaign.

    fun reportSlotFirstRender(payload: CEPTriggerPayload) {
        val campaign = campaignStore.find(payload.campaignKey) ?: return
        val viewed: EngageAnalyticsEvent =
                when (val cfg = campaign.config) {
                    is com.digia.engage.internal.model.CampaignConfigModel.Inline ->
                            CarouselEvent.Viewed(
                                    itemTotal = cfg.inlineConfig.items.size,
                                    slotKey = cfg.inlineConfig.slotKey,
                                    screenName = screenTracker.currentScreen,
                            )
                    is com.digia.engage.internal.model.CampaignConfigModel.Story ->
                            StoriesEvent.Viewed(
                                    itemTotal = cfg.storyConfig.items.size,
                                    slotKey = cfg.storyConfig.slotKey,
                                    screenName = screenTracker.currentScreen,
                            )
                    else -> return
                }
        events.digiaImpressionOnce(payload, viewed)
    }

    /** A carousel item scrolled into view. [auto] = autoplay advance vs manual swipe. */
    fun reportCarouselStepViewed(
            payload: CEPTriggerPayload,
            itemIndex: Int,
            itemTotal: Int,
            auto: Boolean
    ) {
        events.toDigia(
                CarouselEvent.StepViewed(itemIndex = itemIndex, itemTotal = itemTotal, auto = auto),
                payload,
        )
    }

    /** A carousel item (or its CTA) was tapped. */
    fun reportCarouselStepClicked(payload: CEPTriggerPayload, itemIndex: Int, actionUrl: String?) {
        val actionType = actionUrl?.let { "deeplink" }
        // The first item tap also counts as an experience-level engagement click (once).
        events.digiaExperienceClickedOnce(
                payload,
                CarouselEvent.Clicked(actionType = actionType, actionUrl = actionUrl),
        )
        events.toDigia(
                CarouselEvent.StepClicked(
                        itemIndex = itemIndex,
                        actionType = actionType,
                        actionUrl = actionUrl
                ),
                payload,
        )
    }

    // ── Inline story events (per the Engage matrix) ─────────────────────────

    /** A story was opened (ring/thumbnail tapped) — drives open rate. */
    fun reportStoryOpened(payload: CEPTriggerPayload) {
        events.toDigia(StoriesEvent.Opened(), payload)
    }

    /** A story frame became visible. [itemIndex] is 1-based; [itemTotal] = frames in this story. */
    fun reportStoryStepViewed(payload: CEPTriggerPayload, itemIndex: Int, itemTotal: Int) {
        events.toDigia(StoriesEvent.StepViewed(itemIndex = itemIndex, itemTotal = itemTotal), payload)
    }

    /** A CTA inside a story frame was tapped. */
    fun reportStoryStepClicked(
            payload: CEPTriggerPayload,
            itemIndex: Int,
            ctaLabel: String?,
            actionType: String?,
            actionUrl: String?,
    ) {
        events.toDigia(
                StoriesEvent.StepClicked(
                        itemIndex = itemIndex,
                        ctaLabel = ctaLabel,
                        actionType = actionType,
                        actionUrl = actionUrl,
                ),
                payload,
        )
    }

    /** Story closed before the last frame. [itemIndex] is the 1-based frame on close. */
    fun reportStoryStepDismissed(payload: CEPTriggerPayload, itemIndex: Int) {
        events.toDigia(StoriesEvent.StepDismissed(itemIndex = itemIndex), payload)
    }

    /** Last story frame viewed. [itemTotal] = frames viewed; [timeToCompleteMs] from open. */
    fun reportStoryCompleted(payload: CEPTriggerPayload, itemTotal: Int, timeToCompleteMs: Long?) {
        events.toDigia(
                StoriesEvent.Completed(itemTotal = itemTotal, timeToCompleteMs = timeToCompleteMs),
                payload,
        )
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
        events.clearImpressions()
        dwellTracker.clear()
        questionViewedAt.clear()
        completedSurveyToken = null
        welcomeStartToken = null
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }

    override fun onCampaignTriggered(payload: CEPTriggerPayload) {
        scope.launch(Dispatchers.Main.immediate) {
            Logger.verbose(
                    "Campaign received from CEP: id=${payload.cepCampaignId} sdkState=${_sdkState.value}"
            )
            when (_sdkState.value) {
                SDKState.NOT_INITIALIZED -> {
                    Logger.error(
                            "Campaign dropped — SDK not initialized (call Digia.initialize() first): id=${payload.cepCampaignId}"
                    )
                    return@launch
                }
                SDKState.INITIALIZING -> {
                    if (pendingPayload != null) {
                        Logger.warning(
                                "Pending campaign replaced by newer trigger: id=${payload.cepCampaignId}"
                        )
                    } else {
                        Logger.verbose(
                                "SDK still initializing — campaign queued: id=${payload.cepCampaignId}"
                        )
                    }
                    pendingPayload = payload
                    return@launch
                }
                SDKState.FAILED -> {
                    Logger.error("Campaign dropped — SDK init failed: id=${payload.cepCampaignId}")
                    return@launch
                }
                SDKState.READY -> Unit
            }
            routeCampaign(payload)
        }
    }

    override fun onCampaignInvalidated(campaignId: String) {
        Logger.verbose("Campaign invalidated by CEP: id=$campaignId")
        scope.launch(Dispatchers.Main.immediate) {
            displayCoordinator.dismissNudge(campaignId)
            displayCoordinator.dismissInline(campaignId)
            // Forget the impression mark so a re-trigger impresses to Digia afresh.
            events.resetImpression(campaignId)
        }
    }

    private fun routeCampaign(payload: CEPTriggerPayload) {
        doRouteCampaign(payload)
    }

    private fun doRouteCampaign(payload: CEPTriggerPayload) {
        val campaignKey =
                payload.campaignKey.takeIf { it.isNotBlank() }?.trim()
                        ?: payload.cepCampaignId.takeIf { it.isNotBlank() }?.trim()
        if (campaignKey.isNullOrBlank()) {
            Logger.error(
                    "Campaign dropped — missing campaignKey in payload: id=${payload.cepCampaignId}. Ensure the CEP plugin sets campaignKey on the CEPTriggerPayload."
            )
            return
        }
        val campaign = campaignStore.find(campaignKey)
        if (campaign == null) {
            Logger.error(
                    "Campaign dropped — no campaign found for key '$campaignKey'. Check the key matches a published campaign on the Digia dashboard."
            )
            return
        }
        Logger.verbose("Campaign resolved: key=$campaignKey type=${campaign.campaignType}")
        routeCampaign(campaign, payload)
    }

    private fun routeCampaign(campaign: CampaignModel, payload: CEPTriggerPayload) {
        val campaignKey = campaign.campaignKey
        Logger.verbose("Routing campaign: key=$campaignKey type=${campaign.campaignType}")
        when (campaign.campaignType) {
            "guide" -> {
                dwellTracker.markViewed(payload.cepCampaignId)
                val guideContext = buildVariableContext(campaign.variableSchemas, payload.variables)
                guideOrchestrator.start(campaign, payload, guideContext)
            }
            "nudge" -> {
                val nudgeConfig =
                        campaign.nudgeConfig
                                ?: error("unexpected config for nudge campaign '$campaignKey'")
                val context = buildVariableContext(campaign.variableSchemas, payload.variables)
                displayCoordinator.routeNudge(nudgeConfig, payload, context)
            }
            "inline" -> {
                when (val cfg = campaign.config) {
                    is com.digia.engage.internal.model.CampaignConfigModel.Inline ->
                            displayCoordinator.routeInlineCarousel(cfg.inlineConfig, payload)
                    is com.digia.engage.internal.model.CampaignConfigModel.Story ->
                            displayCoordinator.routeInlineStory(cfg.storyConfig, payload)
                    else -> error("unexpected config for inline campaign '$campaignKey'")
                }
                // syncTemplate semantics: CEP considers an inline slot shown and
                // done the moment it is delivered. Digia's impression fires only
                // when the slot first renders (see reportSlotFirstRender).
                events.toCep(DigiaExperienceEvent.Impressed, payload)
                events.toCep(DigiaExperienceEvent.Dismissed, payload)
            }
            "survey" -> {
                if (!surveyOrchestrator.start(campaign, payload)) {
                    Logger.warning(
                            "Survey campaign skipped — another survey is already on screen: key=$campaignKey"
                    )
                }
            }
            else -> error("Unknown campaign_type: ${campaign.campaignType}")
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
        val prefs: SharedPreferences =
                context.getSharedPreferences("digia_prefs", Context.MODE_PRIVATE)
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
    internal fun setSdkStateForTest(state: SDKState) {
        _sdkState.value = state
    }

    @Suppress("unused")
    internal fun flushPendingPayloadForTest() {
        flushPendingPayloadIfAny()
    }

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
        events.clearImpressions()
        dwellTracker.clear()
        questionViewedAt.clear()
        completedSurveyToken = null
        welcomeStartToken = null
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }
}
