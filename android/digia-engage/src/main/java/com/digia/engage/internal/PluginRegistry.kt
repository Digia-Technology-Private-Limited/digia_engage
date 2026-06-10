package com.digia.engage.internal

import com.digia.engage.DigiaCEPDelegate
import com.digia.engage.DigiaCEPPlugin
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.framework.logging.Logger

internal class PluginRegistry(
    private val delegate: DigiaCEPDelegate,
    private val diagnosticsReporter: DiagnosticsReporter,
) {
    private var activePlugin: DigiaCEPPlugin? = null

    fun register(plugin: DigiaCEPPlugin) {
        activePlugin?.let {
            Logger.verbose("Replacing existing plugin: ${it.identifier}")
            it.teardown()
        }
        activePlugin = plugin
        try {
            plugin.setup(delegate)
            Logger.verbose("Plugin setup complete: ${plugin.identifier}")
        } catch (t: Throwable) {
            Logger.error("Plugin setup threw an exception: ${plugin.identifier} — ${t.message}")
        }
        diagnosticsReporter.report(plugin.healthCheck(), plugin.identifier)
    }

    fun forwardScreen(name: String) {
        activePlugin?.forwardScreen(name)
    }

    fun notifyEvent(event: DigiaExperienceEvent, payload: InAppPayload) {
        if (activePlugin == null) {
            Logger.warning("Experience event fired but no plugin is registered — call Digia.register() with a CEP plugin: event=${event::class.simpleName} id=${payload.id}")
        }
        activePlugin?.notifyEvent(event, payload)
    }

    fun runHealthCheck() {
        if (activePlugin == null) {
            Logger.warning("No CEP plugin registered — campaigns will trigger but events won't be forwarded to any plugin")
        }
        activePlugin?.let { diagnosticsReporter.report(it.healthCheck(), it.identifier) }
    }

    fun hasActivePlugin(): Boolean = activePlugin != null

    fun teardown() {
        activePlugin?.let {
            Logger.verbose("Plugin teardown: ${it.identifier}")
            it.teardown()
        }
        activePlugin = null
    }
}
