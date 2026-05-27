package com.digia.engage.framework.datatype.methodbinding.timerController

import com.digia.engage.framework.widgets.timer.TimerController
import com.digia.engage.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.engage.framework.datatype.methodbinding.MethodCommand

fun registerMethodCommandsForTimerController(registry: MethodBindingRegistry) {
    registry.registerMethods<TimerController>(
        mapOf(
            "start" to TimerControllerStartCommand(),
            "resume" to TimerControllerResumeCommand(),
            "pause" to TimerControllerPauseCommand(),
            "reset" to TimerControllerResetCommand()
        )
    )
}

class TimerControllerStartCommand : MethodCommand<TimerController>() {
    override fun run(instance: TimerController, args: Map<String, Any?>) {
        instance.start()
    }
}

class TimerControllerResumeCommand : MethodCommand<TimerController>() {
    override fun run(instance: TimerController, args: Map<String, Any?>) {
        instance.resume()
    }
}

class TimerControllerPauseCommand : MethodCommand<TimerController>() {
    override fun run(instance: TimerController, args: Map<String, Any?>) {
        instance.pause()
    }
}

class TimerControllerResetCommand : MethodCommand<TimerController>() {
    override fun run(instance: TimerController, args: Map<String, Any?>) {
        instance.reset()
    }
}

