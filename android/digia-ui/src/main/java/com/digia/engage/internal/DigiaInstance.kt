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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object DigiaInstance : DigiaCEPDelegate {

    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    val controller = DigiaOverlayController()

    private val _isUiReady = MutableStateFlow(false)
    val isUiReady: StateFlow<Boolean> = _isUiReady.asStateFlow()

    private val _isRenderEngineReady = MutableStateFlow(false)
    val isRenderEngineReady: StateFlow<Boolean> = _isRenderEngineReady.asStateFlow()

    private val activePlugin = AtomicReference<DigiaCEPPlugin?>(null)
    private val pendingPlugin = AtomicReference<DigiaCEPPlugin?>(null)
    private val currentScreen = AtomicReference<String?>(null)
    private val initialized = AtomicBoolean(false)
    private val initializationStarted = AtomicBoolean(false)

    private val lifecycleObserverAttached = AtomicBoolean(false)
    private var lifecycleObserver: LifecycleEventObserver? = null
    private val renderEngineInitialized = AtomicBoolean(false)

    fun initialize(context: Context, config: DigiaConfig) {
        if (!initializationStarted.compareAndSet(false, true)) return

        controller.onEvent = { event, payload ->
            activePlugin.get()?.notifyEvent(event, payload)
        }

        scope.launch(Dispatchers.IO) {
            val options = DigiaUIOptions(
                context = context.applicationContext,
                accessKey = config.apiKey,
                environment = when (config.environment) {
                    DigiaEnvironment.PRODUCTION -> com.digia.digiaui.init.Environment.Production
                    DigiaEnvironment.SANDBOX -> com.digia.digiaui.init.Environment.Development
                },
            )

            try {
                val digiaUI = DigiaUI.initialize(options)
                scope.launch(Dispatchers.Main.immediate) {
                    DigiaUIManager.initialize(digiaUI)
                    _isUiReady.value = true
                    registerLifecycleObserver()
                    initialized.set(true)
                    applyPendingPluginIfAny()
                }.join()
            } catch (t: Throwable) {
                initializationStarted.set(false)
                scope.launch(Dispatchers.Main.immediate) {
                    _isUiReady.value = false
                }
                logWarning("Digia.initialize failed: ${t.message}")
            }
        }
    }

    fun register(plugin: DigiaCEPPlugin) {
        if (!initialized.get()) {
            pendingPlugin.getAndSet(plugin)?.teardown()
            return
        }
        applyPlugin(plugin)
    }

    private fun applyPlugin(plugin: DigiaCEPPlugin) {
        activePlugin.getAndSet(plugin)?.teardown()
        pendingPlugin.set(null)
        plugin.setup(this)
        currentScreen.get()?.let { s -> plugin.forwardScreenEvent(s) }
    }

    private fun applyPendingPluginIfAny() {
        pendingPlugin.getAndSet(null)?.let { applyPlugin(it) }
    }

    fun setCurrentScreen(name: String) {
        currentScreen.set(name)
        activePlugin.get()?.forwardScreenEvent(name)
            ?: logWarning("Digia.register(plugin) has not been called. Screen '$name' is dropped.")
    }

    fun ensureRenderEngineInitialized() {
        if (!_isUiReady.value || !renderEngineInitialized.compareAndSet(false, true)) return
        val manager = DigiaUIManager.getInstance()
        manager.bottomSheetManager = manager.bottomSheetManager ?: BottomSheetManager()
        manager.dialogManager = manager.dialogManager ?: DialogManager()
        DUIFactory.getInstance().initialize()
        _isRenderEngineReady.value = true
    }

    fun markDismissed(payloadId: String) {
        val current = controller.activePayload.value
        if (current?.id == payloadId) {
            activePlugin.get()?.notifyEvent(DigiaExperienceEvent.Dismissed, current)
            controller.dismiss()
        }
    }

    fun emitExplicitCtaClick(elementId: String?) {
        val payload = controller.activePayload.value ?: return
        activePlugin.get()?.notifyEvent(DigiaExperienceEvent.Clicked(elementId), payload)
    }

    fun teardown() {
        supervisorJob.cancel()
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)
        initializationStarted.set(false)
        pendingPlugin.getAndSet(null)?.teardown()
        activePlugin.getAndSet(null)?.teardown()
        controller.dismiss()
        controller.clearSlots()
        currentScreen.set(null)
        initialized.set(false)
        renderEngineInitialized.set(false)
        _isRenderEngineReady.value = false
        _isUiReady.value = false
        lifecycleObserver?.let {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
        }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
        DigiaUIManager.getInstance().dialogManager?.clear()
        DigiaUIManager.getInstance().bottomSheetManager?.clear()
        DigiaUIManager.destroy()
        DUIFactory.getInstance().destroy()
    }

    private fun registerLifecycleObserver() {
        if (!lifecycleObserverAttached.compareAndSet(false, true)) return
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                teardown()
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

    override fun onExperienceReady(payload: InAppPayload) {
        scope.launch(Dispatchers.Main.immediate) {
            if (activePlugin.get() == null) {
                logWarning("Digia.register(plugin) has not been called. Experience '${payload.id}' is dropped.")
                return@launch
            }

            val type = (payload.content["type"] as? String)?.lowercase() ?: "dialog"
            val componentId = payload.content["componentId"] as? String

            when (type) {
                "slot" -> {
                    val placementKey = payload.content["placementKey"] as? String
                    if (placementKey.isNullOrBlank() || componentId == null) {
                        logWarning("Experience '${payload.id}' has invalid slot content (placementKey/componentId) and was dropped.")
                        return@launch
                    }
                    controller.addSlot(placementKey, payload)
                }
                "dialog", "bottom_sheet" -> {
                    if (componentId == null) {
                        logWarning("Experience '${payload.id}' has invalid content (componentId) and was dropped.")
                        return@launch
                    }
                    controller.show(payload)
                    activePlugin.get()?.notifyEvent(DigiaExperienceEvent.Impressed, payload)
                }
                else -> logWarning("Unknown experience type '$type' for '${payload.id}', dropped.")
            }
        }
    }

    override fun onExperienceInvalidated(payloadId: String) {
        scope.launch(Dispatchers.Main.immediate) {
            if (controller.activePayload.value?.id == payloadId) {
                controller.dismiss()
            }
            controller.removeSlotById(payloadId)
        }
    }

    private fun logWarning(message: String) {
        Logger.warning(message, tag = "Digia")
    }

    @Suppress("unused")
    internal fun initForTest() {
        initialized.set(true)
        initializationStarted.set(true)
        controller.onEvent = { event, payload ->
            activePlugin.get()?.notifyEvent(event, payload)
        }
    }

    @Suppress("unused")
    internal fun resetForTest() {
        supervisorJob.cancel()
        supervisorJob = SupervisorJob()
        scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)
        initializationStarted.set(false)
        pendingPlugin.getAndSet(null)?.teardown()
        activePlugin.getAndSet(null)?.teardown()
        currentScreen.set(null)
        initialized.set(false)
        controller.dismiss()
        controller.clearSlots()
        renderEngineInitialized.set(false)
        _isRenderEngineReady.value = false
        _isUiReady.value = false
        lifecycleObserver?.let {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
        }
        lifecycleObserver = null
        lifecycleObserverAttached.set(false)
    }
}
