package com.digia.digiaui.framework.datatype.methodbinding.textFieldController

import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.datatype.methodbinding.MethodCommand
import com.digia.digiaui.framework.widgets.TextController

fun registerMethodCommandsForTextFieldController(registry: MethodBindingRegistry) {
    registry.registerMethods<TextController>(
        mapOf(
            "setValue" to TextFieldControllerSetValueCommand(),
            "clear" to TextFieldControllerClearCommand()
        )
    )
}

class TextFieldControllerSetValueCommand : MethodCommand<TextController>() {
    override fun run(instance: TextController, args: Map<String, Any?>) {
        val text = args["text"] as? String ?: ""
        instance.text = text
    }
}

class TextFieldControllerClearCommand : MethodCommand<TextController>() {
    override fun run(instance: TextController, args: Map<String, Any?>) {
        instance.text = ""
    }
}