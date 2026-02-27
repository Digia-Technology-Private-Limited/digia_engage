package com.digia.digiaui.app

import androidx.compose.runtime.*
import com.digia.digiaui.framework.analytics.DUIAnalytics
import com.digia.digiaui.framework.message.MessageBus
import com.digia.digiaui.init.DigiaUI
import com.digia.digiaui.init.DigiaUIOptions

/**
 * A composable that handles the asynchronous initialization of the Digia UI system.
 *
 * [DigiaUIAppBuilder] provides a builder pattern that allows you to handle different states during
 * the initialization process (loading, ready, error). This is the recommended way to initialize
 * Digia UI as it properly manages the async initialization flow and provides appropriate feedback
 * to users.
 *
 * The composable will:
 * - Show loading state while initializing
 * - Transition to ready state with DigiaUIApp when initialization succeeds
 * - Show error state if initialization fails
 *
 * Example usage:
 * ```kotlin
 * DigiaUIAppBuilder(
 *     options = DigiaUIOptions(
 *         accessKey = "YOUR_ACCESS_KEY",
 *         flavor = Flavor.Production(),
 *     ),
 *     builder = { status ->
 *         when {
 *             status.isLoading -> LoadingScreen()
 *             status.hasError -> ErrorScreen(error = status.error)
 *             else -> {
 *                 // Your app content here
 *                 DUIFactory.getInstance().createInitialPage()
 *             }
 *         }
 *     }
 * )
 * ```
 *
 * @param options Configuration options for initializing the Digia UI system
 * @param builder Builder function that receives the current initialization status
 * @param analytics Optional analytics handler for tracking user interactions and events
 * @param messageBus Optional message bus for inter-component communication
 * @param environmentVariables Environment variables to make available in expressions
 */
@Composable
fun DigiaUIAppBuilder(
    options: DigiaUIOptions,
    builder: @Composable (DigiaUIStatus) -> Unit,
    analytics: DUIAnalytics? = null,
    messageBus: MessageBus = MessageBus(),
    environmentVariables: Map<String, Any?>? = null
) {
    // State to hold the current initialization status
    var status by remember { mutableStateOf<DigiaUIStatus>(DigiaUIStatus.Loading) }

    // Launch initialization effect
    LaunchedEffect(options) {
        try {
            // Attempt to create and initialize DigiaUI with provided options
            val digiaUI = DigiaUI.initialize(options)

            status = DigiaUIStatus.Ready(digiaUI)
        } catch (error: Throwable) {
            // Handle initialization errors and update status accordingly
            status = DigiaUIStatus.Error(error)
        }
    }

    // Render based on current status
    when (val currentStatus = status) {
        is DigiaUIStatus.Loading -> {
            builder(currentStatus)
        }
        is DigiaUIStatus.Ready -> {
            DigiaUIApp(
                    digiaUI = currentStatus.digiaUI,
                    analytics = analytics,
                    messageBus = messageBus,
                    environmentVariables = environmentVariables
            ) { builder(currentStatus) }
        }
        is DigiaUIStatus.Error -> {
            builder(currentStatus)
        }
    }
}

/**
 * Represents the current status of Digia UI initialization process.
 *
 * This sealed class encapsulates the current state along with relevant data such as the initialized
 * DigiaUI instance (when ready) or error information (when failed).
 */
sealed class DigiaUIStatus {
    /** Initialization is in progress */
    object Loading : DigiaUIStatus() {
        val isLoading = true
        val isReady = false
        val hasError = false
    }

    /**
     * Initialization completed successfully and system is ready to use
     *
     * @param digiaUI The initialized DigiaUI instance
     */
    data class Ready(val digiaUI: DigiaUI) : DigiaUIStatus() {
        val isLoading = false
        val isReady = true
        val hasError = false
    }

    /**
     * Initialization failed with an error
     *
     * @param error Error that occurred during initialization
     */
    data class Error(val error: Throwable) : DigiaUIStatus() {
        val isLoading = false
        val isReady = false
        val hasError = true
        val message: String
            get() = error.message ?: "Unknown error"
    }
}
