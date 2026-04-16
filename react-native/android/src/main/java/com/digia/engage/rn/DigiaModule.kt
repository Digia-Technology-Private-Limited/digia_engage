package com.digia.engage.rn

import com.digia.engage.DiagnosticReport
import com.digia.engage.Digia
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEnvironment
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.DigiaLogLevel
import com.digia.engage.InAppPayload
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

internal class DigiaModule(
        private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    private val rnPlugin = RNEventBridgePlugin(reactContext)

    override fun getName(): String = MODULE_NAME

    @ReactMethod
    fun initialize(apiKey: String, environment: String, logLevel: String, promise: Promise) {
        try {
            val config =
                    DigiaConfig(
                            apiKey = apiKey,
                            environment =
                                    when (environment.lowercase()) {
                                        "sandbox" -> DigiaEnvironment.SANDBOX
                                        else -> DigiaEnvironment.PRODUCTION
                                    },
                            logLevel =
                                    when (logLevel.lowercase()) {
                                        "verbose" -> DigiaLogLevel.VERBOSE
                                        "none" -> DigiaLogLevel.NONE
                                        else -> DigiaLogLevel.ERROR
                                    },
                    )
            Digia.initialize(reactContext.applicationContext, config)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("DIGIA_INIT_ERROR", e.message ?: "Initialisation failed", e)
        }
    }

    @ReactMethod
    fun registerBridge() {
        Digia.register(rnPlugin)
    }

    @ReactMethod
    fun setCurrentScreen(name: String) {
        Digia.setCurrentScreen(name)
    }

    @ReactMethod
    fun triggerCampaign(id: String, content: ReadableMap, cepContext: ReadableMap) {
        val delegate = rnPlugin.delegate ?: return
        delegate.onCampaignTriggered(
                InAppPayload(
                        id = id,
                        content = content.toHashMap().toMap(),
                        cepContext = cepContext.toHashMap().toMap(),
                )
        )
    }

    @ReactMethod
    fun invalidateCampaign(campaignId: String) {
        rnPlugin.delegate?.onCampaignInvalidated(campaignId)
    }

    companion object {
        const val MODULE_NAME = "DigiaEngageModule"
    }
}

internal class RNEventBridgePlugin(
        private val reactContext: ReactApplicationContext,
) : DigiaCEPPlugin {

    override val identifier: String = "rn-event-bridge"

    var delegate: DigiaCEPDelegate? = null
        private set

    private fun emit(event: String, params: com.facebook.react.bridge.WritableMap) {
        if (reactContext.hasActiveReactInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(event, params)
        }
    }

    override fun setup(delegate: DigiaCEPDelegate) {
        this.delegate = delegate
    }

    override fun forwardScreen(name: String) {}

    override fun notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload) {

        val params =
                Arguments.createMap().apply {
                    putString("id", payload.id)
                    when (event) {
                        is DigiaExperienceEvent.Impressed -> putString("type", "impressed")
                        is DigiaExperienceEvent.Clicked -> {
                            putString("type", "clicked")
                            event.elementId?.let { putString("elementId", it) }
                        }
                        is DigiaExperienceEvent.Dismissed -> putString("type", "dismissed")
                    }
                }
        emit("digiaEngageEvent", params)
    }

    override fun teardown() {
        delegate = null
    }

    override fun healthCheck(): DiagnosticReport =
            DiagnosticReport(isHealthy = true, metadata = mapOf("identifier" to identifier))
}
