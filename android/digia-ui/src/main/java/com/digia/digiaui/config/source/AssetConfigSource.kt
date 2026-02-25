package com.digia.digiaui.config.source

import com.digia.digiaui.config.ConfigException
import com.digia.digiaui.config.ConfigFetcher
import com.digia.digiaui.config.model.DUIConfig
import com.digia.digiaui.framework.logging.Logger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * ConfigSource that loads configuration from bundled assets
 *
 * This is used as the fallback source for Release flavor, providing a burned-in configuration that
 * is guaranteed to be available.
 *
 * The asset files should be placed in the assets/ directory:
 * - assets/config.json (or custom appConfigPath)
 * - assets/functions.json (or custom functionsPath)
 *
 * @param provider The ConfigProvider (used for bundleOps)
 * @param appConfigPath Path to the app config asset file
 * @param functionsPath Path to the functions asset file (optional)
 */
class AssetConfigSource(
    private val provider: ConfigFetcher,
    private val appConfigPath: String = "config.json",
    private val functionsPath: String? = "functions.json"
) : ConfigSource {

    override suspend fun getConfig(): DUIConfig {
        try {
            Logger.log("Loading config from assets: $appConfigPath")

            // Read the asset file content using bundleOps
            val burnedJson = provider.bundleOps.readString(appConfigPath)

            // Parse the JSON and create DUIConfig
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonData = Gson().fromJson<Map<String, Any>>(burnedJson, type)
            val config = DUIConfig.fromMap(jsonData)

            // Initialize functions if path provided
            functionsPath?.let {
                try {
                    provider.initFunctions(localPath = it)
                } catch (e: Exception) {
                    Logger.log("Functions file not found or failed to load: $it")
                }
            }

            Logger.log("Successfully loaded config from assets")
            return config
        } catch (e: ConfigException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException.assetError("Failed to load config from assets: ${e.message}", e)
        }
    }
}
