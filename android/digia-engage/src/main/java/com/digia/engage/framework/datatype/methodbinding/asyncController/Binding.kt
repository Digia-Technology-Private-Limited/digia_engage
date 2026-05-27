package com.digia.engage.framework.datatype.methodbinding.asyncController

import com.digia.engage.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.engage.framework.datatype.methodbinding.MethodCommand
import com.digia.engage.framework.widgets.AsyncController

fun registerMethodCommandsForAsyncController(registry: MethodBindingRegistry) {
    registry.registerMethods<AsyncController<Any?>>(
        mapOf(
            "invalidate" to AsyncControllerInvalidateCommand()
        )
    )
}

class AsyncControllerInvalidateCommand : MethodCommand<AsyncController<Any?>>() {
    override fun run(instance: AsyncController<Any?>, args: Map<String, Any?>) {
        instance.invalidate()
    }
}