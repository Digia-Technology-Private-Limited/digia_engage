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

    fun captureAnalyticsEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        android.util.Log.d("DigiaAnalytics", "[Digia] captureAnalyticsEvent: event=${event::class.simpleName} payload.id=${payload.id} content=${payload.content}")
        DigiaInstance.captureAnalyticsEvent(event, payload)
    }
}
