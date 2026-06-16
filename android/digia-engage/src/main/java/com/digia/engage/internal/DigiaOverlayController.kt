package com.digia.engage.internal

import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import com.digia.engage.internal.model.InlineCarouselConfig
import com.digia.engage.internal.model.InlineStoryConfig
import com.digia.engage.internal.model.NudgeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class StoryOverlayState(
    val config: InlineStoryConfig,
    val initialIndex: Int,
    val payload: InAppPayload,
)

internal data class NudgeOverlayState(
    val config: NudgeConfig,
    val payload: InAppPayload,
    val defaultVariables: Map<String, String> = emptyMap(),
)

internal class DigiaOverlayController {

    private val _activePayload = MutableStateFlow<InAppPayload?>(null)
    val activePayload: StateFlow<InAppPayload?> = _activePayload.asStateFlow()

    private val _slotPayloads = MutableStateFlow<Map<String, InAppPayload>>(emptyMap())
    val slotPayloads: StateFlow<Map<String, InAppPayload>> = _slotPayloads.asStateFlow()

    private val _slotConfigs = MutableStateFlow<Map<String, InlineCarouselConfig>>(emptyMap())
    val slotConfigs: StateFlow<Map<String, InlineCarouselConfig>> = _slotConfigs.asStateFlow()

    private val _storySlotConfigs = MutableStateFlow<Map<String, InlineStoryConfig>>(emptyMap())
    val storySlotConfigs: StateFlow<Map<String, InlineStoryConfig>> = _storySlotConfigs.asStateFlow()

    private val _storyOverlay = MutableStateFlow<StoryOverlayState?>(null)
    val storyOverlay: StateFlow<StoryOverlayState?> = _storyOverlay.asStateFlow()

    private val _nudgeOverlay = MutableStateFlow<NudgeOverlayState?>(null)
    val nudgeOverlay: StateFlow<NudgeOverlayState?> = _nudgeOverlay.asStateFlow()

    fun show(payload: InAppPayload) {
        _activePayload.value = payload
    }

    fun dismiss() {
        _activePayload.value = null
    }

    fun addSlot(placementKey: String, payload: InAppPayload) {
        _slotPayloads.update { current -> current + (placementKey to payload) }
    }

    fun removeSlotById(payloadId: String) {
        _slotPayloads.update { current -> current.filterValues { it.id != payloadId } }
    }

    fun removeSlotByKey(placementKey: String) {
        _slotPayloads.update { current -> current - placementKey }
    }

    fun clearSlots() {
        _slotPayloads.value = emptyMap()
    }

    fun addSlotConfig(slotKey: String, config: InlineCarouselConfig) {
        _slotConfigs.update { current -> current + (slotKey to config) }
    }

    fun removeSlotConfig(slotKey: String) {
        _slotConfigs.update { current -> current - slotKey }
    }

    fun clearSlotConfigs() {
        _slotConfigs.value = emptyMap()
    }

    fun addStorySlotConfig(slotKey: String, config: InlineStoryConfig) {
        _storySlotConfigs.update { current -> current + (slotKey to config) }
    }

    fun removeStorySlotConfig(slotKey: String) {
        _storySlotConfigs.update { current -> current - slotKey }
    }

    fun clearStorySlotConfigs() {
        _storySlotConfigs.value = emptyMap()
    }

    fun showStoryOverlay(config: InlineStoryConfig, initialIndex: Int, payload: InAppPayload) {
        _storyOverlay.value = StoryOverlayState(config, initialIndex, payload)
    }

    fun dismissStoryOverlay() {
        _storyOverlay.value = null
    }

    fun showNudge(config: NudgeConfig, payload: InAppPayload, defaultVariables: Map<String, String> = emptyMap()) {
        _nudgeOverlay.value = NudgeOverlayState(config, payload, defaultVariables)
    }

    fun dismissNudge() {
        _nudgeOverlay.value = null
    }

    var onEvent: ((DigiaExperienceEvent, InAppPayload) -> Unit)? = null

    var onAction: ((actionType: String, url: String, payload: InAppPayload) -> Boolean)? = null
}
