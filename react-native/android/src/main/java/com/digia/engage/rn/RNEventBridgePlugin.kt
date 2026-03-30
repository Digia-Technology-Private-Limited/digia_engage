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
package com.digia.engage.rn

import com.digia.engage.DiagnosticReport
import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule

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
