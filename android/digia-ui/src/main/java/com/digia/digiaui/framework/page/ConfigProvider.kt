package com.digia.digiaui.framework.page

import com.digia.digiaui.config.model.DUIConfig
import com.digia.digiaui.framework.models.ComponentDefinition
import com.digia.digiaui.framework.models.PageDefinition
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.network.APIModel
import com.digia.digiaui.network.RequestOptions

/**
 * Abstract interface for providing configuration data to the Digia UI system.
 *
 * ConfigProvider defines the contract for accessing page definitions,
 * component definitions, API models, and routing information. This abstraction
 * allows for different configuration sources and enables testing with mock
 * implementations.
 *
 * The provider is responsible for:
 * - **Page Management**: Retrieving page definitions and determining initial routes
 * - **Component Access**: Providing component definitions for reusable UI blocks
 * - **API Configuration**: Supplying API model configurations for network requests
 * - **Type Resolution**: Determining whether an ID represents a page or component
 *
 * Implementations should handle:
 * - Configuration loading and caching
 * - Error handling for missing definitions
 * - Type validation and conversion
 * - Performance optimization for frequent lookups
 */
abstract class ConfigProvider {
    /**
     * Gets the initial route/page ID that should be displayed when the app starts.
     *
     * Returns the page identifier that serves as the entry point for the application.
     * This is typically configured in the app settings within Digia Studio.
     */
    abstract fun getInitialRoute(): String

    /**
     * Retrieves a page definition by its unique identifier.
     *
     * @param pageId The unique identifier of the page to retrieve
     * @return A PageDefinition containing the page structure, state definitions,
     * argument definitions, and UI hierarchy.
     * @throws IllegalArgumentException if the page with the given ID is not found.
     */
    abstract fun getPageDefinition(pageId: String): PageDefinition

    /**
     * Retrieves a component definition by its unique identifier.
     *
     * @param componentId The unique identifier of the component to retrieve
     * @return A ComponentDefinition containing the component structure,
     * argument definitions, state definitions, and UI hierarchy.
     * @throws IllegalArgumentException if the component with the given ID is not found.
     */
    abstract fun getComponentDefinition(componentId: String): ComponentDefinition

    /**
     * Gets all API model configurations available in the project.
     *
     * Returns a map where keys are API model identifiers and values are
     * APIModel instances containing endpoint configurations, authentication
     * settings, and request/response specifications.
     */
    abstract fun getAllApiModels(): Map<String, APIModel>

    /**
     * Determines whether the given identifier represents a page.
     *
     * @param id The identifier to check
     * @return true if the ID corresponds to a page definition, false if it
     * represents a component or doesn't exist.
     */
    abstract fun isPage(id: String): Boolean

    /**
     * Gets the complete DUIConfig instance.
     *
     * @return The full configuration object
     */
    abstract fun getConfig(): DUIConfig
}

/**
 * Default implementation of ConfigProvider that uses DUIConfig as the data source.
 *
 * DUIConfigProvider reads configuration data from a DUIConfig instance,
 * which typically contains the complete project configuration loaded from
 * the Digia Studio backend. This is the standard implementation used in
 * production applications.
 *
 * The provider handles:
 * - **JSON Parsing**: Converts raw configuration data to typed objects
 * - **Error Handling**: Provides clear error messages for missing definitions
 * - **Type Safety**: Ensures proper type conversion and validation
 * - **Performance**: Efficient lookups and minimal data transformation
 *
 * Example usage:
 * ```
 * val config = DUIConfig(configurationData)
 * val provider = DUIConfigProvider(config)
 *
 * val initialPage = provider.getInitialRoute()
 * val pageDefinition = provider.getPageDefinition("home_page")
 * val apiModels = provider.getAllApiModels()
 * ```
 *
 * @property config The configuration instance containing all project data
 */
class DUIConfigProvider(
    private val config: DUIConfig
) : ConfigProvider() {

    override fun getInitialRoute(): String = config.initialRoute

    override fun getPageDefinition(pageId: String): PageDefinition {
        // Extract page configuration from DUIConfig
        val pageDef = config.pages[pageId]
            ?: throw IllegalArgumentException("Page definition for $pageId not found")

        return PageDefinition.fromJson(pageDef as JsonLike)
    }

    override fun getComponentDefinition(componentId: String): ComponentDefinition {
        // Extract component configuration from DUIConfig
        val componentDef = config.components?.get(componentId)
            ?: throw IllegalArgumentException("Component definition for $componentId not found")

        return ComponentDefinition.fromJson(componentDef as JsonLike)
    }

    override fun getAllApiModels(): Map<String, APIModel> {
        // Extract and parse all API model configurations
        val resources = config.restConfig?.get("resources") as? Map<*, *>
            ?: return emptyMap()

        return resources.mapNotNull { (key, value) ->
            if (key is String && value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                key to APIModel.fromJson(value as Map<String, Any>)
            } else {
                null
            }
        }.toMap()
    }

    override fun isPage(id: String): Boolean = config.pages.containsKey(id)

    override fun getConfig(): DUIConfig = config
}