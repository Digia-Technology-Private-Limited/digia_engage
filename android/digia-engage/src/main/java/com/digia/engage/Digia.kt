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

    /**
     * Test helper: triggers a fetched campaign directly by Digia campaign id.
     *
     * This bypasses the configured CEP plugin trigger. The SDK must already be
     * initialized and the campaign must be present in the fetched campaign list.
     */
    fun triggerCampaign(campaignId: String) {
        DigiaInstance.triggerCampaign(campaignId)
    }
}
