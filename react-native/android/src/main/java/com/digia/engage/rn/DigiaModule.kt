/**
 * DigiaModule
 *
 * React Native NativeModule that bridges the Digia Engage Android SDK.
 *
 * Exposed methods (callable from JS via NativeModules.DigiaEngageModule):
 *
 * initialize(apiKey, environment, logLevel): Promise<void>
 * ```
 *     Initialises the SDK and auto-mounts the Compose overlay host on the
 *     current Activity's content view.  Call this once at app startup.
 * ```
 * setCurrentScreen(name): void
 * ```
 *     Forwards the currently-visible screen name to any registered CEP plugins
 *     so they can trigger experience campaigns based on navigation context.
 * ```
 * openNavigation(startPageId, pageArgs): void
 * ```
 *     Launches DigiaUINavigationActivity – a full-screen Compose navigation
 *     flow defined by your Digia DSL configuration.
 * ```
 */
package com.digia.engage.rn

import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.digia.digiaui.framework.navigation.DigiaUINavigationActivity
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
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.modules.core.DeviceEventManagerModule

internal class DigiaModule(
        private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = MODULE_NAME

    // ─── initialize ───────────────────────────────────────────────────────────

    /**
     * Initialise the Digia Engage SDK and mount the Compose overlay host.
     *
     * @param apiKey Your Digia project API key.
     * @param environment "production" | "sandbox"
     * @param logLevel "none" | "error" | "verbose"
     */
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

            // Register the RN event bridge plugin so JS plugins get lifecycle callbacks.
            Digia.register(RNEventBridgePlugin(reactContext))

            // Auto-mount the Compose host on the UI thread after initialisation
            // so dialogs / bottom-sheets can overlay React Native content.
            UiThreadUtil.runOnUiThread {
                mountDigiaHost()
                promise.resolve(null)
            }
        } catch (e: Exception) {
            promise.reject("DIGIA_INIT_ERROR", e.message ?: "Initialisation failed", e)
        }
    }

    // ─── setCurrentScreen ─────────────────────────────────────────────────────

    /**
     * Notify the SDK of the currently active screen. Wire this to your React Navigation focus
     * listener or equivalent.
     */
    @ReactMethod
    fun setCurrentScreen(name: String) {
        Digia.setCurrentScreen(name)
    }

    // ─── openNavigation ───────────────────────────────────────────────────────

    /**
     * Launch the Digia UI full-screen navigation activity.
     *
     * @param startPageId The DSL page ID to start from (null = SDK default).
     * @param pageArgs Key/value string arguments forwarded to the start page.
     */
    @ReactMethod
    fun openNavigation(startPageId: String?, pageArgs: ReadableMap?) {
        val activity = currentActivity ?: run { reactContext.currentActivity ?: return }

        val argsMap: Map<String, Any?>? =
                pageArgs?.toHashMap()?.entries?.associate { (k, v) -> k to v }?.takeIf {
                    it.isNotEmpty()
                }

        val intent =
                DigiaUINavigationActivity.createIntent(
                        context = activity,
                        startPageId = startPageId,
                        pageArgs = argsMap,
                )
        activity.startActivity(intent)
    }

    // ─── triggerCampaign ──────────────────────────────────────────────────────

    /**
     * Push a campaign payload into Digia's rendering engine from JavaScript.
     *
     * Called by the pure-TS MoEngage (or any other CEP) plugin when it receives a self-handled
     * in-app campaign from its own SDK.
     *
     * @param id Unique campaign ID (opaque string from the CEP platform).
     * @param content Marketer-authored payload map (JSON-serialisable).
     * @param cepContext CEP-platform metadata (e.g. { campaignId, campaignName }).
     */
    @ReactMethod
    fun triggerCampaign(id: String, content: ReadableMap, cepContext: ReadableMap) {
        val contentMap: Map<String, Any?> = content.toHashMap().toMap()
        val cepContextMap: Map<String, Any?> = cepContext.toHashMap().toMap()
        val payload =
                com.digia.engage.InAppPayload(
                        id = id,
                        content = contentMap,
                        cepContext = cepContextMap,
                )
        // DigiaCEPDelegate.onCampaignTriggered routes through DisplayCoordinator
        // → DigiaOverlayController.show() → DigiaHost composable renders the overlay.
        com.digia.engage.internal.DigiaInstance.onCampaignTriggered(payload)
    }

    // ─── invalidateCampaign ───────────────────────────────────────────────────

    /**
     * Dismiss / invalidate an active campaign by its ID. Mirrors
     * DigiaCEPDelegate.onCampaignInvalidated().
     */
    @ReactMethod
    fun invalidateCampaign(campaignId: String) {
        com.digia.engage.internal.DigiaInstance.onCampaignInvalidated(campaignId)
    }

    // ─── Internal: mount the Compose overlay host ─────────────────────────────

    /**
     * Adds a [DigiaHostComposeView] to the Activity's content FrameLayout.
     *
     * The view is full-screen and transparent. All touch events pass through it to React Native
     * unless a Compose Dialog / BottomSheet is visible (those create their own Android windows and
     * handle their own touch).
     *
     * Safe to call multiple times – a guard tag prevents duplicate mounting.
     */
    private fun mountDigiaHost() {
        val activity = currentActivity ?: return

        // Guard: don't mount twice
        val contentRoot =
                activity.window.decorView.findViewWithTag<DigiaHostComposeView>(DIGIA_HOST_TAG)
        if (contentRoot != null) return

        val composeView =
                DigiaHostComposeView(activity).apply {
                    tag = DIGIA_HOST_TAG

                    // Wire Architecture Component owners so the Compose runtime works
                    if (activity is LifecycleOwner) setViewTreeLifecycleOwner(activity)
                    if (activity is ViewModelStoreOwner) setViewTreeViewModelStoreOwner(activity)
                    if (activity is SavedStateRegistryOwner)
                            setViewTreeSavedStateRegistryOwner(activity)
                }

        // Add to the Activity's content area (id/content FrameLayout).
        // addContentView appends as the last child – i.e. on top of React Native views.
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
 * A minimal DigiaCEPPlugin registered automatically during `initialize()`. Its sole job is to
 * re-emit Digia overlay lifecycle events (impressed, clicked, dismissed, invalidated) as React
 * Native DeviceEventEmitter events so that pure-JS CEP plugin implementations (e.g.
 * DigiaMoEngagePlugin) can receive lifecycle callbacks without needing native code.
 *
 * JS listeners: import { DeviceEventEmitter } from 'react-native';
 * DeviceEventEmitter.addListener('digiaOverlayEvent', ({ type, campaignId, elementId }) => { });
 * DeviceEventEmitter.addListener('digiaCampaignInvalidated', ({ campaignId }) => { });
 */
internal class RNEventBridgePlugin(
        private val reactContext: ReactApplicationContext,
) : DigiaCEPPlugin {

    override val identifier: String = "rn-event-bridge"

    private fun emit(event: String, params: com.facebook.react.bridge.WritableMap) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(event, params)
        }
    }

    override fun setup(delegate: DigiaCEPDelegate) {
        /* no-op */
    }

    override fun forwardScreen(name: String) {
        /* no-op */
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
        /* no-op */
    }

    override fun healthCheck(): DiagnosticReport =
            DiagnosticReport(isHealthy = true, metadata = mapOf("identifier" to identifier))
}
