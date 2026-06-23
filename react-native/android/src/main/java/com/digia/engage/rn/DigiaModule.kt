/**
 * DigiaModule
 *
 * React Native NativeModule that bridges the Digia Engage Android SDK.
 *
 * Exposed methods (callable from JS via NativeModules.DigiaEngageModule):
 *
 * initialize(apiKey, environment, logLevel, baseUrl): Promise<void> register(): void
 * setCurrentScreen(name): void triggerCampaign(cepCampaignId, campaignKey, variables, cepMetadata): void
 * invalidateCampaign(campaignId): void
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

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.digia.engage.CEPTriggerPayload
import com.digia.engage.DiagnosticReport
import com.digia.engage.Digia
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEnvironment
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.DigiaHostView
import com.digia.engage.DigiaLogLevel
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
    fun initialize(
            apiKey: String,
            environment: String,
            logLevel: String,
            baseUrl: String?,
            fontFamily: String?,
            sdkVersion: String?,
            promise: Promise
    ) {
        try {
            val cleanBaseUrl =
                    baseUrl?.trim()?.trimEnd('/')?.removeSuffix("/api/v1")?.takeIf { it.isNotBlank() }
            val config =
                    DigiaConfig(
                            apiKey = apiKey,
                            baseUrl = cleanBaseUrl,
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
                            fontFamily = fontFamily?.takeIf { it.isNotBlank() },
                            // This native engine is being driven by the RN wrapper.
                            wrapperBinding = "react_native",
                            wrapperVersion = sdkVersion?.takeIf { it.isNotBlank() },
                    )
            Digia.initialize(reactContext.applicationContext, config)

            UiThreadUtil.runOnUiThread {
                // Mount the Compose overlay ABOVE the ReactRootView via addContentView().
                // This keeps it outside Fabric's shadow tree entirely so Fabric hit-testing
                // never sees it. Touch pass-through is handled by DigiaHostView.dispatchTouchEvent
                // returning false at the native Android level — which works correctly at this
                // level of the hierarchy, unlike inside Fabric where pointerEvents="none" on
                // non-ReactViewGroup views is not respected during shadow-tree hit-testing.
                mountDigiaHost()
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("DIGIA_INIT_ERROR", e.message ?: "Initialisation failed", e)
        }
    }

    // ─── register ─────────────────────────────────────────────────────────────

    /**
     * Registers [RNEventBridgePlugin] with the native Digia SDK.
     *
     * This is bridge infrastructure — not a user-facing CEP plugin. Called once by the JS
     * `Digia.register()` wrapper on the first plugin registration so that
     * [RNEventBridgePlugin.delegate] is populated before any triggerCampaign / invalidateCampaign
     * calls arrive from JS.
     */
    @ReactMethod
    fun registerBridge() {
        Digia.register(rnPlugin)
    }

    // ─── setCurrentScreen ─────────────────────────────────────────────────────

    @ReactMethod
    fun setCurrentScreen(name: String) {
        Digia.setCurrentScreen(name)
    }

    // ─── Analytics identity ───────────────────────────────────────────────────

    @ReactMethod
    fun setUserId(userId: String) {
        Digia.setUserId(userId)
    }

    @ReactMethod
    fun clearUserId() {
        Digia.clearUserId()
    }

    // ─── Analytics event forwarding (guide / JS-rendered campaigns) ───────────

    /**
     * Records an analytics event from a JS-rendered campaign (guide). [eventName] is
     * the Engage matrix event name; [props] carries the wire-keyed fields. The native
     * SDK maps it to the typed analytics event and records it. Native campaigns
     * (nudge, inline, survey) are tracked internally by the SDK.
     */
    @ReactMethod
    fun captureAnalyticsEvent(campaignKey: String, eventName: String, props: ReadableMap) {
        android.util.Log.d("DigiaAnalytics", "[captureAnalyticsEvent] name='$eventName' campaignKey=$campaignKey")
        Digia.captureAnalyticsEvent(campaignKey, eventName, props.toHashMap().toMap())
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
    fun triggerCampaign(
        cepCampaignId: String,
        campaignKey: String,
        variables: ReadableMap,
        cepMetadata: ReadableMap,
    ) {
        val delegate = rnPlugin.delegate ?: return
        val vars = variables.toHashMap()
            .entries
            .associate { it.key to it.value?.toString().orEmpty() }
            .takeIf { it.isNotEmpty() }
        delegate.onCampaignTriggered(
                CEPTriggerPayload(
                        cepCampaignId = cepCampaignId,
                        campaignKey = campaignKey,
                        cepMetadata = cepMetadata.toHashMap().toMap(),
                        variables = vars,
                )
        )
    }

    // ─── invalidateCampaign ───────────────────────────────────────────────────

    /** Forwards a campaign invalidation to the native DigiaCEPDelegate. */
    @ReactMethod
    fun invalidateCampaign(campaignId: String) {
        rnPlugin.delegate?.onCampaignInvalidated(campaignId)
    }

    // ─── Anchor registration ──────────────────────────────────────────────────

    @ReactMethod
    fun registerAnchor(key: String, x: Int, y: Int, width: Int, height: Int) {
        Digia.registerAnchor(key, x, y, width, height)
    }

    @ReactMethod
    fun unregisterAnchor(key: String) {
        Digia.unregisterAnchor(key)
    }

    @ReactMethod
    fun getRegisteredComponents(promise: Promise) {
        promise.resolve(Arguments.createArray())
    }

    // ─── Internal: mount the Compose overlay host ─────────────────────────────

    private fun mountDigiaHost() {
        val activity =
                reactContext.currentActivity
                        ?: run {
                            android.util.Log.w(
                                    "DigiaHost",
                                    "[mountDigiaHost] no current activity — skipping"
                            )
                            return
                        }

        val contentRoot = activity.window.decorView.findViewWithTag<DigiaHostView>(DIGIA_HOST_TAG)
        if (contentRoot != null) {
            android.util.Log.d(
                    "DigiaHost",
                    "[mountDigiaHost] already mounted (tag found) — skipping"
            )
            return
        }

        android.util.Log.d("DigiaHost", "[mountDigiaHost] mounting native overlay on DecorView")

        // Mount on the DecorView (not content area) so the overlay covers the full screen
        // including status bar and navigation bar. This also ensures the Compose Popup's
        // canvas y=0 aligns with the absolute screen y=0, matching getLocationOnScreen()
        // coordinates used by DigiaAnchorView.
        val decorView =
                activity.window.decorView as? android.view.ViewGroup
                        ?: run {
                            android.util.Log.w(
                                    "DigiaHost",
                                    "[mountDigiaHost] decorView not a ViewGroup — skipping"
                            )
                            return
                        }

        val composeView =
                DigiaHostView(activity).apply {
                    tag = DIGIA_HOST_TAG
                    if (activity is LifecycleOwner) setViewTreeLifecycleOwner(activity)
                    if (activity is ViewModelStoreOwner) setViewTreeViewModelStoreOwner(activity)
                    if (activity is SavedStateRegistryOwner)
                            setViewTreeSavedStateRegistryOwner(activity)
                }

        decorView.addView(
                composeView,
                android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
        )
        android.util.Log.d(
                "DigiaHost",
                "[mountDigiaHost] done — DecorView child count: ${decorView.childCount}"
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
        if (reactContext.hasActiveReactInstance()) {
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

    override fun notifyEvent(event: DigiaExperienceEvent, payload: CEPTriggerPayload) {
        val params =
                Arguments.createMap().apply {
                    putString("campaignId", payload.cepCampaignId)
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

    override fun notifyAction(actionType: String, url: String, payload: CEPTriggerPayload): Boolean {
        val params =
                Arguments.createMap().apply {
                    putString("campaignId", payload.cepCampaignId)
                    putString("type", "action")
                    putString("actionType", actionType)
                    putString("url", url)
                }
        emit("digiaEngageEvent", params)
        return true
    }

    override fun teardown() {
        delegate = null
    }

    override fun healthCheck(): DiagnosticReport =
            DiagnosticReport(isHealthy = true, metadata = mapOf("identifier" to identifier))
}
