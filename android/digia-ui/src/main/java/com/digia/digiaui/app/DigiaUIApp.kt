package com.digia.digiaui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
//import com.digia.digiaui.core.DigiaUIScope
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.DUIFontFactory
import com.digia.digiaui.framework.actions.showToast.DUISnackbarHost
import com.digia.digiaui.framework.actions.showToast.DUISnackbarManager
import com.digia.digiaui.framework.analytics.AnalyticsHandler
import com.digia.digiaui.framework.analytics.DUIAnalytics
import com.digia.digiaui.framework.appstate.DUIAppState
import com.digia.digiaui.framework.logging.Logger
import com.digia.digiaui.framework.message.MessageBus
import com.digia.digiaui.framework.page.ConfigProvider
import com.digia.digiaui.init.DigiaUI
import com.digia.digiaui.init.DigiaUIManager

/**
 * The main application wrapper for integrating Digia UI SDK into Android Compose applications.
 *
 * [DigiaUIApp] serves as the root composable that manages the lifecycle of the Digia UI system and
 * provides a scope for analytics, messaging, and other core functionalities.
 *
 * This composable handles:
 * - Initialization and disposal of the Digia UI system
 * - Global app state management
 * - UI factory setup with custom resources
 * - Analytics and message bus integration
 * - Environment variable configuration
 * - Providing Digia UI context to child composables
 *
 * Example usage:
 * ```kotlin
 * DigiaUIApp(
 *     digiaUI = digiaUI,
 *     analytics = MyAnalyticsHandler(),
 *     messageBus = MyMessageBus(),
 *     icons = customIcons,
 *     environmentVariables = mapOf("authToken" to "1234567890")
 * ) {
 *     // Your app content here
 *     DUIFactory.getInstance().createInitialPage()
 * }
 * ```
 *
 * @param digiaUI The initialized DigiaUI instance containing configuration and resources
 * @param analytics Optional analytics handler for tracking user interactions and events
 * @param messageBus Optional message bus for inter-component communication
 * @param pageConfigFetcher Custom page configuration provider, defaults to built-in provider if not specified
 * @param icons Custom icon mappings to override or extend default icons
 * @param images Custom image provider mappings for app-specific images
 * @param fontFactory Custom font factory for creating text styles with specific fonts
 * @param environmentVariables Environment variables to make available in expressions and
 * configurations
 * @param content Builder function that creates the child composable tree
 */
@Composable
fun DigiaUIApp(
    digiaUI: DigiaUI,
    analytics: DUIAnalytics? = null,
    messageBus: MessageBus = MessageBus(),
    pageConfigFetcher: ConfigProvider? = null,
    icons: Map<String, ImageVector>? = null,
    images: Map<String, ImageBitmap>? = null,
    fontFactory: Any? = null,
    environmentVariables: Map<String, Any?>? = null,
    content: @Composable () -> Unit
) {

    var isReady by remember { mutableStateOf(false) }

    /**
     * One-time async initialization
     */
    LaunchedEffect(digiaUI) {
        DigiaUIManager.initialize(digiaUI)
        DigiaUIManager.getInstance().messageBus = messageBus
        
        // Initialize managers
        DigiaUIManager.getInstance().bottomSheetManager = 
            com.digia.digiaui.framework.bottomsheet.BottomSheetManager()
        DigiaUIManager.getInstance().dialogManager = 
            com.digia.digiaui.framework.dialog.DialogManager()

        
        // Set analytics provider
        analytics?.let {
            AnalyticsHandler.analyticsProvider = it
        }

        DUIAppState.instance.init(
            digiaUI.dslConfig.appState ?: emptyList()
        )

        DUIFactory.getInstance().initialize(
            pageConfigFetcher = pageConfigFetcher,
            icons = icons ,
            images = images ,
            fontFactory = fontFactory as DUIFontFactory?
        )

        environmentVariables?.let {
            DUIFactory.getInstance().setEnvironmentVariables(it)
        }

        isReady = true
        Logger.log("DigiaUIApp initialized")
    }

    /**
     * Cleanup (non-suspending)
     */
    DisposableEffect(Unit) {
        onDispose {
            DigiaUIManager.destroy()
            DUIFactory.getInstance().destroy()
            Logger.log("DigiaUIApp disposed")
        }
    }

    if(!isReady){
        // Optionally, you can show a loading indicator here
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
//        DigiaUIScope(
//            messageBus = messageBus,
//            analytics = analytics
//        ) {
            content()
//        }
        
        // Dialog Host
        DigiaUIManager.getInstance().dialogManager?.let { manager ->
            com.digia.digiaui.framework.dialog.DialogHost(
                dialogManager = manager,
                registry = DUIFactory.getInstance().getRegistry(),
                resources = DUIFactory.getInstance().getResources()
            )
        }
        
        // Bottom Sheet Host
        DigiaUIManager.getInstance().bottomSheetManager?.let { manager ->
            com.digia.digiaui.framework.bottomsheet.BottomSheetHost(
                bottomSheetManager = manager,
                resources = DUIFactory.getInstance().getResources()
            )
        }
    }
}
