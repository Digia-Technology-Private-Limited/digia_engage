package com.digia.digiaui.framework.datatype.methodbinding.apiCancelToken

import com.digia.digiaui.framework.datatype.CancelToken
import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.datatype.methodbinding.MethodCommand

fun registerMethodCommandsForApiCancelToken(registry: MethodBindingRegistry) {
    registry.registerMethods<CancelToken>(
        mapOf(
            "cancel" to APICancelTokenCommand()
        )
    )
}

class APICancelTokenCommand : MethodCommand<CancelToken>() {
    override fun run(instance: CancelToken, args: Map<String, Any?>) {
        val reason = args["reason"] as? String ?: "User canceled the upload"
        instance.cancel(reason)
    }
}