package com.digia.digiaui.framework.datatype.methodbinding.asyncController

import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.datatype.methodbinding.MethodCommand
import com.digia.digiaui.framework.widgets.AsyncController

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