package com.digia.engage

import android.content.Context
import com.digia.engage.internal.DigiaInstance

object Digia {
    fun initialize(context: Context, config: DigiaConfig) {
        DigiaInstance.initialize(context = context, config = config)
    }

    fun register(plugin: DigiaCEPPlugin) {
        DigiaInstance.register(plugin)
    }

    fun setCurrentScreen(name: String) {
        DigiaInstance.setCurrentScreen(name)
    }

    fun registerAnchor(key: String, x: Int, y: Int, width: Int, height: Int) {
        DigiaInstance.registerAnchor(key, x, y, width, height)
    }

    fun registerAnchorView(key: String, view: android.view.View) {
        DigiaInstance.registerAnchorView(key, view)
    }

    fun unregisterAnchor(key: String) {
        DigiaInstance.unregisterAnchor(key)
    }

    fun setUserId(userId: String) {
        DigiaInstance.setUserId(userId)
    }

    fun clearUserId() {
        DigiaInstance.clearUserId()
    }

    /**
     * Register the RN render hook. When set, the SDK treats guides as JS-rendered:
     * on a guide trigger native applies frequency capping and, if allowed, invokes
     * this callback (with the trigger payload) to ask JS to render — it does not
     * render the guide natively. Used only by the React Native bridge.
     */
    fun setOnGuideRenderRequest(callback: ((CEPTriggerPayload) -> Unit)?) {
        DigiaInstance.onGuideRenderRequest = callback
    }

    /**
     * Records an analytics event for a JS-rendered campaign (guide). [eventName] is
     * the Engage matrix event name (e.g. "Digia Step Viewed") and [props] carries
     * its wire-keyed fields (step_index, anchor_key, cta_label, …). The SDK maps it
     * to the typed analytics event and records it.
     */
    fun captureAnalyticsEvent(campaignKey: String, eventName: String, props: Map<String, Any?>) {
        android.util.Log.d("DigiaAnalytics", "[Digia] captureAnalyticsEvent: name='$eventName' campaignKey=$campaignKey props=$props")
        DigiaInstance.captureAnalyticsEvent(campaignKey, eventName, props)
    }
}
