/**
 * DigiaModuleImpl
 *
 * Shared business logic for the Digia Engage native module, used by both Old Architecture
 * (ReactContextBaseJavaModule) and New Architecture (NativeDigiaEngageSpec / TurboModule) wrappers.
 *
 * This class is NOT a React Native module itself — it is instantiated by the arch-specific
 * DigiaModule wrapper in src/oldarch/ or src/newarch/.
 */
package com.digia.engage.rn

import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.digia.engage.Digia
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaEnvironment
import com.digia.engage.DigiaHostView
import com.digia.engage.DigiaLogLevel
import com.digia.engage.InAppPayload
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil

internal class DigiaModuleImpl(
        private val reactContext: ReactApplicationContext,
) {

    val rnPlugin = RNEventBridgePlugin(reactContext)

    // ─── initialize ───────────────────────────────────────────────────────────

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

    // ─── registerBridge ───────────────────────────────────────────────────────

    fun registerBridge() {
        Digia.register(rnPlugin)
    }

    // ─── setCurrentScreen ─────────────────────────────────────────────────────

    fun setCurrentScreen(name: String) {
        Digia.setCurrentScreen(name)
    }

    // ─── triggerCampaign ──────────────────────────────────────────────────────

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
