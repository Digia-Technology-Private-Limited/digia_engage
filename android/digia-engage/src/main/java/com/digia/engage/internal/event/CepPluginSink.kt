package com.digia.engage.internal.event

import com.digia.engage.CEPTriggerPayload
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.internal.PluginRegistry

/**
 * Delivers coarse [DigiaExperienceEvent]s to the registered CEP plugin.
 *
 * The CEP channel is a campaign-agnostic protocol — the plugin only understands
 * Impressed/Clicked/Dismissed. [PluginRegistry.notifyEvent] is a clean no-op
 * when no plugin is registered, so callers fire unconditionally (SRP: this knows
 * only how to reach the CEP).
 */
internal class CepPluginSink(
    private val pluginRegistry: PluginRegistry,
) {
    fun deliver(event: DigiaExperienceEvent, payload: CEPTriggerPayload) {
        pluginRegistry.notifyEvent(event, payload)
    }
}
