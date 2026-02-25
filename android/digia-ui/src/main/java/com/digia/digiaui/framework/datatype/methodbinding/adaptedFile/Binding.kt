package com.digia.digiaui.framework.datatype.methodbinding.adaptedFile

import com.digia.digiaui.framework.datatype.adaptedfile.AdaptedFile
import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.datatype.methodbinding.MethodCommand

fun registerMethodCommandsForFile(registry: MethodBindingRegistry) {
    registry.registerMethods<AdaptedFile>(
        mapOf(
            "setNull" to FileSetNullCommand()
        )
    )
}

class FileSetNullCommand : MethodCommand<AdaptedFile>() {
    override fun run(instance: AdaptedFile, args: Map<String, Any?>) {
        instance.setDataFromAdaptedFile(AdaptedFile())
    }
}