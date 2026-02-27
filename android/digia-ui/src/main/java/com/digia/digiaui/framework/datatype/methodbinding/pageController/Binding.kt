package com.digia.digiaui.framework.datatype.methodbinding.pageController

import androidx.compose.animation.core.tween
import com.digia.digiaui.framework.datatype.AdaptedPageController
import com.digia.digiaui.framework.datatype.methodbinding.MethodBindingRegistry
import com.digia.digiaui.framework.datatype.methodbinding.MethodCommand
import com.digia.digiaui.framework.utils.NumUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun registerMethodCommandsForPageController(registry: MethodBindingRegistry) {
    registry.registerMethods<AdaptedPageController>(
        mapOf(
            "jumpToPage" to PageControllerJumpToPageCommand(),
            "animateToPage" to PageControllerAnimateToPageCommand(),
        )
    )
}

class PageControllerJumpToPageCommand : MethodCommand<AdaptedPageController>() {
    override fun run(instance: AdaptedPageController, args: Map<String, Any?>) {
        val page = NumUtil.toInt(args["page"]) ?: 0
        CoroutineScope(Dispatchers.Main).launch {
            instance.jumpToPage(page)
        }
    }
}

class PageControllerAnimateToPageCommand : MethodCommand<AdaptedPageController>() {
    override fun run(instance: AdaptedPageController, args: Map<String, Any?>) {
        val page = NumUtil.toInt(args["page"]) ?: 0
        val durationInMs = NumUtil.toInt(args["durationInMs"]) ?: 300
        CoroutineScope(Dispatchers.Main).launch {
            instance.animateToPage(page, animationSpec = tween(durationMillis = durationInMs))
        }
    }
}
