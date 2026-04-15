package com.digia.digiaui.framework

import ResourceProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.ActionProvider
import com.digia.digiaui.framework.component.DUIComponent
import com.digia.digiaui.framework.logging.Logger
import com.digia.digiaui.framework.page.ConfigProvider
import com.digia.digiaui.framework.page.DUIConfigProvider
import com.digia.digiaui.framework.page.DUIPage
import com.digia.digiaui.framework.utils.asSafe
import com.digia.digiaui.framework.widgets.registerBuiltInWidgets
import com.digia.digiaui.init.DigiaUIManager
import com.digia.digiaui.utils.asSafe
import convertToTextStyle

/**
 * Central factory class for creating Digia UI widgets, pages, and components.
 *
 * [DUIFactory] is a singleton that serves as the main entry point for creating UI elements from
 * JSON configurations. It manages UI resources, widget registries, and provides methods for
 * creating various UI elements with optional customization.
 *
 * This follows the Flutter DUIFactory pattern, acting as the central point for:
 * - Creating pages and components from JSON definitions
 * - Managing UI resources (icons, images, fonts, colors)
 * - Handling widget registration for custom components
 * - Providing resource override capabilities
 * - Managing navigation routes and bottom sheets
 *
 * Example usage:
 * ```kotlin
 * // Initialize factory
 * DUIFactory.getInstance().initialize(config = appConfig)
 *
 * // Create a page
 * DUIFactory.getInstance().CreatePage("pageId")
 *
 * // Register custom widget
 * DUIFactory.getInstance().registerWidget("custom/myWidget") { data, parent ->
 *     MyCustomWidget(data)
 * }
 * ```
 */
enum class UIActionType {
    SHOW_BOTTOM_SHEET,
    SHOW_DIALOG,
    CLOSE_BOTTOM_SHEET,
    CLOSE_DIALOG
}

/** Public factory for Digia server-driven UI (Flutter / iOS parity). */
public class DUIFactory private constructor() {

    private lateinit var configProvider: ConfigProvider
    private lateinit var widgetRegistry: DefaultVirtualWidgetRegistry
    private var resources: UIResources = UIResources()
    private var isInitialized = false

    fun ShowUIAction(
            actionType: UIActionType,
            componentId: String,
            componentArgs: Map<String, Any?>? = null,
            onDismiss: ((Any?) -> Unit)? = null
    ) {
        when (actionType) {
            UIActionType.SHOW_BOTTOM_SHEET -> {
                DigiaUIManager.getInstance()
                        .bottomSheetManager
                        ?.show(
                                componentId = componentId,
                                args = componentArgs,
                                maxHeightRatio = 0.7F,
                                onDismiss = onDismiss
                        )
            }
            UIActionType.SHOW_DIALOG -> {
                DigiaUIManager.getInstance()
                        .dialogManager
                        ?.show(
                                componentId = componentId,
                                args = componentArgs,
                                onDismiss = onDismiss
                        )
            }
            UIActionType.CLOSE_BOTTOM_SHEET -> {
                DigiaUIManager.getInstance().bottomSheetManager?.dismiss()
            }
            UIActionType.CLOSE_DIALOG -> {
                DigiaUIManager.getInstance().dialogManager?.dismiss()
            }
        }
    }

    /**
     * Initializes the singleton factory with all necessary configuration and resources.
     *
     * This method must be called before using any other factory methods. It sets up the widget
     * registry, configuration provider, and UI resources based on the initialized DigiaUI instance
     * and optional custom resources.
     *
     * @param config The application configuration containing pages, components, and resources
     * @param pageConfigFetcher Custom page configuration provider, defaults to built-in provider if
     * not specified
     * @param icons Custom icon mappings to override or extend default icons
     * @param images Custom image provider mappings for app-specific images
     * @param textStyles Custom text styles for typography
     * @param colors Custom color tokens for light theme
     * @param darkColors Custom color tokens for dark theme
     * @param fontFactory Custom font factory for creating text styles with specific fonts
     *
     * @throws IllegalStateException if DigiaUIManager is not properly initialized
     *
     * Note: Environment variables should be set using the DUIConfig or DigiaUIManager directly
     */
    fun initialize(
            pageConfigFetcher: ConfigProvider? = null,
            icons: Map<String, ImageVector>? = null,
            images: Map<String, ImageBitmap>? = null,
            fontFactory: DUIFontFactory? = null
    ) {
        if (isInitialized) {
            Logger.log("DUIFactory already initialized, reinitializing...")
            destroy()
        }

        // Ensure DigiaUIManager is properly initialized
        val digiaUIInstance = DigiaUIManager.getInstance().safeInstance
        if (digiaUIInstance == null) {
            throw IllegalStateException(
                    "DigiaUIManager is not initialized. Make sure to call DigiaUI.initialize() " +
                            "and DigiaUIManager.initialize() before calling DUIFactory.initialize()."
            )
        }

        // Initialize configuration provider with custom provider or default
        configProvider = pageConfigFetcher ?: DUIConfigProvider(digiaUIInstance.dslConfig)

        // Create UI resources from config and custom overrides
        resources =
                UIResources(
                        icons = icons,
                        images = images,
                        textStyles =
                                digiaUIInstance.dslConfig.fontTokens.mapValues {
                                    convertToTextStyle(it.value, fontFactory)
                                },
                        colors =
                                digiaUIInstance.dslConfig.colorTokens.mapValues { it ->
                                    asSafe<String>(it.value)?.let { ColorUtil.fromString(it) }
                                },
                        darkColors =
                                digiaUIInstance.dslConfig.darkColorTokens.mapValues { it ->
                                    asSafe<String>(it.value)?.let { ColorUtil.fromString(it) }
                                },
                        fontFactory = fontFactory
                )

        // Initialize widget registry with component builder
        widgetRegistry =
                DefaultVirtualWidgetRegistry(
                        componentBuilder = { id, args -> CreateComponent(id, args) }
                )

        // Register all built-in widgets
        widgetRegistry.registerBuiltInWidgets()

        isInitialized = true
        Logger.log("DUIFactory initialized successfully")
    }

    /**
     * Get the configuration provider for internal use
     * @throws IllegalStateException if factory is not initialized
     */
    internal fun getConfigProvider(): ConfigProvider {
        checkInitialized()
        return configProvider
    }

    /**
     * Get the widget registry for internal use
     * @throws IllegalStateException if factory is not initialized
     */
    internal fun getWidgetRegistry(): VirtualWidgetRegistry {
        checkInitialized()
        return widgetRegistry
    }

    /**
     * Creates a page by ID with optional arguments and resource overrides.
     *
     * Pages are full-screen UI definitions that typically represent app screens with their own
     * lifecycle, state management, and navigation capabilities.
     *
     * @param pageId The unique identifier of the page to create
     * @param pageArgs Optional arguments to pass to the page (accessible via expressions)
     * @param overrideIcons Custom icons to override defaults for this page
     * @param overrideImages Custom images to override defaults for this page
     * @param overrideTextStyles Custom text styles to override defaults for this page
     * @param overrideColors Custom colors to override defaults for this page (light theme)
     * @param overrideDarkColors Custom colors to override defaults for this page (dark theme)
     *
     * @throws IllegalArgumentException if page is not found
     * @throws IllegalStateException if factory is not initialized
     */
    @Composable
    fun CreatePage(
            pageId: String,
            pageArgs: Map<String, Any?>? = null,
            overrideIcons: Map<String, ImageVector>? = null,
            overrideImages: Map<String, ImageBitmap>? = null,
            overrideTextStyles: Map<String, TextStyle>? = null,
            overrideColors: Map<String, Color>? = null,
            overrideDarkColors: Map<String, Color>? = null
    ) {
        checkInitialized()

        // getPageDefinition throws IllegalArgumentException if not found
        val pageDef = configProvider.getPageDefinition(pageId)

        // Merge overriding resources with existing resources
        val mergedResources =
                UIResources(
                        icons = mergeMap(resources.icons, overrideIcons),
                        images = mergeMap(resources.images, overrideImages),
                        textStyles = mergeMap(resources.textStyles, overrideTextStyles),
                        colors = mergeMap(resources.colors, overrideColors),
                        darkColors = mergeMap(resources.darkColors, overrideDarkColors),
                        fontFactory = resources.fontFactory
                )
        ActionProvider(actionExecutor = ActionExecutor()) {
            ResourceProvider(mergedResources, apiModels = configProvider.getAllApiModels()) {
                DUIPage(
                        pageId = pageId,
                        pageArgs = pageArgs,
                        pageDef = pageDef,
                        registry = widgetRegistry,
                )
            }
        }
    }

    /**
     * Creates the initial page from configuration.
     *
     * Uses the initialRoute defined in the configuration to determine which page to display first.
     *
     * @param overrideIcons Custom icons to override defaults
     * @param overrideImages Custom images to override defaults
     * @param overrideTextStyles Custom text styles to override defaults
     * @param overrideColors Custom colors to override defaults
     * @param overrideDarkColors Custom dark colors to override defaults
     *
     * @throws IllegalStateException if factory is not initialized or no pages exist
     */
    @Composable
    fun CreateInitialPage(
            overrideIcons: Map<String, ImageVector>? = null,
            overrideImages: Map<String, ImageBitmap>? = null,
            overrideTextStyles: Map<String, TextStyle>? = null,
            overrideColors: Map<String, Color>? = null,
            overrideDarkColors: Map<String, Color>? = null
    ) {
        checkInitialized()

        // Get initial route from config
        val initialRoute = configProvider.getInitialRoute()
        CreatePage(
                pageId = initialRoute,
                overrideIcons = overrideIcons,
                overrideImages = overrideImages,
                overrideTextStyles = overrideTextStyles,
                overrideColors = overrideColors,
                overrideDarkColors = overrideDarkColors
        )
    }

    /**
     * Creates a navigation host with all pages from configuration.
     *
     * This automatically launches the Digia UI navigation in a separate Activity, abstracting away
     * the Activity management from the host app. The navigation system runs in an isolated context
     * with full state preservation.
     *
     * Example usage:
     * ```kotlin
     * @Composable
     * fun MyScreen() {
     *     Button(onClick = {}) {
     *         Text("Open App")
     *     }
     *
     *     // Automatically launches SDK navigation
     *     DUIFactory.getInstance().CreateNavHost(
     *         startPageId = "home",
     *         pageArgs = mapOf("userId" to "123")
     *     )
     * }
     * ```
     *
     * @param startPageId Optional custom start page ID (defaults to config's initialRoute)
     * @param pageArgs Optional arguments to pass to the start page
     * @param overrideIcons Custom icons to override defaults
     * @param overrideImages Custom images to override defaults
     * @param overrideTextStyles Custom text styles to override defaults
     * @param overrideColors Custom colors to override defaults
     * @param overrideDarkColors Custom dark colors to override defaults
     *
     * @throws IllegalStateException if factory is not initialized
     */
    @Composable
    fun CreateNavHost(
            startPageId: String? = null,
            pageArgs: Map<String, Any?>? = null,
            overrideIcons: Map<String, ImageVector>? = null,
            overrideImages: Map<String, ImageBitmap>? = null,
            overrideTextStyles: Map<String, TextStyle>? = null,
            overrideColors: Map<String, Color>? = null,
            overrideDarkColors: Map<String, Color>? = null
    ) {
        checkInitialized()

        val context = androidx.compose.ui.platform.LocalContext.current

        // Launch SDK Activity with navigation
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val intent =
                    com.digia.digiaui.framework.navigation.DigiaUINavigationActivity.createIntent(
                            context = context,
                            startPageId = startPageId,
                            pageArgs = pageArgs
                    )
            context.startActivity(intent)
        }
    }

    /**
     * Creates a reusable component widget from a JSON configuration.
     *
     * Components are smaller, reusable UI blocks that can be embedded within pages or other
     * components. They have their own state management and can receive arguments for customization.
     *
     * @param componentId Unique identifier for the component configuration
     * @param args Arguments to pass to the component (accessible via expressions)
     * @param overrideIcons Custom icons to override defaults for this component
     * @param overrideImages Custom images to override defaults for this component
     * @param overrideTextStyles Custom text styles to override defaults for this component
     * @param overrideColors Custom colors to override defaults for this component
     * @param overrideDarkColors Custom dark colors to override defaults for this component
     *
     * @throws IllegalArgumentException if component is not found
     * @throws IllegalStateException if factory is not initialized
     */
    @Composable
    fun CreateComponent(
            componentId: String,
            args: Map<String, Any?>? = null,
            overrideIcons: Map<String, ImageVector>? = null,
            overrideImages: Map<String, ImageBitmap>? = null,
            overrideTextStyles: Map<String, TextStyle>? = null,
            overrideColors: Map<String, Color>? = null,
            overrideDarkColors: Map<String, Color>? = null
    ) {
        checkInitialized()

        // getComponentDefinition throws IllegalArgumentException if not found
        val componentDef = configProvider.getComponentDefinition(componentId)

        // Merge overriding resources with existing resources
        val mergedResources =
                UIResources(
                        icons = mergeMap(resources.icons, overrideIcons),
                        images = mergeMap(resources.images, overrideImages),
                        textStyles = mergeMap(resources.textStyles, overrideTextStyles),
                        colors = mergeMap(resources.colors, overrideColors),
                        darkColors = mergeMap(resources.darkColors, overrideDarkColors),
                        fontFactory = resources.fontFactory
                )

        ResourceProvider(mergedResources, apiModels = configProvider.getAllApiModels()) {
            DUIComponent(
                    componentId = componentId,
                    args = args,
                    componentDef = componentDef,
                    registry = widgetRegistry,
                    resources = mergedResources
            )
        }
    }

    /**
     * Loads and displays a widget dynamically from remote configuration.
     *
     * This method gets component configuration from the provided [RemoteConfigProvider] (e.g.,
     * Firebase Remote Config) and dynamically renders the widget based on the data.
     *
     * The remote config should return data in the format:
     * ```json
     * {
     *   "componentId": "cardItem-1d1w",
     *   "args": {
     *     "itemPrice": "999",
     *     "itemName": "Product"
     *   }
     * }
     * ```
     *
     * @param key The remote config key/slot name to fetch
     * @param overrideArgs Optional arguments that completely replace the args from remote config
     * @param overrideIcons Custom icons to override defaults for this widget
     * @param overrideImages Custom images to override defaults for this widget
     * @param overrideTextStyles Custom text styles to override defaults for this widget
     * @param overrideColors Custom colors to override defaults for this widget
     * @param overrideDarkColors Custom dark colors to override defaults for this widget
     */
    @Composable
    fun Load(
            key: String,
            overrideArgs: Map<String, Any?>? = null,
            overrideIcons: Map<String, ImageVector>? = null,
            overrideImages: Map<String, ImageBitmap>? = null,
            overrideTextStyles: Map<String, TextStyle>? = null,
            overrideColors: Map<String, Color>? = null,
            overrideDarkColors: Map<String, Color>? = null
    ) {
        if (!isInitialized) {
            Logger.log("Load: DUIFactory not initialized. Call initialize() first.")
            return
        }

        // Get remote config provider from DigiaUI instance
        val digiaUIInstance = DigiaUIManager.getInstance().safeInstance
        if (digiaUIInstance == null) {
            Logger.log("Load: DigiaUIManager not initialized")
            return
        }

        val remoteConfigProvider = digiaUIInstance.initConfig.remoteConfigProvider
        if (remoteConfigProvider == null) {
            Logger.log("Load: RemoteConfigProvider not configured")
            return
        }

        // Get config synchronously from provider (already cached by developer)
        val configData =
                try {
                    remoteConfigProvider.getConfig(key)
                } catch (e: Exception) {
                    Logger.log("Load: Error getting config for key $key: ${e.message}")
                    return
                }

        if (configData == null) {
            Logger.log("Load: Config not found for key: $key")
            return
        }

        Logger.log(
                "Load: Successfully loaded config for key: $key, componentId: ${configData.componentId}"
        )

        // Render the component with fetched config
        CreateComponent(
                componentId = configData.componentId,
                args = overrideArgs ?: configData.args, // overrideArgs replaces remote config args
                overrideIcons = overrideIcons,
                overrideImages = overrideImages,
                overrideTextStyles = overrideTextStyles,
                overrideColors = overrideColors,
                overrideDarkColors = overrideDarkColors
        )
    }

    /**
     * Registers a custom widget builder.
     *
     * This method allows you to extend Digia UI with custom widgets that can be used in JSON
     * configurations. The widget will be identified by the provided [type] string.
     *
     * @param type The widget type identifier (e.g., "custom/myWidget")
     * @param builder The builder function that creates the widget
     *
     * @throws IllegalStateException if factory is not initialized
     *
     * Example:
     * ```kotlin
     * DUIFactory.getInstance().registerWidget("custom/map") { data, registry ->
     *     VWCustomMap(data, registry)
     * }
     * ```
     */
    fun registerWidget(type: String, builder: VirtualWidgetBuilder) {
        checkInitialized()
        widgetRegistry.registerWidget(type, builder)
        Logger.log("Registered custom widget: $type")
    }

    /**
     * Sets an environment variable that can be accessed in expressions.
     *
     * This method allows updating environment variables at runtime through the DUIConfig.
     *
     * @param key The variable name
     * @param value The variable value
     *
     * @throws IllegalStateException if DigiaUIManager is not initialized
     */
    fun setEnvironmentVariable(key: String, value: Any?) {
        val digiaUIInstance = DigiaUIManager.getInstance().safeInstance
        if (digiaUIInstance == null) {
            throw IllegalStateException(
                    "DigiaUIManager is not initialized. Make sure to call DigiaUI.initialize() " +
                            "and await its completion before calling setEnvironmentVariable()."
            )
        }
        // Delegate to DUIConfig
        digiaUIInstance.dslConfig.setEnvVariable(key, value)
        Logger.log("Set environment variable: $key")
    }

    /**
     * Sets multiple environment variables at once.
     *
     * This method allows updating multiple environment variables simultaneously, which is more
     * efficient than calling setEnvironmentVariable multiple times.
     *
     * @param variables Map of variable names to values
     *
     * @throws IllegalStateException if DigiaUIManager is not initialized
     */
    fun setEnvironmentVariables(variables: Map<String, Any?>) {
        val digiaUIInstance = DigiaUIManager.getInstance().safeInstance
        if (digiaUIInstance == null) {
            throw IllegalStateException(
                    "DigiaUIManager is not initialized. Make sure to call DigiaUI.initialize() " +
                            "and await its completion before calling setEnvironmentVariables()."
            )
        }
        // Delegate to DUIConfig for each variable
        for ((key, value) in variables) {
            digiaUIInstance.dslConfig.setEnvVariable(key, value)
        }
        Logger.log("Set ${variables.size} environment variables")
    }

    /**
     * Clears a single environment variable value at runtime.
     *
     * This method resets an environment variable to null.
     *
     * @param key The variable name
     *
     * @throws IllegalStateException if DigiaUIManager is not initialized
     */
    fun clearEnvironmentVariable(key: String) {
        val digiaUIInstance = DigiaUIManager.getInstance().safeInstance
        if (digiaUIInstance == null) {
            throw IllegalStateException(
                    "DigiaUIManager is not initialized. Make sure to call DigiaUI.initialize() " +
                            "and await its completion before calling clearEnvironmentVariable()."
            )
        }
        digiaUIInstance.dslConfig.setEnvVariable(key, null)
        Logger.log("Cleared environment variable: $key")
    }

    /**
     * Clears multiple environment variables at once.
     *
     * @param keys List of variable names to clear
     *
     * @throws IllegalStateException if DigiaUIManager is not initialized
     */
    fun clearEnvironmentVariables(keys: List<String>) {
        val digiaUIInstance = DigiaUIManager.getInstance().safeInstance
        if (digiaUIInstance == null) {
            throw IllegalStateException(
                    "DigiaUIManager is not initialized. Make sure to call DigiaUI.initialize() " +
                            "and await its completion before calling clearEnvironmentVariables()."
            )
        }
        for (key in keys) {
            digiaUIInstance.dslConfig.setEnvVariable(key, null)
        }
        Logger.log("Cleared ${keys.size} environment variables")
    }

    /**
     * Destroys the factory and cleans up resources.
     *
     * This method should be called when the factory is no longer needed, typically during app
     * shutdown. It disposes of the widget registry to free up resources.
     */
    fun destroy() {
        if (isInitialized) {
            // Clean up registry if needed
            isInitialized = false
            Logger.log("DUIFactory destroyed")
        }
    }

    /**
     * Gets the widget registry.
     *
     * @return The widget registry instance
     * @throws IllegalStateException if factory is not initialized
     */
    fun getRegistry(): DefaultVirtualWidgetRegistry {
        checkInitialized()
        return widgetRegistry
    }

    /**
     * Gets the UI resources.
     *
     * @return The UI resources instance
     * @throws IllegalStateException if factory is not initialized
     */
    fun getResources(): UIResources {
        checkInitialized()
        return resources
    }

    /** Helper method to merge two maps, with the override map taking precedence. */
    private fun <K, V> mergeMap(base: Map<K, V>?, override: Map<K, V>?): Map<K, V>? {
        return when {
            base == null && override == null -> null
            base == null -> override
            override == null -> base
            else -> base + override
        }
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(
                    "DUIFactory is not initialized. Call DUIFactory.getInstance().initialize() first."
            )
        }
    }

    companion object {
        @Volatile private var instance: DUIFactory? = null

        /**
         * Gets the singleton instance of DUIFactory.
         *
         * @return The DUIFactory singleton instance
         */
        @JvmStatic
        public fun getInstance(): DUIFactory {
            return instance
                    ?: synchronized(this) { instance ?: DUIFactory().also { instance = it } }
        }
    }
}
