package com.digia.digiaui.framework.appstate

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * A reactive value that holds a value and exposes its changes
 */
open class ReactiveValue<T>(
    initialValue: T,
    val streamName: String
) {

    private val _state = MutableStateFlow(initialValue)

    val flow: StateFlow<T> = _state.asStateFlow()

    val value: T
        get() = _state.value

    open fun update(newValue: T): Boolean {
        return if (_state.value != newValue) {
            _state.value = newValue
            true
        } else {
            false
        }
    }
}
