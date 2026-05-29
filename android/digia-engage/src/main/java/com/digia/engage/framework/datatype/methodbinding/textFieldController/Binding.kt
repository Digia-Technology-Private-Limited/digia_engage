package com.digia.engage.framework.datatype.methodbinding.textFieldController

import com.digia.engage.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.engage.framework.datatype.methodbinding.MethodCommand
import com.digia.engage.framework.widgets.TextController

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