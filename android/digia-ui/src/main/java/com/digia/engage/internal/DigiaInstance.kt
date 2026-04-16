package com.digia.engage.internal

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.bottomsheet.BottomSheetManager
import com.digia.digiaui.framework.dialog.DialogManager
import com.digia.digiaui.framework.logging.Logger
import com.digia.digiaui.init.DigiaUI
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.init.DigiaUIOptions
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaConfig
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

    private val _isRenderEngineReady = MutableStateFlow(false)
    val isRenderEngineReady: StateFlow<Boolean> = _isRenderEngineReady.asStateFlow()

    private val _sdkState = MutableStateFlow(SDKState.NOT_INITIALIZED)
    val sdkState: StateFlow<SDKState> = _sdkState.asStateFlow()

    private val initializationStarted = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private val lifecycleObserverAttached = AtomicBoolean(false)
    private val renderEngineInitialized = AtomicBoolean(false)

    private var lifecycleObserver: LifecycleEventObserver? = null
    private var hostMounted = false
    private var pendingPayload: InAppPayload? = null

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
            val options =
                    DigiaUIOptions(
                            context = context.applicationContext,
                            accessKey = config.apiKey,
                            environment =
                                    when (config.environment) {
                                        DigiaEnvironment.PRODUCTION ->
                                                com.digia.digiaui.init.Environment.Production
                                        DigiaEnvironment.SANDBOX ->
                                                com.digia.digiaui.init.Environment.Development
                                    },
                    )

            try {
                val digiaUI = DigiaUI.initialize(options)
                scope
                        .launch(Dispatchers.Main.immediate) {
                            DigiaUIManager.initialize(digiaUI)
                            _isUiReady.value = true
                            initialized.set(true)
                            _sdkState.value = SDKState.READY
                            registerLifecycleObserver()
                            pluginRegistry.runHealthCheck()
                            flushPendingPayloadIfAny()
                        }
                        .join()
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

    fun ensureRenderEngineInitialized() {
        if (!_isUiReady.value || !renderEngineInitialized.compareAndSet(false, true)) return
        val manager = DigiaUIManager.getInstance()
        manager.bottomSheetManager = manager.bottomSheetManager ?: BottomSheetManager()
        manager.dialogManager = manager.dialogManager ?: DialogManager()
        DUIFactory.getInstance().initialize()
        _isRenderEngineReady.value = true
    }

    fun onHostMounted() {
        hostMounted = true
    }

    fun onHostUnmounted() {
        hostMounted = false
    }

    fun reportOverlayImpression(payload: InAppPayload) {
        displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Impressed, payload)
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


    fun markOverlayDismissed(payloadId: String) {
        val current = controller.activePayload.value
        if (current?.id == payloadId) {
            displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Dismissed, current)
            controller.dismiss()
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
        hostMounted = false
        pluginRegistry.teardown()
        controller.dismiss()
        controller.clearSlots()
        screenTracker.clear()
        renderEngineInitialized.set(false)
        _isRenderEngineReady.value = false
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
        DigiaUIManager.getInstance().dialogManager?.clear()
        DigiaUIManager.getInstance().bottomSheetManager?.clear()
        DigiaUIManager.destroy()
        DUIFactory.getInstance().destroy()
    }

    override fun onCampaignTriggered(payload: InAppPayload) {
        scope.launch(Dispatchers.Main.immediate) {
            when (_sdkState.value) {
                SDKState.NOT_INITIALIZED, SDKState.FAILED, -> {
                    logWarning("campaign dropped before sdk ready: ${payload.id}")
                    return@launch
                }
                SDKState.INITIALIZING -> {
                    if (isNudgePayload(payload)) {
                        if (pendingPayload != null) {
                            logWarning("pending payload replaced by newer payload: ${payload.id}")
                        }
                        pendingPayload = payload
                    } else {
                        logWarning("inline payload dropped while initializing: ${payload.id}")
                    }
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

    @Suppress("UNCHECKED_CAST")
    private fun routeCampaign(payload: InAppPayload) {
        val type = (payload.content["type"] as? String)?.trim()?.lowercase()
        val command = (payload.content["command"] as? String)?.trim()?.uppercase()

        if (type.isNullOrBlank() && command.isNullOrBlank()) {
            logWarning("campaign dropped: neither 'type' nor 'command' is set: ${payload.id}")
            return
        }

        val viewId = (payload.content["viewId"] as? String)?.trim()
        if (viewId.isNullOrBlank()) {
            logWarning("campaign dropped: 'viewId' is required: ${payload.id}")
            return
        }

        if (type == "inline") {
            val placementKey = (payload.content["placementKey"] as? String)?.trim()
            if (placementKey.isNullOrBlank()) {
                logWarning(
                        "inline payload dropped: 'placementKey' is required when 'type' is set: ${payload.id}"
                )
                return
            }
            displayCoordinator.routeInline(placementKey, payload)
            reportSlotImpression(payload)
            return
        }

        when (command) {
            "SHOW_DIALOG", "SHOW_BOTTOM_SHEET" -> {
                if (!hostMounted) {
                    logWarning("nudge campaign arrived before DigiaHost mounted: ${payload.id}")
                }
                displayCoordinator.routeNudge(payload)
            }
            else ->
                    logWarning(
                            "campaign dropped due to unsupported command '$command': ${payload.id}"
                    )
        }
    }

    private fun isNudgePayload(payload: InAppPayload): Boolean {
        val type = (payload.content["type"] as? String)?.trim()?.lowercase()
        if (type == "inline") return false
        val command = (payload.content["command"] as? String)?.trim()?.uppercase()
        return command == "SHOW_DIALOG" || command == "SHOW_BOTTOM_SHEET"
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
        Logger.warning(message, tag = "Digia")
    }

    @Suppress("unused")
    internal fun initForTest() {
        initializationStarted.set(true)
        initialized.set(true)
        _isUiReady.value = true
        _sdkState.value = SDKState.READY
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
        hostMounted = false
        pluginRegistry.teardown()
        screenTracker.clear()
        controller.dismiss()
        controller.clearSlots()
        renderEngineInitialized.set(false)
        _isRenderEngineReady.value = false
        _isUiReady.value = false
        _sdkState.value = SDKState.NOT_INITIALIZED
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }
}
