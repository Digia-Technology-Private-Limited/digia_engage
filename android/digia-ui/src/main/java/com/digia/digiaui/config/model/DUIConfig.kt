package com.digia.digiaui.config.model

import com.digia.digiaui.core.functions.JSFunctions
import com.digia.digiaui.framework.datatype.Variable
import com.digia.digiaui.framework.datatype.VariableConverter
import com.digia.digiaui.network.APIModel
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Core configuration model for the Digia UI system.
 *
 * DUIConfig represents the complete configuration loaded from the Digia
 * Studio backend, containing all the information needed to render pages, components, and handle API
 * interactions. This includes theme configuration, page definitions, component definitions, API
 * configurations, and environment settings.
 *
 * The configuration is typically loaded during app initialization and used throughout the
 * application lifecycle to:
 * - Define UI structure and styling
 * - Configure API endpoints and authentication
 * - Manage environment variables and app state
 * - Handle routing and navigation
 *
 * Key components:
 * - **Theme Configuration**: Colors, fonts, and styling tokens
 * - **Pages & Components**: UI definitions and structure
 * - **REST Configuration**: API endpoints and models
 * - **Environment Settings**: Variables and configuration per environment
 * - **App State**: Global state definitions and initial values
 */
@Suppress("UNCHECKED_CAST")
data class DUIConfig(
        @SerializedName("theme") val themeConfig: Map<String, Any>,
        @SerializedName("pages") val pages: Map<String, Any>,
        @SerializedName("components") val components: Map<String, Any>? = null,
        @SerializedName("rest") val restConfig: Map<String, Any>,
        @SerializedName("appSettings") val appSettings: AppSettings,
        @SerializedName("appState") val appState: List<Any>? = null,
        @SerializedName("version") val version: Int? = null,
        @SerializedName("versionUpdated") val versionUpdated: Boolean? = null,
        @SerializedName("functionsFilePath") val functionsFilePath: String? = null,
        @SerializedName("environment") private val _environment: Map<String, Any>? = null,
        @SerializedName("appAssets") val appAssets: List<Any>? = null
) {
    /** The initial route/page ID to display when the app starts */
    val initialRoute: String
        get() = appSettings.initialRoute

    /** Gets the light theme color tokens as a map of color names to values */
    val colorTokens: Map<String, Any>
        get() =
                (themeConfig["colors"] as? Map<String, Any>)?.get("light") as? Map<String, Any>
                        ?: emptyMap()

    /** Gets the dark theme color tokens as a map of color names to values */
    val darkColorTokens: Map<String, Any>
        get() =
                (themeConfig["colors"] as? Map<String, Any>)?.get("dark") as? Map<String, Any>
                        ?: emptyMap()

    /** Gets the font tokens as a map of font names to font configurations */
    val fontTokens: Map<String, Any>
        get() = themeConfig["fonts"] as? Map<String, Any> ?: emptyMap()

    /**
     * Retrieves a color value by its token name.
     *
     * This method looks up a color value from the light theme colors using the provided token name.
     * Returns null if the token doesn't exist.
     *
     * @param colorToken The name/key of the color token to retrieve
     * @return The color value as a string (typically hex format) or null
     */
    fun getColorValue(colorToken: String): String? {
        return colorTokens[colorToken] as? String
    }

    /**
     * Gets the default HTTP headers for API requests.
     *
     * These headers are applied to all API requests made through the Digia UI system unless
     * overridden by specific API configurations.
     *
     * @return A map of header names to values, or null if no default headers are configured
     */
    fun getDefaultHeaders(): Map<String, Any>? {
        return restConfig["defaultHeaders"] as? Map<String, Any>
    }

    /**
     * Gets all environment variables defined in the configuration.
     *
     * Environment variables are used to store configuration values that can vary between different     * environments.
     *
     * @return A map of variable names to Variable objects
     */
    fun getEnvironmentVariables(): Map<String, Variable> {
        val rawVariables = _environment?.get("variables") as? Map<String, Any> ?: return emptyMap()

        return VariableConverter.fromJson(rawVariables)
    }


    /**
     * Sets an environment variable value at runtime.
     *
     * This method allows updating environment variables after configuration loading, which is
     * useful for dynamic configuration based on user preferences or runtime conditions.
     *
     * Note: This creates a new map since Kotlin data classes are immutable. For persistent changes,
     * the updated config should be saved.
     *
     * @param varName The name of the environment variable to update
     * @param value The new value to set for the variable
     */
    fun setEnvVariable(varName: String, value: Any?) {
        val variables = getEnvironmentVariables().toMutableMap()
        val currentVar = variables[varName] ?: return

        // Update the variable value
        variables[varName] = currentVar.copyWith(defaultValue = value)

        // Since _environment is a val Map, we need to cast it to MutableMap to update internal keys
        // or ensure the class property is defined as a MutableMap.
        (_environment as? MutableMap<String, Any>)?.let { env ->
            env["variables"] = VariableConverter.toJson(variables)
        }
    }

    /**
     * Retrieves an API model configuration by its identifier.
     *
     * API models define the structure and configuration for making HTTP requests to specific
     * endpoints. This includes URL patterns, request methods, authentication requirements, and
     * response parsing.
     *
     * @param id The unique identifier of the API model to retrieve
     * @return A map containing the API model configuration
     * @throws NoSuchElementException if the API model with the given ID is not found
     */
    fun getApiDataSource(id: String): APIModel? {
        val resources =
                restConfig["resources"] as? Map<String, Any>
                        ?: throw NoSuchElementException("No resources found in REST config")
        val resource= resources[id] as? Map<String, Any>
                ?: throw NoSuchElementException("API model with id '$id' not found")
        return APIModel.fromJson(resource )
    }
    


    companion object {


        /** Creates a DUIConfig instance from a Map */
        fun fromMap(map: Map<String, Any>): DUIConfig {
            return DUIConfig(
                    themeConfig = map["theme"] as? Map<String, Any> ?: emptyMap(),
                    pages = map["pages"] as? Map<String, Any> ?: emptyMap(),
                    components = map["components"] as? Map<String, Any>,
                    restConfig = map["rest"] as? Map<String, Any> ?: emptyMap(),
                    appSettings =
                        AppSettings(
                                initialRoute =
                                    (map["appSettings"] as? Map<String, Any>)
                                            ?.get("initialRoute") as? String
                                            ?: ""
                        ),
                    appState = map["appState"] as? List<Any>,
                    version = (map["version"] as? Double)?.toInt(),
                    versionUpdated = map["versionUpdated"] as? Boolean,
                    functionsFilePath = map["functionsFilePath"] as? String,
                    _environment = map["environment"] as? Map<String, Any>
            )
        }
    }

    var jsFunctions: JSFunctions? = null
}

data class AppSettings(@SerializedName("initialRoute") val initialRoute: String)
