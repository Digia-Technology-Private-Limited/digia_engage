package com.digia.digiaui.framework.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.page.ConfigProvider
import com.digia.digiaui.framework.page.DUIPage
import java.util.UUID

//
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.CompositionLocalProvider
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.remember
//import androidx.navigation.NavHostController
////import androidx.navigation.compose.NavHost
////import androidx.navigation.compose.composable
////import androidx.navigation.compose.rememberNavController
//import androidx.navigation.toRoute
//import com.digia.digiaui.config.model.DUIConfig
//import com.digia.digiaui.framework.VirtualWidgetRegistry
//import com.digia.digiaui.framework.page.ConfigProvider
//import com.digia.digiaui.framework.page.DUIPage
//import kotlinx.coroutines.flow.collectLatest
//
///**
// * DUINavHost
// *
// * Navigation host component for the Digia UI framework.
// * Manages page navigation and routing based on the DUIConfig.
// * Uses a single route pattern with page ID as parameter for efficiency.
// *
// * @param configProvider The configuration provider containing page definitions
// * @param startPageId The initial page to display
// * @param registry The widget registry for creating widgets
// * @param navController Optional custom NavController (creates one if not provided)
// */
//@Composable
//fun DUINavHost(
//    configProvider: ConfigProvider,
//    startPageId: String,
//    registry: VirtualWidgetRegistry,
//    navController: NavHostController = rememberNavController()
//) {
//    // Listen to navigation events from NavigationManager
//    LaunchedEffect(navController) {
//        NavigationManager.navigationEvents.collectLatest { event ->
//            when (event) {
//                is NavigationEvent.Navigate -> {
//                    // Navigate using type-safe route
//                    // Arguments are already stored in NavigationManager
//                    if (event.replace) {
//                        navController.navigate(event.route) {
//                            // Pop up to the previous destination
//                            popUpTo(navController.currentDestination?.route ?: PageRoute(startPageId)) {
//                                inclusive = true
//                                saveState = true        // Save current state
//                            }
//                        }
//                    } else {
//                        navController.navigate(event.route)
//                    }
//                }
//                is NavigationEvent.Pop -> {
//                    if (navController.previousBackStackEntry != null) {
//                        // Get the previous route to execute result callback
//                        val previousRoute = navController.previousBackStackEntry?.destination?.route
//
//                        // Pop the stack
//                        navController.popBackStack()
//
//                        // Execute result callback if registered
//                        if (previousRoute != null && event.result != null) {
//                            NavigationManager.executeResultCallback(previousRoute, event.result)
//                        }
//                    }
//                }
//                is NavigationEvent.PopTo -> {
//                    navController.popBackStack(
//                        route = event.route,
//                        inclusive = event.inclusive
//                    )
//                }
//                is NavigationEvent.ExecuteResultCallback -> {
//                    // Execute the result callback action flow
//                    // This would require ActionExecutor to be available in this context
//                    // For now, just log it
//                    println("DUINavHost: Would execute result callback with result: ${event.result}")
//                    // TODO: Execute event.actionFlow with result in scope context
//                    // This requires access to ActionExecutor which should be provided through composition
//                }
//            }
//        }
//    }
//
//    // Provide NavController to composition tree
//    CompositionLocalProvider(LocalNavController provides navController) {
//        NavHost(
//            navController = navController,
//            startDestination = PageRoute(startPageId)
//        ) {
//            // Register a single type-safe route pattern that handles ALL pages
//            composable<PageRoute> { backStackEntry ->
//                // Extract the actual page ID from the type-safe route
//                val route = backStackEntry.toRoute<PageRoute>()
//                val pageId = route.pageId
//
//                // Get page arguments from NavigationManager only once (single-use data)
//                // Cache with remember so it survives recompositions
//                val pageArgs =
//                    NavigationManager.getPageArgs(pageId)
//
//                val page = configProvider.getPageDefinition(pageId)
//
//                // All pages render through the same DUIPage composable
//                DUIPage(
//                    pageId = pageId,
//                    pageArgs = pageArgs,
//                    pageDef = page,
//                    registry = registry
//                )
//            }
//        }
//    }
//}


@Stable
data class DUIPageEntry(
    val pageId: String,
    val args: Map<String, Any?>?,
    val key: String = UUID.randomUUID().toString()
)


@Stable
class DUINavState {

    private val _stack = mutableStateListOf<DUIPageEntry>()
    val stack: List<DUIPageEntry> get() = _stack

    fun push(page: DUIPageEntry) {
        _stack.add(page)
    }

    fun replace(page: DUIPageEntry) {
        if (_stack.isNotEmpty()) _stack.removeLast()
        _stack.add(page)
    }

    fun pop(): DUIPageEntry? {
        if (_stack.size <= 1) return null
        return _stack.removeLast()
    }

    fun canPop() = _stack.size > 1

    fun popTo(pageId: String, inclusive: Boolean) {
        val index = _stack.indexOfLast { it.pageId == pageId }
        if (index == -1) return

        val target = if (inclusive) index else index + 1
        while (_stack.size > target) {
            _stack.removeLast()
        }
    }
}


class DUINavController internal constructor(
    private val state: DUINavState
) {

    fun navigate(
        pageId: String,
        args: Map<String, Any?>? = null,
        replace: Boolean = false
    ) {
        val entry = DUIPageEntry(pageId, args)
        if (replace) state.replace(entry)
        else state.push(entry)
    }

    fun pop(result: Any? = null, maybe: Boolean = true) {
        if (!maybe || state.canPop()) {
            val popped = state.pop()
            if (popped != null && result != null) {
                NavigationManager.executeResultCallback(popped.pageId, result)
            }
        }
    }

    fun popTo(pageId: String, inclusive: Boolean = false) {
        state.popTo(pageId, inclusive)
    }
}


val LocalDUINavController =
    staticCompositionLocalOf<DUINavController> {
        error("DUINavController not provided")
    }


@Composable
fun DUINavHost(
    configProvider: ConfigProvider,
    startPageId: String,
    startPageArgs: Map<String, Any?>? = null,
    registry: VirtualWidgetRegistry
) {
    val navState = remember {
        DUINavState().apply {
            push(DUIPageEntry(startPageId, startPageArgs))
        }
    }

    val navController = remember {
        DUINavController(navState)
    }

    // Bridge NavigationManager → Nav3
    LaunchedEffect(Unit) {
        NavigationManager.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.Navigate -> {
                    navController.navigate(
                        pageId = event.route.pageId,
                        args = event.args,
                        replace = event.replace
                    )
                }

                is NavigationEvent.Pop -> {
                    navController.pop(event.result, event.maybe)
                }

                is NavigationEvent.PopTo -> {
                    navController.popTo(
                        event.route.pageId,
                        event.inclusive
                    )
                }

                is NavigationEvent.ExecuteResultCallback -> {
                    // handled via pop()
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalDUINavController provides navController
    ) {
        navState.stack.forEachIndexed { index, entry ->
            val isTop = index == navState.stack.lastIndex

            key(entry.key) {

                val transition = updateTransition(
                    targetState = isTop,
                    label = "pageTransition"
                )

                val alpha by transition.animateFloat(
                    label = "alpha",
                    transitionSpec = { tween(durationMillis = 250) }
                ) { visible ->
                    if (visible) 1f else 0f
                }

                val translation by transition.animateDp(
                    label = "translationX",
                    transitionSpec = { tween(durationMillis = 250) }
                ) { visible ->
                    if (visible) 0.dp else 32.dp
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (isTop) 1f else 0f)
                        .graphicsLayer {
                            this.alpha = alpha
                            translationX = translation.toPx()
                        }
                        .pointerInput(isTop) {
                            if (!isTop) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent()
                                }
                            }
                        }
                ) {
                    RenderDUIPage(
                        entry = entry,
                        configProvider = configProvider,
                        registry = registry
                    )
                }
            }
        }


        BackHandler(enabled = true) {
            navController.pop()
        }
    }
}

@Composable
private fun RenderDUIPage(
    entry: DUIPageEntry,
    configProvider: ConfigProvider,
    registry: VirtualWidgetRegistry
) {
    val pageDef = remember(entry.pageId) {
        configProvider.getPageDefinition(entry.pageId)
    }

    DUIPage(
        pageId = entry.pageId,
        pageArgs = entry.args,
        pageDef = pageDef,
        registry = registry
    )
}
