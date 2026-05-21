package com.digia.engage.internal

import android.graphics.Rect
import com.digia.engage.DigiaExperienceEvent
import com.digia.engage.InAppPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class AnchoredOverlayState(
    val payload: InAppPayload,
    val anchorKey: String,
    val anchorRect: Rect,
    val command: String, // "SHOW_TOOLTIP" or "SHOW_SPOTLIGHT"
    val cornerRadius: Float = 0f,
)

internal class DigiaOverlayController {

    private val _activePayload = MutableStateFlow<InAppPayload?>(null)
    val activePayload: StateFlow<InAppPayload?> = _activePayload.asStateFlow()

    private val _slotPayloads = MutableStateFlow<Map<String, InAppPayload>>(emptyMap())
    val slotPayloads: StateFlow<Map<String, InAppPayload>> = _slotPayloads.asStateFlow()

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

    private val _activeAnchoredOverlay = MutableStateFlow<AnchoredOverlayState?>(null)
    val activeAnchoredOverlay: StateFlow<AnchoredOverlayState?> = _activeAnchoredOverlay.asStateFlow()

    fun showAnchored(state: AnchoredOverlayState) {
        _activeAnchoredOverlay.value = state
    }

    fun dismissAnchored() {
        _activeAnchoredOverlay.value = null
    }

    var onEvent: ((DigiaExperienceEvent, InAppPayload) -> Unit)? = null
}
