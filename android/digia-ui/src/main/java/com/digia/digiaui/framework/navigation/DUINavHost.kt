package com.digia.digiaui.framework.navigation

import LocalUIResources
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.actions.LocalActionExecutor
import com.digia.digiaui.framework.page.ConfigProvider
import com.digia.digiaui.framework.page.DUIPage
import kotlinx.coroutines.flow.collectLatest

/** Navigation controller composition local for providing access throughout the tree */
val LocalDUINavController =
        staticCompositionLocalOf<NavHostController> { error("NavController not provided") }

/**
 * DUINavHost - Navigation host component using official Compose Navigation
 *
 * Manages page navigation and routing based on the DUIConfig using androidx.navigation.compose.
 * Integrates with NavigationManager to bridge existing navigation events to Compose Navigation.
 *
 * @param configProvider The configuration provider containing page definitions
 * @param startPageId The initial page to display
 * @param startPageArgs Optional arguments for the start page
 * @param registry The widget registry for creating widgets
 * @param navController Optional custom NavController (creates one if not provided)
 */
@Composable
fun DUINavHost(
        configProvider: ConfigProvider,
        startPageId: String,
        startPageArgs: Map<String, Any?>? = null,
        registry: VirtualWidgetRegistry,
        navController: NavHostController = rememberNavController()
) {

    val actionExecutor = LocalActionExecutor.current
    val context = LocalContext.current
    val resource = LocalUIResources.current
    // Store start page args if provided
    LaunchedEffect(startPageArgs) {
        if (startPageArgs != null) {
            NavigationManager.setPageArgs(startPageId, startPageArgs)
        }
    }

    // Track backstack changes to manage state lifecycle
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            // Mark the current page as entered in backstack
            entry?.let {
                val pageId = it.toRoute<PageRoute>().pageId
                NavigationManager.markPageEntered(pageId)
            }
        }
    }

    // Listen to navigation events from NavigationManager
    LaunchedEffect(navController) {
        NavigationManager.onNavHostAttached()
        try {
            NavigationManager.navigationEvents.collectLatest { event ->
                when (event) {
                    is NavigationEvent.Navigate -> {
                        // Store arguments for the new page
                        if (event.args != null) {
                            NavigationManager.setPageArgs(event.route.pageId, event.args)
                        }

                        // Navigate using type-safe route
                        if (event.replace) {
                            // Mark replaced page as left
                            navController.currentBackStackEntry?.let {
                                val replacedPageId = it.toRoute<PageRoute>().pageId
                                NavigationManager.markPageLeft(replacedPageId)
                            }
                            navController.navigate(event.route) {
                                popUpTo(
                                        navController.currentDestination?.route
                                                ?: PageRoute(startPageId)
                                ) {
                                    inclusive = true
                                    saveState = true
                                }
                                restoreState = true
                            }
                        } else {
                            navController.navigate(event.route) { restoreState = true }
                        }
                    }
                    is NavigationEvent.Pop -> {
                        val canPop = navController.previousBackStackEntry != null
                        if (canPop || !event.maybe) {
                            // The page being popped (this is the key used to register the result
                            // callback)
                            val poppedPageId =
                                    navController.currentBackStackEntry?.let { backStackEntry ->
                                        backStackEntry.toRoute<PageRoute>().pageId
                                    }

                            // Mark page as left before popping
                            if (poppedPageId != null) {
                                NavigationManager.markPageLeft(poppedPageId)
                            }

                            // Pop the current page
                            navController.popBackStack()

                            // Execute result callback if there's a result and a registered callback
                            // key.
                            if (event.result != null && poppedPageId != null) {
                                NavigationManager.executeResultCallback(poppedPageId, event.result)
                            }
                        }
                    }
                    is NavigationEvent.PopTo -> {
                        // Mark pages being removed as left
                        var current = navController.currentBackStackEntry
                        while (current != null && current.toRoute<PageRoute>() != event.route) {
                            val pageId = current.toRoute<PageRoute>().pageId
                            NavigationManager.markPageLeft(pageId)
                            current = navController.previousBackStackEntry
                        }
                        if (event.inclusive && current != null) {
                            val pageId = current.toRoute<PageRoute>().pageId
                            NavigationManager.markPageLeft(pageId)
                        }
                        navController.popBackStack(route = event.route, inclusive = event.inclusive)
                    }
                    is NavigationEvent.ExecuteResultCallback -> {
                        actionExecutor.execute(
                                context,
                                event.actionFlow,
                                event.scopeContext,
                                event.stateContext,
                                resource
                        )
                    }
                }
            }
        } finally {
            NavigationManager.onNavHostDetached()
        }
    }

    // Handle back button
    BackHandler(enabled = navController.previousBackStackEntry != null) {
        // Mark current page as left before back navigation
        navController.currentBackStackEntry?.let {
            val pageId = it.toRoute<PageRoute>().pageId
            NavigationManager.markPageLeft(pageId)
        }
        navController.popBackStack()
    }

    // Provide NavController to composition tree
    CompositionLocalProvider(LocalDUINavController provides navController) {
        NavHost(navController = navController, startDestination = PageRoute(startPageId)) {
            // Register a single type-safe route pattern that handles ALL pages
            composable<PageRoute> { backStackEntry ->
                // Extract the actual page ID from the type-safe route
                val route = backStackEntry.toRoute<PageRoute>()
                val pageId = route.pageId

                // Get page arguments from NavigationManager
                val pageArgs = remember(pageId) { NavigationManager.getPageArgs(pageId) }

                val pageDef = remember(pageId) { configProvider.getPageDefinition(pageId) }

                // All pages render through the same DUIPage composable
                DUIPage(
                        pageId = pageId,
                        pageArgs = pageArgs,
                        pageDef = pageDef,
                        registry = registry
                )
            }
        }
    }
}
