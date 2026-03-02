package com.digia.engage.internal

import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload

internal class PluginRegistry(
    private val delegate: DigiaCEPDelegate,
    private val diagnosticsReporter: DiagnosticsReporter,
) {
    private var activePlugin: DigiaCEPPlugin? = null

    fun register(plugin: DigiaCEPPlugin) {
        activePlugin?.teardown()
        activePlugin = plugin
        plugin.setup(delegate)
        diagnosticsReporter.report(plugin.healthCheck(), plugin.identifier)
    }

    fun forwardScreen(name: String) {
        activePlugin?.forwardScreen(name)
    }

    fun notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        activePlugin?.notifyEvent(event, payload)
    }

    fun runHealthCheck() {
        activePlugin?.let { diagnosticsReporter.report(it.healthCheck(), it.identifier) }
    }

    fun hasActivePlugin(): Boolean = activePlugin != null

    fun teardown() {
        activePlugin?.teardown()
        activePlugin = null
    }
}
