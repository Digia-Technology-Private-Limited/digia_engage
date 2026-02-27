package com.digia.digiaui.framework.state

import androidx.lifecycle.ViewModel

/**
 * ViewModel to hold StateTree and StateContexts across configuration changes and navigation. This
 * allows storing complex objects that wouldn't survive Saver serialization.
 */
class StateViewModel : ViewModel() {
    val stateTree = StateTree()

    // Cache of StateContexts by namespace
    private val stateContexts = mutableMapOf<String?, StateContext>()

    fun getOrCreateStateContext(
            namespace: String?,
            initialState: Map<String, Any?> = emptyMap()
    ): StateContext {
        return stateContexts.getOrPut(namespace) {
            StateContext(namespace = namespace, tree = stateTree, initialState = initialState)
        }
    }

    override fun onCleared() {

        super.onCleared()
        stateContexts.values.forEach { it.dispose() }
        stateContexts.clear()
        // StateContexts are not disposed here — they persist across navigation
    }
}
