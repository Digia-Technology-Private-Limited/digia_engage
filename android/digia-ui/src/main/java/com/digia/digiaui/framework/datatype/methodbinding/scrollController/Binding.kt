package com.digia.digiaui.framework.datatype.methodbinding.scrollController

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import com.digia.digiaui.framework.datatype.AdaptedScrollController
import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.datatype.methodbinding.MethodCommand
import com.digia.digiaui.framework.utils.NumUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun registerMethodCommandsForScrollController(registry: MethodBindingRegistry) {
    registry.registerMethods<AdaptedScrollController>(
        mapOf(
            "jumpTo" to ScrollControllerJumpToCommand(),
            "animateTo" to ScrollControllerAnimateToCommand()
        )
    )
}

class ScrollControllerJumpToCommand : MethodCommand<AdaptedScrollController>() {
    override fun run(instance: AdaptedScrollController, args: Map<String, Any?>) {
        val offset = NumUtil.toDouble(args["offset"]) ?: 0f
        CoroutineScope(Dispatchers.Main).launch {
            instance.scrollTo(offset.toFloat())
        }
    }
}

class ScrollControllerAnimateToCommand : MethodCommand<AdaptedScrollController>() {
    override fun run(instance: AdaptedScrollController, args: Map<String, Any?>) {
        val offset = NumUtil.toDouble(args["offset"]) ?: 0f
        val durationInMs = NumUtil.toInt(args["durationInMs"]) ?: 300
        
        CoroutineScope(Dispatchers.Main).launch {
            instance.animateScrollTo(offset.toFloat(), tween(durationInMs))
        }
    }
}