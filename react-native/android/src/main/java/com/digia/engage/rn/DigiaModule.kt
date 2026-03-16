/**
 * DigiaModule
 *
 * React Native NativeModule that bridges the Digia Engage Android SDK.
 *
 * Exposed methods (callable from JS via NativeModules.DigiaEngageModule):
 *
 * initialize(apiKey, environment, logLevel): Promise<void> register(): void setCurrentScreen(name):
 * void triggerCampaign(id, content, cepContext): void invalidateCampaign(campaignId): void
 *
 * Architecture ──────────── The RN bridge mirrors the native Digia.initialize / Digia.register /
 * Digia.setCurrentScreen flow exactly. An internal [RNEventBridgePlugin] is the single native
 * DigiaCEPPlugin registered via Digia.register().
 *
 * When the SDK calls plugin.setup(delegate), the bridge stores that delegate reference. JS plugins
 * that need to push campaigns into the Compose overlay call triggerCampaign / invalidateCampaign
 * which forward to delegate.onCampaignTriggered / delegate.onCampaignInvalidated.
 *
 * Overlay lifecycle events (impressed / clicked / dismissed) are forwarded from the native
 * plugin.notifyEvent() to JS via DeviceEventEmitter so that pure-JS CEP plugins (e.g.
 * DigiaMoEngagePlugin) can report analytics.
 */
package com.digia.engage.rn

import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.digia.engage.DiagnosticReport
import com.digia.engage.Digia
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEnvironment
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.DigiaHostView
import com.digia.engage.DigiaLogLevel
import com.digia.engage.InAppPayload
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.modules.core.DeviceEventManagerModule

internal class DigiaModule(
        private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    private val rnPlugin = RNEventBridgePlugin(reactContext)

    override fun getName(): String = MODULE_NAME

    // ─── initialize ───────────────────────────────────────────────────────────

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

            UiThreadUtil.runOnUiThread {
                mountDigiaHost()
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("DIGIA_INIT_ERROR", e.message ?: "Initialisation failed", e)
        }
    }

    // ─── register ─────────────────────────────────────────────────────────────

    /**
     * Registers the internal [RNEventBridgePlugin] with the native Digia SDK.
     *
     * The host app must call this from JS after initialize() resolves. This mirrors
     * `Digia.register(plugin)` on native — the bridge plugin is the single native DigiaCEPPlugin
     * that: • receives the DigiaCEPDelegate via setup(delegate) • forwards overlay lifecycle events
     * to JS via DeviceEventEmitter
     */
    @ReactMethod
    fun register() {
        Digia.register(rnPlugin)
    }

    // ─── setCurrentScreen ─────────────────────────────────────────────────────

    @ReactMethod
    fun setCurrentScreen(name: String) {
        Digia.setCurrentScreen(name)
    }

    // ─── triggerCampaign ──────────────────────────────────────────────────────

    /**
     * Forwards a campaign payload to the native DigiaCEPDelegate.
     *
     * This is called by the JS DigiaDelegate.onCampaignTriggered() implementation when a JS CEP
     * plugin (e.g. DigiaMoEngagePlugin) delivers a campaign. The delegate routes it into the
     * Compose overlay for rendering.
     */
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

    // ─── invalidateCampaign ───────────────────────────────────────────────────

    /** Forwards a campaign invalidation to the native DigiaCEPDelegate. */
    @ReactMethod
    fun invalidateCampaign(campaignId: String) {
        rnPlugin.delegate?.onCampaignInvalidated(campaignId)
    }

    // ─── Internal: mount the Compose overlay host ─────────────────────────────

    private fun mountDigiaHost() {
        val activity = reactContext.currentActivity ?: return

        val contentRoot = activity.window.decorView.findViewWithTag<DigiaHostView>(DIGIA_HOST_TAG)
        if (contentRoot != null) return

        val composeView =
                DigiaHostView(activity).apply {
                    tag = DIGIA_HOST_TAG
                    if (activity is LifecycleOwner) setViewTreeLifecycleOwner(activity)
                    if (activity is ViewModelStoreOwner) setViewTreeViewModelStoreOwner(activity)
                    if (activity is SavedStateRegistryOwner)
                            setViewTreeSavedStateRegistryOwner(activity)
                }

        activity.addContentView(
                composeView,
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                ),
        )
    }

    companion object {
        const val MODULE_NAME = "DigiaEngageModule"
        private const val DIGIA_HOST_TAG = "digia_host_compose_view"
    }
}

/**
 * RNEventBridgePlugin
 *
 * The single native DigiaCEPPlugin used in React Native.
 *
 * When Digia.register(rnPlugin) is called, the SDK calls setup(delegate) which gives us the
 * DigiaCEPDelegate reference. This delegate is used by triggerCampaign / invalidateCampaign bridge
 * methods to push campaigns into the native rendering engine.
 *
 * Overlay lifecycle events (impressed / clicked / dismissed) received via notifyEvent() are emitted
 * to JS as DeviceEventEmitter events so that JS CEP plugins can report analytics back to their
 * platform.
 */
internal class RNEventBridgePlugin(
        private val reactContext: ReactApplicationContext,
) : DigiaCEPPlugin {

    override val identifier: String = "rn-event-bridge"

    /** Delegate received from the SDK via setup(). Used by DigiaModule bridge methods. */
    var delegate: DigiaCEPDelegate? = null
        private set

    private fun emit(event: String, params: com.facebook.react.bridge.WritableMap) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(event, params)
        }
    }

    override fun setup(delegate: DigiaCEPDelegate) {
        this.delegate = delegate
    }

    override fun forwardScreen(name: String) {
        /* forwarded by Digia.setCurrentScreen() on the native side */
    }

    override fun notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        val params =
                Arguments.createMap().apply {
                    putString("campaignId", payload.id)
                    when (event) {
                        is DigiaExperienceEvent.Impressed -> putString("type", "impressed")
                        is DigiaExperienceEvent.Clicked -> {
                            putString("type", "clicked")
                            event.elementId?.let { putString("elementId", it) }
                        }
                        is DigiaExperienceEvent.Dismissed -> putString("type", "dismissed")
                    }
                }
        emit("digiaOverlayEvent", params)
    }

    override fun teardown() {
        delegate = null
    }

    override fun healthCheck(): DiagnosticReport =
            DiagnosticReport(isHealthy = true, metadata = mapOf("identifier" to identifier))
}
