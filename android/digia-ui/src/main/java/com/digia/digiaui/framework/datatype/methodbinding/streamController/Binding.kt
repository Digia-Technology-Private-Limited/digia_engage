package com.digia.digiaui.framework.datatype.methodbinding.streamController

import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.datatype.methodbinding.MethodCommand
import kotlinx.coroutines.flow.MutableStateFlow

fun registerMethodCommandsForStreamController(registry: MethodBindingRegistry) {
    registry.registerMethods<MutableStateFlow<Any?>>(
        mapOf(
            "add" to StreamControllerAddCommand(),
            "close" to StreamControllerCloseCommand()
        )
    )
}

class StreamControllerAddCommand : MethodCommand<MutableStateFlow<Any?>>() {
    override fun run(instance: MutableStateFlow<Any?>, args: Map<String, Any?>) {
        val value = args["value"]
        instance.value = value
    }
}

class StreamControllerCloseCommand : MethodCommand<MutableStateFlow<Any?>>() {
    override fun run(instance: MutableStateFlow<Any?>, args: Map<String, Any?>) {
        // In Kotlin Flow, there's no close operation like Dart StreamController
        // This is a no-op or could set to a terminal value
        instance.value = null
    }
}